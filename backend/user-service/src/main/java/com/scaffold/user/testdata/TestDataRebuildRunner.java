package com.scaffold.user.testdata;

import com.scaffold.id.GlobalSequenceAllocator;
import com.scaffold.id.SnowflakeIdGenerator;
import com.scaffold.user.device.RiskLevel;
import com.scaffold.user.device.UserDevice;
import com.scaffold.user.device.UserDeviceMapper;
import com.scaffold.user.device.UserDeviceRegistry;
import com.scaffold.user.entity.Permission;
import com.scaffold.user.entity.PermissionGroup;
import com.scaffold.user.entity.PermissionGroupItem;
import com.scaffold.user.entity.Role;
import com.scaffold.user.entity.RolePermission;
import com.scaffold.user.entity.RolePermissionGroup;
import com.scaffold.user.entity.User;
import com.scaffold.user.entity.UserRole;
import com.scaffold.user.enums.EnableStatus;
import com.scaffold.user.mapper.PermissionGroupItemMapper;
import com.scaffold.user.mapper.PermissionGroupMapper;
import com.scaffold.user.mapper.PermissionMapper;
import com.scaffold.user.mapper.RoleMapper;
import com.scaffold.user.mapper.RolePermissionGroupMapper;
import com.scaffold.user.mapper.RolePermissionMapper;
import com.scaffold.user.mapper.UserRoleMapper;
import com.scaffold.user.service.impl.UserServiceImpl;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 测试数据重建程序。
 *
 * <p>用法（action 留空时本程序不执行任何动作，不会随普通启动自动运行）：
 * <pre>
 * java -jar user-service.jar --spring.profiles.active=test --test-data.action=inspect
 * java -jar user-service.jar --spring.profiles.active=test \
 *      --test-data.action=rebuild --test-data.confirm=I_UNDERSTAND
 * java -jar user-service.jar --spring.profiles.active=test --test-data.action=verify
 * # 把 user_device.sort_seq 重排为 1..N（消除跳号），同样需要 confirm
 * java -jar user-service.jar --spring.profiles.active=test \
 *      --test-data.action=compact --test-data.confirm=I_UNDERSTAND
 * </pre>
 *
 * <p>安全约束（rebuild 前逐项校验，任一不满足即中止）：
 * <ol>
 *   <li>Spring Profile 不得为 prod / production，且必须为 test 或 dev</li>
 *   <li>显式授权标记 {@code test-data.confirm} 必须等于 {@link #CONFIRM_TOKEN}</li>
 *   <li>当前数据库名必须在 {@code test-data.allowed-databases} 白名单内</li>
 * </ol>
 *
 * <p>不触碰 {@code sys_sequence} 与 {@code sys_worker_node}：
 * 前者是全局序号高水位，后者是 Worker ID 租约，二者都不应被测试数据重建重置。
 *
 * <p>可重复执行：每次都是先物理删除允许范围内的业务数据，再按依赖顺序重建。
 */
@Slf4j
@Component
@EnableConfigurationProperties(TestDataProperties.class)
public class TestDataRebuildRunner implements ApplicationRunner {

    /** rebuild 的显式授权标记 */
    public static final String CONFIRM_TOKEN = "I_UNDERSTAND";

    /** 允许执行重建的 Profile */
    private static final Set<String> ALLOWED_PROFILES = Set.of("test", "dev");

    /** 禁止执行重建的 Profile */
    private static final Set<String> FORBIDDEN_PROFILES = Set.of("prod", "production");

    /**
     * 物理清理顺序（逆依赖顺序）。
     * 不含 sys_sequence（序号高水位）与 sys_worker_node（Worker 租约）。
     */
    private static final List<String> PURGE_TABLES = List.of(
            "sys_role_permission",
            "sys_role_permission_group",
            "sys_permission_group_item",
            "sys_user_role",
            "user_device",
            "sys_permission",
            "sys_permission_group",
            "sys_role",
            "user"
    );

    /** 参与统计与校验的业务表 */
    private static final List<String> REPORT_TABLES = List.of(
            "user", "sys_role", "sys_permission", "sys_permission_group",
            "sys_user_role", "sys_role_permission", "sys_permission_group_item",
            "sys_role_permission_group", "user_device"
    );

    private final TestDataProperties properties;
    private final Environment environment;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final SnowflakeIdGenerator idGenerator;
    private final GlobalSequenceAllocator sequenceAllocator;
    private final PasswordEncoder passwordEncoder;

    private final UserServiceImpl userService;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final PermissionGroupMapper permissionGroupMapper;
    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionGroupItemMapper permissionGroupItemMapper;
    private final RolePermissionGroupMapper rolePermissionGroupMapper;
    private final UserDeviceMapper deviceMapper;

    private final ConfigurableApplicationContext context;

    public TestDataRebuildRunner(TestDataProperties properties,
                                 Environment environment,
                                 JdbcTemplate jdbcTemplate,
                                 DataSource dataSource,
                                 SnowflakeIdGenerator idGenerator,
                                 GlobalSequenceAllocator sequenceAllocator,
                                 PasswordEncoder passwordEncoder,
                                 UserServiceImpl userService,
                                 RoleMapper roleMapper,
                                 PermissionMapper permissionMapper,
                                 PermissionGroupMapper permissionGroupMapper,
                                 UserRoleMapper userRoleMapper,
                                 RolePermissionMapper rolePermissionMapper,
                                 PermissionGroupItemMapper permissionGroupItemMapper,
                                 RolePermissionGroupMapper rolePermissionGroupMapper,
                                 UserDeviceMapper deviceMapper,
                                 ConfigurableApplicationContext context) {
        this.properties = properties;
        this.environment = environment;
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.idGenerator = idGenerator;
        this.sequenceAllocator = sequenceAllocator;
        this.passwordEncoder = passwordEncoder;
        this.userService = userService;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.permissionGroupMapper = permissionGroupMapper;
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.permissionGroupItemMapper = permissionGroupItemMapper;
        this.rolePermissionGroupMapper = rolePermissionGroupMapper;
        this.deviceMapper = deviceMapper;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        String action = properties.getAction() == null ? "" : properties.getAction().trim().toLowerCase();
        if (action.isEmpty()) {
            log.info("[test-data] 未配置 test-data.action，跳过重建程序");
            return;
        }
        long start = System.currentTimeMillis();
        try {
            switch (action) {
                case "inspect" -> inspect();
                case "rebuild" -> rebuild();
                case "verify" -> verify();
                case "compact" -> compact();
                default -> throw new IllegalArgumentException(
                        "未知的 test-data.action=" + action + "，可选值：inspect / rebuild / verify / compact");
            }
            log.info("[test-data] 动作 {} 完成，耗时 {} ms", action, System.currentTimeMillis() - start);
        } catch (RuntimeException e) {
            log.error("[test-data] 动作 {} 失败：{}", action, e.getMessage(), e);
            throw e;
        } finally {
            if (properties.isExitAfterRun()) {
                log.info("[test-data] 执行完毕，退出进程（exit-after-run=true）");
                SpringApplication.exit(context, (ExitCodeGenerator) () -> 0);
            }
        }
    }

    // ========================================================================
    // inspect
    // ========================================================================

    /** 检查测试数据库与待重建对象，不写入任何数据。 */
    public void inspect() {
        log.info("========== [test-data] inspect ==========");
        log.info("  Spring Profile : {}", Arrays.toString(environment.getActiveProfiles()));
        log.info("  JDBC URL       : {}", resolveJdbcUrl());
        log.info("  当前数据库     : {}", currentDatabase());
        log.info("  授权标记       : {}", CONFIRM_TOKEN.equals(properties.getConfirm()) ? "已提供" : "未提供");
        log.info("  将清理的表     : {}", PURGE_TABLES);
        log.info("  不会清理的表   : [sys_sequence, sys_worker_node]");
        log.info("  计划生成用户数 : {}（另有 admin / guest / operator）", properties.getUserCount());
        log.info("  当前行数：");
        for (Map.Entry<String, Long> e : countAll().entrySet()) {
            log.info("    - {} : {}", e.getKey(), e.getValue());
        }
        log.info("========== [test-data] inspect 结束 ==========");
    }

    // ========================================================================
    // rebuild
    // ========================================================================

    /** 删除并重建允许范围内的测试数据。可重复执行。 */
    @Transactional(rollbackFor = Exception.class)
    public void rebuild() {
        log.info("========== [test-data] rebuild ==========");
        assertRebuildAllowed();

        long start = System.currentTimeMillis();
        for (String table : PURGE_TABLES) {
            int deleted = jdbcTemplate.update("DELETE FROM " + table);
            log.info("  清理 {} : {} 行", table, deleted);
        }

        Map<String, Long> permissionIds = rebuildPermissions();
        Map<String, Long> roleIds = rebuildRoles();
        List<Long> userIds = rebuildUsers();
        bindUserRoles(userIds, roleIds);
        bindRolePermissions(roleIds, permissionIds);
        List<Long> groupIds = rebuildPermissionGroups(permissionIds);
        bindRolePermissionGroups(roleIds, groupIds);
        int devices = rebuildDevices(userIds);

        log.info("  重建完成，耗时 {} ms", System.currentTimeMillis() - start);
        Map<String, Long> counts = countAll();
        log.info("  最终行数：");
        counts.forEach((t, c) -> log.info("    - {} : {}", t, c));
        log.info("  登录设备数：{}", devices);
        log.info("========== [test-data] rebuild 结束 ==========");
    }

    // ========================================================================
    // verify
    // ========================================================================

    /** 检查新数据完整性，任一检查失败抛异常。 */
    public void verify() {
        log.info("========== [test-data] verify ==========");
        List<String> failures = new ArrayList<>();
        // 只对业务表断言非空；sys_sequence / sys_worker_node 是基础设施表，
        // 在 FIXED worker-id 模式下本来就是空的，不参与完整性校验
        Map<String, Long> counts = countBusiness();

        for (Map.Entry<String, Long> e : counts.entrySet()) {
            log.info("  {} : {}", e.getKey(), e.getValue());
            if (e.getValue() == null || e.getValue() == 0) {
                failures.add(e.getKey() + " 行数为 0");
            }
        }
        log.info("  sys_sequence : {}", countOf("sys_sequence"));
        log.info("  sys_worker_node : {}", countOf("sys_worker_node"));

        // 1. 主键合法性：全部为正数且能通过 Snowflake 位结构校验
        for (String table : List.of("user", "sys_role", "sys_permission", "sys_permission_group",
                "sys_user_role", "sys_role_permission", "sys_permission_group_item",
                "sys_role_permission_group", "user_device")) {
            List<Long> ids = jdbcTemplate.queryForList("SELECT id FROM " + table, Long.class);
            long bad = ids.stream().filter(id -> id == null || !idGenerator.validate(id)).count();
            if (bad > 0) {
                failures.add(table + " 有 " + bad + " 个非法主键");
            }
        }

        // 2. 主键唯一（同一表内）
        for (String table : REPORT_TABLES) {
            Long dup = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM (SELECT id FROM " + table + " GROUP BY id HAVING COUNT(*) > 1) t",
                    Long.class);
            if (dup != null && dup > 0) {
                failures.add(table + " 存在 " + dup + " 个重复主键");
            }
        }

        // 3. 外键关联完整性
        checkOrphan(failures, "sys_user_role.user_id", "sys_user_role", "user_id", "user");
        checkOrphan(failures, "sys_user_role.role_id", "sys_user_role", "role_id", "sys_role");
        checkOrphan(failures, "sys_role_permission.role_id", "sys_role_permission", "role_id", "sys_role");
        checkOrphan(failures, "sys_role_permission.permission_id", "sys_role_permission", "permission_id", "sys_permission");
        checkOrphan(failures, "sys_permission_group_item.group_id", "sys_permission_group_item", "group_id", "sys_permission_group");
        checkOrphan(failures, "sys_permission_group_item.permission_id", "sys_permission_group_item", "permission_id", "sys_permission");
        checkOrphan(failures, "sys_role_permission_group.role_id", "sys_role_permission_group", "role_id", "sys_role");
        checkOrphan(failures, "sys_role_permission_group.group_id", "sys_role_permission_group", "group_id", "sys_permission_group");
        checkOrphan(failures, "user_device.user_id", "user_device", "user_id", "user");

        // 4. 严格排序字段：唯一且递增
        Long dupSeq = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (SELECT sort_seq FROM user_device GROUP BY sort_seq HAVING COUNT(*) > 1) t",
                Long.class);
        if (dupSeq != null && dupSeq > 0) {
            failures.add("user_device.sort_seq 存在 " + dupSeq + " 个重复值");
        }
        Long nonPositiveSeq = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_device WHERE sort_seq IS NULL OR sort_seq <= 0", Long.class);
        if (nonPositiveSeq != null && nonPositiveSeq > 0) {
            failures.add("user_device.sort_seq 有 " + nonPositiveSeq + " 个非正值");
        }

        // 4b. 空洞统计：序号只保证唯一与单调，允许跳号。这里不判失败，只如实报告，
        //     让「跳号是设计使然」可被观测，而不是等前端展示时才发现。
        reportSortSeqGaps();

        // 5. 逻辑删除字段未被误置
        Long deletedUsers = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user WHERE deleted = 1", Long.class);
        if (deletedUsers != null && deletedUsers > 0) {
            failures.add("user 表存在 " + deletedUsers + " 条逻辑删除记录");
        }

        // 6. 索引与查询实际使用的排序字段对齐。
        //    排序字段改了而索引没跟上，在数据量小的时候看不出问题，上线后才暴露，
        //    所以在这里做成显式断言。
        checkExpectedIndexes(failures);

        if (failures.isEmpty()) {
            log.info("  全部校验通过");
            log.info("========== [test-data] verify 结束 ==========");
            return;
        }
        failures.forEach(f -> log.error("  校验失败：{}", f));
        log.info("========== [test-data] verify 结束（{} 项失败）==========", failures.size());
        throw new IllegalStateException("测试数据校验失败：" + failures);
    }

    // ========================================================================
    // 索引对齐
    // ========================================================================

    /** 期望存在的索引：表名 → 索引名 → 列顺序（必须完全一致）。 */
    private static final Map<String, Map<String, List<String>>> EXPECTED_INDEXES = Map.of(
            "user", Map.of("idx_user_created", List.of("create_time", "id")),
            "sys_role", Map.of("idx_role_sort", List.of("sort", "id")),
            "sys_permission", Map.of("idx_perm_sort", List.of("sort", "id")),
            "sys_permission_group", Map.of("idx_group_sort", List.of("sort", "id")),
            "user_device", Map.of(
                    "uk_device_sort_seq", List.of("sort_seq"),
                    "idx_device_user_login", List.of("user_id", "last_login_time", "sort_seq"))
    );

    /**
     * 校验索引是否存在且列顺序与查询的排序/过滤字段一致。
     *
     * <p>背景：最初只按「普通列表 ORDER BY create_time DESC, id DESC」建了索引，
     * 但角色/权限/权限组的列表实际按 {@code sort ASC, id ASC} 排序，索引没对上。
     * 小数据量下看不出差异，因此做成断言防止再次漂移。
     */
    private void checkExpectedIndexes(List<String> failures) {
        for (Map.Entry<String, Map<String, List<String>>> tableEntry : EXPECTED_INDEXES.entrySet()) {
            String table = tableEntry.getKey();
            for (Map.Entry<String, List<String>> indexEntry : tableEntry.getValue().entrySet()) {
                String indexName = indexEntry.getKey();
                List<String> expectedColumns = indexEntry.getValue();
                List<String> actualColumns = jdbcTemplate.queryForList(
                        "SELECT COLUMN_NAME FROM information_schema.STATISTICS "
                                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ? "
                                + "ORDER BY SEQ_IN_INDEX",
                        String.class, table, indexName);
                if (actualColumns.isEmpty()) {
                    failures.add("缺少索引 " + table + "." + indexName + "（期望列 " + expectedColumns + "）");
                    continue;
                }
                if (!actualColumns.equals(expectedColumns)) {
                    failures.add("索引 " + table + "." + indexName + " 列顺序不符：期望 "
                            + expectedColumns + "，实际 " + actualColumns);
                }
            }
        }
    }

    // ========================================================================
    // compact
    // ========================================================================

    /**
     * 把 {@code user_device.sort_seq} 按现有顺序重排为 1..N，消除空洞，
     * 并把 {@code sys_sequence} 的高水位同步重置到 N。
     *
     * <p>为什么单独提供一个动作而不是让 rebuild 顺手做：序号允许空洞是刻意的取舍
     * （分配后不回退，避免额外的锁竞争）。只有当业务确实需要「连续编号」时才执行
     * 本动作，且它同样受 profile / confirm / 库名白名单三道护栏约束。
     *
     * <p>重排保持相对顺序不变：按当前 {@code sort_seq} 升序重新编号，
     * 因此设备注册顺序这一语义不被改变。
     */
    @Transactional(rollbackFor = Exception.class)
    public void compact() {
        log.info("========== [test-data] compact ==========");
        assertRebuildAllowed();

        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM user_device ORDER BY sort_seq", Long.class);
        for (int i = 0; i < ids.size(); i++) {
            jdbcTemplate.update("UPDATE user_device SET sort_seq = ? WHERE id = ?", i + 1L, ids.get(i));
        }
        int updated = jdbcTemplate.update(
                "UPDATE sys_sequence SET current_value = ?, update_time = NOW(3) WHERE biz_key = ?",
                (long) ids.size(), UserDeviceRegistry.DEVICE_SEQ_KEY);
        if (updated == 0) {
            // 高水位行还不存在也无所谓：下一个分配器调用会按新值插入
            log.info("  sys_sequence 无 {} 行，跳过高水位重置", UserDeviceRegistry.DEVICE_SEQ_KEY);
        }

        log.info("  重排 {} 条 sort_seq 为 1..{}", ids.size(), ids.size());
        reportSortSeqGaps();
        log.info("========== [test-data] compact 结束 ==========");
    }

    /**
     * 报告 {@code user_device.sort_seq} 的空洞情况。只记录不判失败。
     *
     * <p>空洞数 = 实际跨度 − 行数，即中间缺了多少个号；
     * 最大空洞 = 相邻两个序号之间最大的间隔 − 1。
     */
    private void reportSortSeqGaps() {
        Long rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_device", Long.class);
        Long min = jdbcTemplate.queryForObject("SELECT MIN(sort_seq) FROM user_device", Long.class);
        Long max = jdbcTemplate.queryForObject("SELECT MAX(sort_seq) FROM user_device", Long.class);
        if (rows == null || rows == 0 || min == null || max == null) {
            log.info("  sort_seq 空洞统计：表为空，跳过");
            return;
        }
        long span = max - min + 1;
        long gaps = span - rows;
        // 相邻序号的最大间隔：用窗口函数求后一个 sort_seq 与当前之差的最大值
        Long maxGap = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(next_seq - sort_seq - 1), 0) FROM ("
                        + "SELECT sort_seq, LEAD(sort_seq) OVER (ORDER BY sort_seq) AS next_seq FROM user_device"
                        + ") t WHERE next_seq IS NOT NULL", Long.class);
        log.info("  sort_seq 空洞统计：行数={}, 区间=[{}, {}], 跨度={}, 空洞={}, 最大单段空洞={}",
                rows, min, max, span, gaps, maxGap == null ? 0 : maxGap);
        if (gaps > 0) {
            log.info("  说明：序号只保证唯一与单调，跳号是设计使然（分配后不回退）。"
                    + "如需连续编号，执行 --test-data.action=compact");
        }
    }

    // ========================================================================
    // 环境校验
    // ========================================================================

    private void assertRebuildAllowed() {
        Set<String> profiles = Set.of(environment.getActiveProfiles());

        for (String forbidden : FORBIDDEN_PROFILES) {
            if (profiles.contains(forbidden)) {
                throw new IllegalStateException("禁止在 profile=" + forbidden + " 下执行测试数据重建");
            }
        }
        boolean profileAllowed = profiles.stream().anyMatch(ALLOWED_PROFILES::contains);
        if (!profileAllowed) {
            throw new IllegalStateException(
                    "测试数据重建仅允许在 " + ALLOWED_PROFILES + " profile 下执行，当前="
                            + Arrays.toString(environment.getActiveProfiles())
                            + "。请通过 --spring.profiles.active=test 指定");
        }
        if (!CONFIRM_TOKEN.equals(properties.getConfirm())) {
            throw new IllegalStateException(
                    "缺少显式授权标记：请传入 --test-data.confirm=" + CONFIRM_TOKEN);
        }
        String database = currentDatabase();
        if (!properties.getAllowedDatabases().contains(database)) {
            throw new IllegalStateException(
                    "当前数据库 " + database + " 不在白名单 " + properties.getAllowedDatabases() + " 内，拒绝重建");
        }
        log.info("  环境校验通过：profile={}, database={}, url={}",
                Arrays.toString(environment.getActiveProfiles()), database, resolveJdbcUrl());
    }

    // ========================================================================
    // 数据生成
    // ========================================================================

    private Map<String, Long> rebuildRoles() {
        Map<String, Long> ids = new LinkedHashMap<>();
        int sort = 1;
        ids.put("ADMIN", insertRole("ADMIN", "管理员", "系统超级管理员，拥有全部权限", sort++));
        ids.put("USER", insertRole("USER", "普通用户", "仅查看权限", sort++));
        ids.put("OPERATOR", insertRole("OPERATOR", "运营", "用户与设备的日常运营权限", sort));
        log.info("  角色 {} 个", ids.size());
        return ids;
    }

    private Long insertRole(String code, String name, String description, int sort) {
        Role role = new Role();
        role.setCode(code);
        role.setName(name);
        role.setDescription(description);
        role.setSort(sort);
        role.setStatus(EnableStatus.ENABLED);
        roleMapper.insert(role);
        return role.getId();
    }

    /** 权限目录：菜单(type=1) + 按钮(type=2) + 接口(type=3)。 */
    private record PermSpec(String code, String name, int type, String parentCode,
                            String path, String component, String icon, int sort) {
    }

    private static List<PermSpec> permissionCatalog() {
        List<PermSpec> specs = new ArrayList<>();
        // 菜单
        specs.add(new PermSpec("menu:user", "用户管理", 1, null, "/user", "user-list/index", "team", 10));
        specs.add(new PermSpec("menu:device", "登录设备", 1, null, "/device", "device-list/index", "smartphone", 20));
        specs.add(new PermSpec("menu:role", "角色管理", 1, null, "/role", "role-list/index", "shield", 30));
        specs.add(new PermSpec("menu:permission", "权限管理", 1, null, "/permission", "permission-list/index", "key", 40));
        specs.add(new PermSpec("menu:permission-group", "权限组管理", 1, null, "/permission-group", "permission-group-list/index", "layers", 50));
        specs.add(new PermSpec("menu:operation-log", "操作日志", 1, null, "/operation-log", "operation-log/index", "file-text", 60));
        specs.add(new PermSpec("menu:login-log", "登录日志", 1, null, "/login-log", "login-log/index", "history", 70));
        specs.add(new PermSpec("menu:dict", "字典管理", 1, null, "/dict", "dict/index", "book", 80));
        // 用户按钮
        specs.add(new PermSpec("user:view", "查看", 2, "menu:user", null, null, null, 1));
        specs.add(new PermSpec("user:create", "新增", 2, "menu:user", null, null, null, 2));
        specs.add(new PermSpec("user:update", "编辑", 2, "menu:user", null, null, null, 3));
        specs.add(new PermSpec("user:delete", "删除", 2, "menu:user", null, null, null, 4));
        specs.add(new PermSpec("user:assign-role", "分配角色", 2, "menu:user", null, null, null, 5));
        specs.add(new PermSpec("user:reset-password", "重置密码", 2, "menu:user", null, null, null, 6));
        // 设备按钮
        specs.add(new PermSpec("device:view", "查看", 2, "menu:device", null, null, null, 1));
        specs.add(new PermSpec("device:trust", "设为可信", 2, "menu:device", null, null, null, 2));
        specs.add(new PermSpec("device:revoke", "吊销", 2, "menu:device", null, null, null, 3));
        specs.add(new PermSpec("device:remove", "移除", 2, "menu:device", null, null, null, 4));
        // 角色按钮
        specs.add(new PermSpec("role:view", "查看", 2, "menu:role", null, null, null, 1));
        specs.add(new PermSpec("role:create", "新增", 2, "menu:role", null, null, null, 2));
        specs.add(new PermSpec("role:update", "编辑", 2, "menu:role", null, null, null, 3));
        specs.add(new PermSpec("role:delete", "删除", 2, "menu:role", null, null, null, 4));
        specs.add(new PermSpec("role:assign", "分配权限", 2, "menu:role", null, null, null, 5));
        specs.add(new PermSpec("role:assign-group", "分配权限组", 2, "menu:role", null, null, null, 6));
        // 权限按钮
        specs.add(new PermSpec("permission:view", "查看", 2, "menu:permission", null, null, null, 1));
        specs.add(new PermSpec("permission:create", "新增", 2, "menu:permission", null, null, null, 2));
        specs.add(new PermSpec("permission:update", "编辑", 2, "menu:permission", null, null, null, 3));
        specs.add(new PermSpec("permission:delete", "删除", 2, "menu:permission", null, null, null, 4));
        // 权限组按钮
        specs.add(new PermSpec("permission-group:view", "查看", 2, "menu:permission-group", null, null, null, 1));
        specs.add(new PermSpec("permission-group:create", "新增", 2, "menu:permission-group", null, null, null, 2));
        specs.add(new PermSpec("permission-group:update", "编辑", 2, "menu:permission-group", null, null, null, 3));
        specs.add(new PermSpec("permission-group:delete", "删除", 2, "menu:permission-group", null, null, null, 4));
        specs.add(new PermSpec("permission-group:assign", "分配权限", 2, "menu:permission-group", null, null, null, 5));
        // 操作日志按钮
        specs.add(new PermSpec("operation-log:view", "查看", 2, "menu:operation-log", null, null, null, 1));
        // 登录日志按钮
        specs.add(new PermSpec("login-log:view", "查看", 2, "menu:login-log", null, null, null, 1));
        // 字典按钮（类型与项分两组，分别对应各自的增删改）
        specs.add(new PermSpec("dict-type:view", "查看", 2, "menu:dict", null, null, null, 1));
        specs.add(new PermSpec("dict-type:create", "新增", 2, "menu:dict", null, null, null, 2));
        specs.add(new PermSpec("dict-type:update", "编辑", 2, "menu:dict", null, null, null, 3));
        specs.add(new PermSpec("dict-type:delete", "删除", 2, "menu:dict", null, null, null, 4));
        specs.add(new PermSpec("dict-item:view", "查看", 2, "menu:dict", null, null, null, 5));
        specs.add(new PermSpec("dict-item:create", "新增", 2, "menu:dict", null, null, null, 6));
        specs.add(new PermSpec("dict-item:update", "编辑", 2, "menu:dict", null, null, null, 7));
        specs.add(new PermSpec("dict-item:delete", "删除", 2, "menu:dict", null, null, null, 8));
        // 接口权限（后端 @RequirePermission 校验）
        String[] apiDomains = {"user", "device", "role", "permission", "permission-group", "operation-log", "login-log", "dict-type", "dict-item"};
        String[][] apiActions = {
                {"list", "create", "update", "delete", "reset-password"},
                {"list", "trust", "revoke", "remove"},
                {"list", "create", "update", "delete", "assign", "assign-group"},
                {"list", "create", "update", "delete"},
                {"list", "create", "update", "delete", "assign"},
                {"list"},
                {"list"},
                {"list", "create", "update", "delete"},
                {"list", "create", "update", "delete"},
        };
        for (int i = 0; i < apiDomains.length; i++) {
            String[] actions = apiActions[i];
            for (int j = 0; j < actions.length; j++) {
                specs.add(new PermSpec("api:" + apiDomains[i] + ":" + actions[j],
                        apiDomains[i] + " " + actions[j] + " 接口", 3, null, null, null, null, j + 1));
            }
        }
        return specs;
    }

    private Map<String, Long> rebuildPermissions() {
        Map<String, Long> ids = new LinkedHashMap<>();
        List<PermSpec> specs = permissionCatalog();
        // 先插菜单（parentCode 为空），再插子项，保证 parentId 可用
        for (PermSpec spec : specs) {
            if (spec.parentCode() != null) {
                continue;
            }
            ids.put(spec.code(), insertPermission(spec, 0L));
        }
        for (PermSpec spec : specs) {
            if (spec.parentCode() == null) {
                continue;
            }
            Long parentId = ids.get(spec.parentCode());
            ids.put(spec.code(), insertPermission(spec, parentId == null ? 0L : parentId));
        }
        log.info("  权限 {} 个", ids.size());
        return ids;
    }

    private Long insertPermission(PermSpec spec, Long parentId) {
        Permission p = new Permission();
        p.setParentId(parentId);
        p.setCode(spec.code());
        p.setName(spec.name());
        p.setType(spec.type());
        p.setPath(spec.path());
        p.setComponent(spec.component());
        p.setIcon(spec.icon());
        p.setSort(spec.sort());
        p.setStatus(EnableStatus.ENABLED);
        permissionMapper.insert(p);
        return p.getId();
    }

    private List<Long> rebuildUsers() {
        List<User> users = new ArrayList<>();
        users.add(buildUser("admin", "管理员", "admin@fullstack-scaffold.local"));
        users.add(buildUser("guest", "访客", "guest@fullstack-scaffold.local"));
        users.add(buildUser("operator", "运营", "operator@fullstack-scaffold.local"));
        for (int i = 1; i <= properties.getUserCount(); i++) {
            users.add(buildUser(String.format("user_%03d", i), "测试用户 " + i,
                    String.format("user_%03d@fullstack-scaffold.local", i)));
        }
        // saveBatch：由统一 Snowflake 发号器逐条分配主键
        userService.saveBatch(users);
        List<Long> ids = users.stream().map(User::getId).toList();
        log.info("  用户 {} 个（含固定账号 3 个）", ids.size());
        return ids;
    }

    private User buildUser(String username, String nickname, String email) {
        User user = new User();
        user.setUsername(username);
        user.setNickname(nickname);
        user.setEmail(email);
        user.setPhone("138" + String.format("%08d", Math.abs(username.hashCode() % 100_000_000)));
        user.setPassword(passwordEncoder.encode("123456"));
        user.setStatus(EnableStatus.ENABLED);
        return user;
    }

    private void bindUserRoles(List<Long> userIds, Map<String, Long> roleIds) {
        Long adminRole = roleIds.get("ADMIN");
        Long userRole = roleIds.get("USER");
        Long operatorRole = roleIds.get("OPERATOR");
        List<UserRole> rows = new ArrayList<>();
        for (int i = 0; i < userIds.size(); i++) {
            Long userId = userIds.get(i);
            if (i == 0) {
                // admin：仅超级管理员
                rows.add(new UserRole(null, userId, adminRole));
            } else if (i == 1) {
                // guest：不绑定任何角色，用于验证无权限/403 视图
                continue;
            } else if (i == 2) {
                // operator：仅运营角色（权限目录中显式授权）
                rows.add(new UserRole(null, userId, operatorRole));
            } else {
                // 生成账号按序号轮换普通/运营角色
                rows.add(new UserRole(null, userId, (i % 2 == 0) ? userRole : operatorRole));
            }
        }
        List<Long> ids = idGenerator.nextIds(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setId(ids.get(i));
        }
        userRoleMapper.insertBatch(rows);
        log.info("  用户-角色关联 {} 条", rows.size());
    }

    private void bindRolePermissions(Map<String, Long> roleIds, Map<String, Long> permissionIds) {
        Long adminRole = roleIds.get("ADMIN");
        List<RolePermission> adminRows = new ArrayList<>();
        List<Long> allPermIds = new ArrayList<>(permissionIds.values());
        List<Long> ids = idGenerator.nextIds(allPermIds.size());
        for (int i = 0; i < allPermIds.size(); i++) {
            adminRows.add(new RolePermission(ids.get(i), adminRole, allPermIds.get(i)));
        }
        rolePermissionMapper.insertBatch(adminRows);

        // USER：仅查看类
        List<Long> userPermIds = pickPermissions(permissionIds, List.of(
                "menu:user", "user:view", "api:user:list",
                "menu:device", "device:view", "device:trust", "device:revoke", "device:remove",
                "api:device:list", "api:device:trust", "api:device:revoke", "api:device:remove",
                "menu:role", "role:view", "api:role:list"));
        insertRolePermissions(roleIds.get("USER"), userPermIds);

        // OPERATOR：用户与设备的读写
        List<Long> operatorPermIds = pickPermissions(permissionIds, List.of(
                "menu:user", "user:view", "user:create", "user:update", "user:assign-role",
                "user:reset-password",
                "api:user:list", "api:user:create", "api:user:update", "api:user:reset-password",
                "menu:device", "device:view", "device:trust", "device:revoke",
                "api:device:list", "api:device:trust", "api:device:revoke"));
        insertRolePermissions(roleIds.get("OPERATOR"), operatorPermIds);

        log.info("  角色-权限关联 {} 条", adminRows.size() + userPermIds.size() + operatorPermIds.size());
    }

    private void insertRolePermissions(Long roleId, List<Long> permissionIds) {
        if (permissionIds.isEmpty()) {
            return;
        }
        List<Long> ids = idGenerator.nextIds(permissionIds.size());
        List<RolePermission> rows = new ArrayList<>(permissionIds.size());
        for (int i = 0; i < permissionIds.size(); i++) {
            rows.add(new RolePermission(ids.get(i), roleId, permissionIds.get(i)));
        }
        rolePermissionMapper.insertBatch(rows);
    }

    private List<Long> pickPermissions(Map<String, Long> permissionIds, List<String> codes) {
        List<Long> result = new ArrayList<>();
        for (String code : codes) {
            Long id = permissionIds.get(code);
            if (id != null) {
                result.add(id);
            }
        }
        return result;
    }

    private List<Long> rebuildPermissionGroups(Map<String, Long> permissionIds) {
        List<Long> groupIds = new ArrayList<>();
        groupIds.add(insertGroup("GROUP_USER_READONLY", "用户只读组", "用户模块只读权限集合", 1,
                pickPermissions(permissionIds, List.of("menu:user", "user:view", "api:user:list"))));
        groupIds.add(insertGroup("GROUP_DEVICE_SELF", "设备自服务组", "登录设备自服务权限集合", 2,
                pickPermissions(permissionIds, List.of("menu:device", "device:view", "device:trust",
                        "device:revoke", "device:remove", "api:device:list", "api:device:trust",
                        "api:device:revoke", "api:device:remove"))));
        log.info("  权限组 {} 个", groupIds.size());
        return groupIds;
    }

    private Long insertGroup(String code, String name, String description, int sort, List<Long> permIds) {
        PermissionGroup group = new PermissionGroup();
        group.setCode(code);
        group.setName(name);
        group.setDescription(description);
        group.setSort(sort);
        group.setStatus(EnableStatus.ENABLED);
        permissionGroupMapper.insert(group);

        if (!permIds.isEmpty()) {
            List<Long> ids = idGenerator.nextIds(permIds.size());
            List<PermissionGroupItem> items = new ArrayList<>(permIds.size());
            for (int i = 0; i < permIds.size(); i++) {
                items.add(new PermissionGroupItem(ids.get(i), group.getId(), permIds.get(i)));
            }
            permissionGroupItemMapper.insertBatch(items);
        }
        return group.getId();
    }

    private void bindRolePermissionGroups(Map<String, Long> roleIds, List<Long> groupIds) {
        List<RolePermissionGroup> rows = new ArrayList<>();
        rows.add(new RolePermissionGroup(null, roleIds.get("USER"), groupIds.get(0)));
        rows.add(new RolePermissionGroup(null, roleIds.get("USER"), groupIds.get(1)));
        rows.add(new RolePermissionGroup(null, roleIds.get("OPERATOR"), groupIds.get(1)));
        List<Long> ids = idGenerator.nextIds(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setId(ids.get(i));
        }
        rolePermissionGroupMapper.insertBatch(rows);
        log.info("  角色-权限组关联 {} 条", rows.size());
    }

    private int rebuildDevices(List<Long> userIds) {
        List<UserDevice> devices = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < userIds.size(); i++) {
            int count = 1 + (i % 3);
            for (int d = 0; d < count; d++) {
                UserDevice device = new UserDevice();
                // 全局严格序号：由统一序列分配器分配，跨节点唯一且单调递增
                device.setSortSeq(sequenceAllocator.next(UserDeviceRegistry.DEVICE_SEQ_KEY));
                device.setUserId(userIds.get(i));
                device.setDeviceId(UUID.randomUUID().toString().replace("-", ""));
                device.setFingerprint(sha256Hex(userIds.get(i) + "-" + d + "-" + UUID.randomUUID()));
                device.setIp("192.168.1." + (1 + (i % 200)));
                device.setRiskLevel(d == 0 ? RiskLevel.TRUSTED.name() : RiskLevel.NORMAL.name());
                device.setLastLoginTime(now.minusHours(d));
                device.setCreateTime(now.minusDays(1));
                devices.add(device);
            }
        }
        // 逐条插入以触发 Snowflake 主键分配；数据量小，无需批处理
        devices.forEach(deviceMapper::insert);
        return devices.size();
    }

    // ========================================================================
    // 工具
    // ========================================================================

    private void checkOrphan(List<String> failures, String label, String table, String column, String refTable) {
        Long orphan = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " t LEFT JOIN " + refTable + " r ON t." + column + " = r.id "
                        + "WHERE r.id IS NULL", Long.class);
        if (orphan != null && orphan > 0) {
            failures.add(label + " 有 " + orphan + " 条孤儿记录");
        }
    }

    /** 统计业务表行数。 */
    public Map<String, Long> countBusiness() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : REPORT_TABLES) {
            counts.put(table, countOf(table));
        }
        return counts;
    }

    /** 统计全部表行数（含被重建程序保护的两张基础设施表）。 */
    public Map<String, Long> countAll() {
        Map<String, Long> counts = countBusiness();
        counts.put("sys_sequence", countOf("sys_sequence"));
        counts.put("sys_worker_node", countOf("sys_worker_node"));
        return counts;
    }

    private Long countOf(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    public String currentDatabase() {
        return jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
    }

    @Nullable
    public String resolveJdbcUrl() {
        if (dataSource instanceof HikariDataSource hikari) {
            return hikari.getJdbcUrl();
        }
        return null;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
