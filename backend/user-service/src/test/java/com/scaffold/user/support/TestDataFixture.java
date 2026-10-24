package com.scaffold.user.support;

import com.scaffold.common.exception.BizException;
import com.scaffold.user.entity.Role;
import com.scaffold.user.entity.User;
import com.scaffold.user.service.RoleService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 业务测试的用例数据工厂。
 *
 * <p>三条约定：
 * <ul>
 *   <li><b>不依赖种子数据</b>。CI 的 MySQL 只由 Flyway 建表、不含任何账号与角色，
 *       因此需要角色时用例必须自建，而不是去库里「取两个现成的」；</li>
 *   <li>用户名一律带纳秒后缀，与其它用例及既存数据隔离，重复执行不会互相冲突；</li>
 *   <li>清理走<b>物理删除</b>。逻辑删除只把 deleted 置 1，唯一索引仍占着该用户名，
 *       下一个用例再建同名账号会撞唯一键。</li>
 * </ul>
 *
 * <p>用例只需在 {@code @AfterEach} 里调 {@link #cleanup()}，不必逐个跟踪自己建了什么；
 * 放 {@code support} 包下由组件扫描拾取：该包只在测试 classpath 中存在。
 */
@Component
public class TestDataFixture {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private final JdbcTemplate jdbcTemplate;
    private final RoleService roleService;

    /** 本轮用例建出的数据，cleanup 时倒序回收 */
    private final List<Long> trackedUsers = new ArrayList<>();
    private final List<Long> trackedRoles = new ArrayList<>();

    public TestDataFixture(JdbcTemplate jdbcTemplate, RoleService roleService) {
        this.jdbcTemplate = jdbcTemplate;
        this.roleService = roleService;
    }

    /** 唯一用户名：纳秒 + 自增序号，同一 JVM 内也不会重名 */
    public String uniqueUsername() {
        return "it_user_" + System.nanoTime() + "_" + SEQ.incrementAndGet();
    }

    public User user(String username, String rawPassword) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(rawPassword);
        user.setNickname("集成测试用户");
        user.setEmail(username + "@it.local");
        return user;
    }

    /** 跟踪用例创建的用户，交给 {@link #cleanup()} 回收 */
    public void trackUser(Long userId) {
        if (userId != null) {
            trackedUsers.add(userId);
        }
    }

    /**
     * 自建角色（不读种子数据），并登记回收。
     * 权限分配由用例另行调用 RoleService 完成。
     */
    public Role createRole(String codePrefix) {
        Role role = new Role();
        role.setCode(codePrefix + "_" + System.nanoTime() + "_" + SEQ.incrementAndGet());
        role.setName("集成测试角色 " + role.getCode());
        role.setSort(999);
        Role created = roleService.create(role);
        trackedRoles.add(created.getId());
        return created;
    }

    /**
     * 物理删除用例用户：先断开关联表再删主体，避免外键约束报错。
     */
    public void deleteUser(Long userId) {
        if (userId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user WHERE id = ?", userId);
    }

    /**
     * 回收本轮用例建出的角色与用户。角色先删（其权限绑定由服务层一并清理），
     * 再删用户，避免关联表留下指向已删角色的行。
     */
    public void cleanup() {
        for (Long roleId : trackedRoles) {
            quietlyDeleteRole(roleId);
        }
        trackedRoles.clear();
        for (Long userId : trackedUsers) {
            quietlyDeleteUser(userId);
        }
        trackedUsers.clear();
    }

    private void quietlyDeleteRole(Long roleId) {
        try {
            roleService.deleteById(roleId);
        } catch (BizException ignored) {
            // 用例内已自行删除，无需重试
        }
    }

    private void quietlyDeleteUser(Long userId) {
        try {
            deleteUser(userId);
        } catch (RuntimeException ignored) {
            // 同上：删除是清理动作，失败不应掩盖用例结果
        }
    }
}
