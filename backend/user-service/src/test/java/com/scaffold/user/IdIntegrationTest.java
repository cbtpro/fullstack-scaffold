package com.scaffold.user;

import com.scaffold.common.api.PageQuery;
import com.scaffold.common.api.PageResult;
import com.scaffold.id.GlobalSequenceAllocator;
import com.scaffold.id.JdbcWorkerIdLease;
import com.scaffold.id.SnowflakeIdGenerator;
import com.scaffold.user.entity.Permission;
import com.scaffold.user.entity.Role;
import com.scaffold.user.entity.User;
import com.scaffold.user.enums.EnableStatus;
import com.scaffold.user.mapper.PermissionMapper;
import com.scaffold.user.mapper.RoleMapper;
import com.scaffold.user.mapper.UserRoleMapper;
import com.scaffold.user.service.impl.UserServiceImpl;
import com.scaffold.user.support.ItDatabase;
import com.scaffold.user.testdata.TestDataProperties;
import com.scaffold.user.testdata.TestDataRebuildRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据库集成测试：独立 MySQL 测试实例验证主键策略、CRUD、主外键关联、
 * 排序、全局序号分配与测试数据重建。
 *
 * <p>使用独立 MySQL 实例而非 Testcontainers：本机 Docker 29.x 与
 * Testcontainers 1.19.x 的 docker-java 客户端存在 API 版本协商问题
 * （{@code /_ping} 返回 400）。测试实例由 docker compose 提供：
 * <pre>
 * docker compose -f docker/docker-compose.test.yml up -d --wait
 * </pre>
 * 连接参数可用系统属性覆盖：{@code -Dit.db.url=... -Dit.db.user=... -Dit.db.password=...}
 *
 * <p>实例不可用时整类跳过（{@code @EnabledIf}），而不是判失败——
 * 缺基础设施不是代码缺陷，但也不能静悄悄放行，跳过原因会打印到标准输出。
 *
 * <p>重建用例排在最后（{@code @Order(100)}），避免清空其他用例的数据。
 */
@SpringBootTest(properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "captcha.cache-mode=memory",
        "test-data.exit-after-run=false",
        "test-data.confirm=I_UNDERSTAND",
        "test-data.user-count=5"
})
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIf("databaseReachable")
class IdIntegrationTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        ItDatabase.register(registry);
    }

    @SuppressWarnings("unused")
    static boolean databaseReachable() {
        return ItDatabase.isReachable();
    }

    @Autowired
    private UserServiceImpl userService;
    @Autowired
    private RoleMapper roleMapper;
    @Autowired
    private PermissionMapper permissionMapper;
    @Autowired
    private UserRoleMapper userRoleMapper;
    @Autowired
    private SnowflakeIdGenerator idGenerator;
    @Autowired
    private GlobalSequenceAllocator sequenceAllocator;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TestDataRebuildRunner rebuildRunner;
    @Autowired
    private TestDataProperties testDataProperties;

    private static User newUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setNickname(username);
        user.setEmail(username + "@test.local");
        user.setPassword("123456");
        return user;
    }

    @Test
    @Order(1)
    @DisplayName("所有业务表主键列不含 AUTO_INCREMENT")
    void noAutoIncrementOnPrimaryKey() {
        List<String> autoInc = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'id' "
                        + "AND EXTRA LIKE '%auto_increment%'", String.class);
        assertTrue(autoInc.isEmpty(), "以下表的主键仍是自增: " + autoInc);
    }

    @Test
    @Order(2)
    @DisplayName("save 由统一发号器分配 Snowflake 主键")
    void saveAssignsSnowflakeId() {
        User user = newUser("it_save_1");
        userService.create(user);
        assertNotNull(user.getId());
        assertTrue(idGenerator.validate(user.getId()), "主键未通过 Snowflake 结构校验");
        assertEquals(0, idGenerator.parse(user.getId()).workerId());
    }

    @Test
    @Order(3)
    @DisplayName("saveBatch 批量插入：主键非空、唯一、递增")
    void saveBatchAssignsUniqueIds() {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            users.add(newUser("it_batch_" + i));
        }
        assertTrue(userService.saveBatch(users));

        Set<Long> seen = new HashSet<>();
        Long prev = null;
        for (User u : users) {
            assertNotNull(u.getId(), "saveBatch 未分配主键");
            assertTrue(seen.add(u.getId()), "saveBatch 产生重复主键: " + u.getId());
            assertTrue(idGenerator.validate(u.getId()));
            if (prev != null) {
                assertTrue(u.getId() > prev, "saveBatch 主键未递增");
            }
            prev = u.getId();
        }
    }

    @Test
    @Order(4)
    @DisplayName("updateById 不重新生成主键")
    void updateByIdKeepsId() {
        User user = newUser("it_update_1");
        userService.create(user);
        Long original = user.getId();

        user.setNickname("改过的昵称");
        userService.updateById(user);
        assertEquals(original, user.getId(), "updateById 修改了主键");
        assertEquals("改过的昵称", userService.getById(original).getNickname());
    }

    @Test
    @Order(5)
    @DisplayName("saveOrUpdate：无 id 插入并分配主键，有 id 更新且不改主键")
    void saveOrUpdateSemantics() {
        User user = newUser("it_upsert_1");
        userService.saveOrUpdate(user);
        Long id = user.getId();
        assertNotNull(id);

        user.setNickname("upsert 后");
        userService.saveOrUpdate(user);
        assertEquals(id, user.getId());
        assertEquals("upsert 后", userService.getById(id).getNickname());
    }

    @Test
    @Order(6)
    @DisplayName("removeById 走逻辑删除，记录仍在库中且 deleted=1")
    void removeByIdUsesLogicDelete() {
        User user = newUser("it_remove_1");
        userService.create(user);
        Long id = user.getId();

        assertTrue(userService.removeById(id));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user WHERE id = ? AND deleted = 1", Integer.class, id));
    }

    @Test
    @Order(7)
    @DisplayName("关联表批量插入使用 Snowflake 主键，主外键关联完整")
    void associationUsesSnowflakeId() {
        Role role = new Role();
        role.setCode("IT_ROLE_" + System.nanoTime());
        role.setName("集成测试角色");
        role.setStatus(EnableStatus.ENABLED);
        roleMapper.insert(role);

        User user = newUser("it_assoc_1");
        userService.create(user);

        userService.assignRoles(user.getId(), List.of(role.getId()));

        List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(user.getId());
        assertEquals(1, roleIds.size());
        assertEquals(role.getId(), roleIds.get(0));

        List<Long> assocIds = jdbcTemplate.queryForList(
                "SELECT id FROM sys_user_role WHERE user_id = ?", Long.class, user.getId());
        assertEquals(1, assocIds.size());
        assertTrue(idGenerator.validate(assocIds.get(0)), "关联表主键不是合法 Snowflake ID");

        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user_role ur LEFT JOIN user u ON ur.user_id = u.id WHERE u.id IS NULL",
                Long.class), "存在孤儿关联记录");

        // 重复分配（先删后插）不应产生重复主键
        userService.assignRoles(user.getId(), List.of(role.getId()));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (SELECT id FROM sys_user_role GROUP BY id HAVING COUNT(*) > 1) t",
                Long.class));
    }

    @Test
    @Order(8)
    @DisplayName("普通列表排序稳定：同一查询两次返回顺序一致")
    void listOrderingIsStable() {
        for (int i = 0; i < 5; i++) {
            userService.create(newUser("it_order_" + i + "_" + System.nanoTime()));
        }
        PageQuery query = new PageQuery();
        query.setPage(1);
        query.setSize(10);

        PageResult<User> first = userService.page(query, null);
        PageResult<User> second = userService.page(query, null);
        assertEquals(first.getList().size(), second.getList().size());
        for (int i = 0; i < first.getList().size(); i++) {
            assertEquals(first.getList().get(i).getId(), second.getList().get(i).getId(),
                    "同一分页两次查询结果顺序不一致");
        }
        assertFalse(first.getList().isEmpty());
    }

    @Test
    @Order(9)
    @DisplayName("分页 total 正确（分页拦截器生效）")
    void paginationTotalIsCorrect() {
        int total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user WHERE deleted = 0", Integer.class);
        PageQuery query = new PageQuery();
        query.setPage(1);
        query.setSize(5);
        PageResult<User> page = userService.page(query, null);
        assertEquals(total, page.getTotal());
        assertEquals(1, page.getPageNum());
        assertEquals(5, page.getPageSize());
    }

    @Test
    @Order(10)
    @DisplayName("权限实体插入与 parent_id 树形关联")
    void permissionInsertAndParentLink() {
        Permission menu = new Permission();
        menu.setParentId(0L);
        menu.setCode("it:menu:" + System.nanoTime());
        menu.setName("集成测试菜单");
        menu.setType(1);
        menu.setStatus(EnableStatus.ENABLED);
        permissionMapper.insert(menu);
        assertNotNull(menu.getId());
        assertTrue(idGenerator.validate(menu.getId()));

        Permission button = new Permission();
        button.setParentId(menu.getId());
        button.setCode("it:button:" + System.nanoTime());
        button.setName("集成测试按钮");
        button.setType(2);
        button.setStatus(EnableStatus.ENABLED);
        permissionMapper.insert(button);

        assertNotEquals(menu.getId(), button.getId());
        assertEquals(menu.getId(), permissionMapper.selectById(button.getId()).getParentId());
    }

    @Test
    @Order(11)
    @DisplayName("全局序号分配器：单线程单调递增")
    void sequenceAllocatorMonotonic() {
        long a = sequenceAllocator.next("it_seq");
        long b = sequenceAllocator.next("it_seq");
        long c = sequenceAllocator.next("it_seq");
        assertTrue(a < b && b < c, "序号未单调递增: " + a + ", " + b + ", " + c);
    }

    @Test
    @Order(12)
    @DisplayName("全局序号分配器：批量分配连续且升序")
    void sequenceAllocatorBatch() {
        List<Long> values = sequenceAllocator.next("it_seq_batch", 10);
        assertEquals(10, values.size());
        for (int i = 1; i < values.size(); i++) {
            assertEquals(values.get(i - 1) + 1, values.get(i), "批量序号不连续");
        }
    }

    @Test
    @Order(13)
    @DisplayName("全局序号分配器：并发分配不重复")
    void sequenceAllocatorConcurrent() throws Exception {
        int threads = 8;
        int perThread = 25;
        Set<Long> all = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        all.add(sequenceAllocator.next("it_seq_concurrent"));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdownNow();
        assertEquals(threads * perThread, all.size(), "并发分配出现重复序号");
    }

    @Test
    @Order(14)
    @DisplayName("Worker ID 租约：分配、重启复用、续约、冲突检测、释放")
    void workerIdLease() {
        JdbcWorkerIdLease lease = new JdbcWorkerIdLease(jdbcTemplate, 30_000L);
        String nodeKey = "it-node-" + System.nanoTime();

        int workerId = lease.acquire(nodeKey, 1023);
        assertTrue(workerId >= 0 && workerId <= 1023, "workerId 超出范围: " + workerId);

        // 同一 nodeKey 再次 acquire → 复用同一个 workerId（节点重启安全）
        assertEquals(workerId, lease.acquire(nodeKey, 1023));

        assertTrue(lease.renew(workerId, nodeKey), "续约应成功");
        assertFalse(lease.renew(workerId, "wrong-node-" + System.nanoTime()),
                "错误 nodeKey 续约不应成功（冲突检测）");

        lease.release(workerId, nodeKey);
        assertFalse(lease.renew(workerId, nodeKey), "释放后续约应失败");
    }

    @Test
    @Order(100)
    @DisplayName("测试数据重建：rebuild 后 verify 通过，且可重复执行")
    void rebuildAndVerify() {
        rebuildRunner.rebuild();
        rebuildRunner.verify();

        long usersAfterFirst = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user", Long.class);
        assertEquals(8L, usersAfterFirst, "固定账号 3 个 + test-data.user-count=5");

        rebuildRunner.rebuild();
        rebuildRunner.verify();
        long usersAfterSecond = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user", Long.class);
        assertEquals(usersAfterFirst, usersAfterSecond, "重复重建后数据量应一致");

        // 重建不得重置序列高水位
        assertTrue(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_sequence WHERE biz_key = 'user_device'", Long.class) > 0,
                "sys_sequence 应保留序号高水位");
    }

    @Test
    @Order(101)
    @DisplayName("数据库不在白名单时 rebuild 被拒绝")
    void rebuildRejectedWhenDatabaseNotAllowed() {
        List<String> original = new ArrayList<>(testDataProperties.getAllowedDatabases());
        try {
            testDataProperties.setAllowedDatabases(List.of("some_other_db"));
            IllegalStateException ex = assertThrows(IllegalStateException.class, rebuildRunner::rebuild);
            assertTrue(ex.getMessage().contains("不在白名单"), "异常信息应说明白名单校验失败: " + ex.getMessage());
        } finally {
            testDataProperties.setAllowedDatabases(original);
        }
    }
}
