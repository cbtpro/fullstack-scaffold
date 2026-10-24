package com.scaffold.user.service;

import com.scaffold.common.exception.BizException;
import com.scaffold.user.entity.Permission;
import com.scaffold.user.entity.PermissionGroup;
import com.scaffold.user.entity.Role;
import com.scaffold.user.entity.User;
import com.scaffold.user.enums.EnableStatus;
import com.scaffold.user.support.ItDatabase;
import com.scaffold.user.support.TestDataFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 权限解析业务测试（DB 集成）。
 *
 * <p>权限解析 SQL（user → role → permission，外加 role → group → item → permission 一条并集路径）
 * 是全项目最要害的一段：解析错了要么该拦的没拦（越权），要么该放的没放（界面点不动），
 * 而两种症状都不会在编译期或启动期出现。这里用真实库把并集、停用、逻辑删除三种过滤逐条钉住。
 *
 * <p>用例自建角色/权限/权限组，跑完逐个删掉，不与种子数据交叉。
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIf("databaseReachable")
class PermissionResolutionBusinessTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final int TYPE_MENU = 1;
    private static final int TYPE_BUTTON = 2;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        ItDatabase.register(registry);
    }

    @SuppressWarnings("unused")
    static boolean databaseReachable() {
        return ItDatabase.isReachable();
    }

    @Autowired
    private RbacService rbacService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private PermissionGroupService permissionGroupService;

    @Autowired
    private UserService userService;

    @Autowired
    private TestDataFixture fixture;

    private static String unique(String prefix) {
        return prefix + "_" + System.nanoTime() + "_" + SEQ.incrementAndGet();
    }

    private Permission newPermission(String codePrefix, int type) {
        Permission permission = new Permission();
        permission.setCode(unique(codePrefix));
        permission.setName("测试权限 " + permission.getCode());
        permission.setType(type);
        permission.setParentId(0L);
        permission.setSort(999);
        return permission;
    }

    private Role newRole(String codePrefix) {
        Role role = new Role();
        role.setCode(unique(codePrefix));
        role.setName("测试角色 " + role.getCode());
        role.setSort(999);
        return role;
    }

    private PermissionGroup newGroup(String codePrefix) {
        PermissionGroup group = new PermissionGroup();
        group.setCode(unique(codePrefix));
        group.setName("测试权限组 " + group.getCode());
        group.setSort(999);
        return group;
    }

    @Test
    @DisplayName("权限解析：直接分配与权限组内权限取并集，重复授权只出现一次")
    void resolvesDirectAndGroupedPermissions() {
        Permission direct = permissionService.create(newPermission("it_perm_direct", TYPE_BUTTON));
        Permission viaGroup = permissionService.create(newPermission("it_perm_group", TYPE_BUTTON));
        // 同一个权限既直接分配又放进权限组，验证 UNION 去重
        Permission both = permissionService.create(newPermission("it_perm_both", TYPE_BUTTON));

        PermissionGroup group = permissionGroupService.create(newGroup("it_group"));
        permissionGroupService.assignPermissions(group.getId(), List.of(viaGroup.getId(), both.getId()));

        Role role = roleService.create(newRole("it_role_resolve"));
        roleService.assignPermissions(role.getId(), List.of(direct.getId(), both.getId()));
        roleService.assignGroups(role.getId(), List.of(group.getId()));

        String username = fixture.uniqueUsername();
        User user = userService.create(fixture.user(username, "Secret#123"));
        userService.assignRoles(user.getId(), List.of(role.getId()));

        List<String> codes = rbacService.listPermissionCodesByUserId(user.getId());
        assertThat(codes).contains(direct.getCode(), viaGroup.getCode(), both.getCode());
        assertThat(codes).containsOnlyOnce(both.getCode());

        cleanup(user.getId(), role.getId(), group.getId(), direct.getId(), viaGroup.getId(), both.getId());
    }

    @Test
    @DisplayName("停用权限组内的权限立即失效，直接分配的那条不受影响")
    void disabledGroupDropsItsPermissions() {
        Permission direct = permissionService.create(newPermission("it_perm_keep", TYPE_BUTTON));
        Permission grouped = permissionService.create(newPermission("it_perm_drop", TYPE_BUTTON));

        PermissionGroup group = permissionGroupService.create(newGroup("it_group_toggle"));
        permissionGroupService.assignPermissions(group.getId(), List.of(grouped.getId()));

        Role role = roleService.create(newRole("it_role_toggle"));
        roleService.assignPermissions(role.getId(), List.of(direct.getId()));
        roleService.assignGroups(role.getId(), List.of(group.getId()));

        String username = fixture.uniqueUsername();
        User user = userService.create(fixture.user(username, "Secret#123"));
        userService.assignRoles(user.getId(), List.of(role.getId()));
        assertThat(rbacService.listPermissionCodesByUserId(user.getId()))
                .contains(direct.getCode(), grouped.getCode());

        PermissionGroup patch = new PermissionGroup();
        patch.setId(group.getId());
        patch.setStatus(EnableStatus.DISABLED);
        permissionGroupService.update(patch);

        assertThat(rbacService.listPermissionCodesByUserId(user.getId()))
                .contains(direct.getCode())
                .doesNotContain(grouped.getCode());

        // 权限组整体删除后同样不再参与解析
        permissionGroupService.deleteById(group.getId());
        assertThat(rbacService.listPermissionCodesByUserId(user.getId()))
                .contains(direct.getCode())
                .doesNotContain(grouped.getCode());

        cleanup(user.getId(), role.getId(), group.getId(), direct.getId(), grouped.getId());
    }

    @Test
    @DisplayName("停用或删除权限：两种方式都让它立刻从用户权限里消失")
    void disabledOrDeletedPermissionIsDropped() {
        Permission toDisable = permissionService.create(newPermission("it_perm_stop", TYPE_BUTTON));
        Permission toDelete = permissionService.create(newPermission("it_perm_rm", TYPE_BUTTON));

        Role role = roleService.create(newRole("it_role_stop"));
        roleService.assignPermissions(role.getId(), List.of(toDisable.getId(), toDelete.getId()));

        String username = fixture.uniqueUsername();
        User user = userService.create(fixture.user(username, "Secret#123"));
        userService.assignRoles(user.getId(), List.of(role.getId()));
        assertThat(rbacService.listPermissionCodesByUserId(user.getId()))
                .contains(toDisable.getCode(), toDelete.getCode());

        Permission patch = new Permission();
        patch.setId(toDisable.getId());
        patch.setStatus(EnableStatus.DISABLED);
        permissionService.update(patch);
        permissionService.deleteById(toDelete.getId());

        assertThat(rbacService.listPermissionCodesByUserId(user.getId()))
                .doesNotContain(toDisable.getCode())
                .doesNotContain(toDelete.getCode());

        cleanup(user.getId(), role.getId(), null, toDisable.getId(), toDelete.getId());
    }

    @Test
    @DisplayName("停用角色：用户与角色的绑定还在，但不再贡献权限与菜单")
    void disabledRoleContributesNothing() {
        Permission permission = permissionService.create(newPermission("it_perm_role", TYPE_BUTTON));
        Permission menu = permissionService.create(newPermission("it_menu_role", TYPE_MENU));

        Role role = roleService.create(newRole("it_role_off"));
        roleService.assignPermissions(role.getId(), List.of(permission.getId(), menu.getId()));

        String username = fixture.uniqueUsername();
        User user = userService.create(fixture.user(username, "Secret#123"));
        userService.assignRoles(user.getId(), List.of(role.getId()));
        assertThat(rbacService.listPermissionCodesByUserId(user.getId()))
                .contains(permission.getCode(), menu.getCode());
        assertThat(rbacService.listMenusByUserId(user.getId()))
                .extracting(Permission::getId).contains(menu.getId());

        Role patch = new Role();
        patch.setId(role.getId());
        patch.setStatus(EnableStatus.DISABLED);
        roleService.update(patch);

        // 停用角色后权限与菜单都必须立刻消失，且与角色码解析口径一致
        assertThat(rbacService.listRoleCodesByUserId(user.getId())).doesNotContain(role.getCode());
        assertThat(rbacService.listPermissionCodesByUserId(user.getId()))
                .doesNotContain(permission.getCode(), menu.getCode());
        assertThat(rbacService.listMenusByUserId(user.getId()))
                .extracting(Permission::getId).doesNotContain(menu.getId());
        // 绑定关系本身仍在（解绑是另一件事，由 assignRoles 负责）
        assertThat(userService.listRoleIds(user.getId())).contains(role.getId());

        cleanup(user.getId(), role.getId(), null, permission.getId(), menu.getId());
    }

    @Test
    @DisplayName("菜单解析只返回菜单类型：按钮与接口不会出现在侧边栏数据里")
    void menusOnlyContainMenuType() {
        Permission menu = permissionService.create(newPermission("it_menu", TYPE_MENU));
        Permission button = permissionService.create(newPermission("it_button", TYPE_BUTTON));
        Permission api = permissionService.create(newPermission("it_api", 3));

        Role role = roleService.create(newRole("it_role_menu"));
        roleService.assignPermissions(role.getId(),
                List.of(menu.getId(), button.getId(), api.getId()));

        String username = fixture.uniqueUsername();
        User user = userService.create(fixture.user(username, "Secret#123"));
        userService.assignRoles(user.getId(), List.of(role.getId()));

        List<Permission> menus = rbacService.listMenusByUserId(user.getId());
        assertThat(menus).extracting(Permission::getId).containsExactly(menu.getId());
        // 权限码解析仍然包含它们，菜单过滤只作用于渲染数据
        assertThat(rbacService.listPermissionCodesByUserId(user.getId()))
                .contains(menu.getCode(), button.getCode(), api.getCode());

        cleanup(user.getId(), role.getId(), null, menu.getId(), button.getId(), api.getId());
    }

    @Test
    @DisplayName("重复分配权限与权限组：先清后置，以最后一次为准")
    void assignPermissionsAndGroupsReplaces() {
        Permission first = permissionService.create(newPermission("it_perm_r1", TYPE_BUTTON));
        Permission second = permissionService.create(newPermission("it_perm_r2", TYPE_BUTTON));
        PermissionGroup groupA = permissionGroupService.create(newGroup("it_group_a"));
        PermissionGroup groupB = permissionGroupService.create(newGroup("it_group_b"));

        Role role = roleService.create(newRole("it_role_assign"));
        roleService.assignPermissions(role.getId(), List.of(first.getId(), second.getId()));
        assertThat(roleService.listPermissionIds(role.getId()))
                .containsExactlyInAnyOrder(first.getId(), second.getId());

        roleService.assignPermissions(role.getId(), List.of(second.getId()));
        assertThat(roleService.listPermissionIds(role.getId())).containsExactly(second.getId());

        roleService.assignGroups(role.getId(), List.of(groupA.getId(), groupB.getId()));
        assertThat(roleService.listGroupIds(role.getId()))
                .containsExactlyInAnyOrder(groupA.getId(), groupB.getId());

        roleService.assignGroups(role.getId(), List.of(groupB.getId()));
        assertThat(roleService.listGroupIds(role.getId())).containsExactly(groupB.getId());

        roleService.assignPermissions(role.getId(), List.of());
        roleService.assignGroups(role.getId(), List.of());
        assertThat(roleService.listPermissionIds(role.getId())).isEmpty();
        assertThat(roleService.listGroupIds(role.getId())).isEmpty();

        cleanup(null, role.getId(), null, first.getId(), second.getId(), groupA.getId(), groupB.getId());
    }

    @Test
    @DisplayName("权限组分配：先清后置，组内权限数正确")
    void groupAssignmentReplaces() {
        Permission first = permissionService.create(newPermission("it_gp_1", TYPE_BUTTON));
        Permission second = permissionService.create(newPermission("it_gp_2", TYPE_BUTTON));
        PermissionGroup group = permissionGroupService.create(newGroup("it_group_assign"));

        permissionGroupService.assignPermissions(group.getId(), List.of(first.getId(), second.getId()));
        assertThat(permissionGroupService.listPermissionIds(group.getId()))
                .containsExactlyInAnyOrder(first.getId(), second.getId());

        permissionGroupService.assignPermissions(group.getId(), List.of(first.getId()));
        assertThat(permissionGroupService.listPermissionIds(group.getId())).containsExactly(first.getId());

        cleanup(null, null, group.getId(), first.getId(), second.getId());
    }

    @Test
    @DisplayName("无角色用户解析出空权限，不报错也不给默认权限")
    void userWithoutRolesHasNoPermission() {
        String username = fixture.uniqueUsername();
        User user = userService.create(fixture.user(username, "Secret#123"));

        assertThat(rbacService.listRoleCodesByUserId(user.getId())).isEmpty();
        assertThat(rbacService.listPermissionCodesByUserId(user.getId())).isEmpty();
        assertThat(rbacService.listMenusByUserId(user.getId())).isEmpty();

        cleanup(user.getId(), null, null);
    }

    @Test
    @DisplayName("删除角色：其权限与权限组绑定一并清理，不留悬挂关联")
    void deleteRoleCleansBindings() {
        Permission permission = permissionService.create(newPermission("it_perm_del_role", TYPE_BUTTON));
        PermissionGroup group = permissionGroupService.create(newGroup("it_group_del_role"));

        Role role = roleService.create(newRole("it_role_del"));
        roleService.assignPermissions(role.getId(), List.of(permission.getId()));
        roleService.assignGroups(role.getId(), List.of(group.getId()));
        assertThat(roleService.deleteById(role.getId())).isTrue();

        assertThat(roleService.listPermissionIds(role.getId())).isEmpty();
        assertThat(roleService.listGroupIds(role.getId())).isEmpty();

        cleanup(null, null, group.getId(), permission.getId());
    }

    /**
     * 用例数据清理：删角色 → 权限组 → 权限 → 用户。
     * 用例内部可能已经删过其中某项，因此每步都按「不存在即跳过」处理——
     * 清理不该因为用例已经完成删除而报错。
     */
    private void cleanup(Long userId, Long roleId, Long groupId, Long... permissionIds) {
        if (roleId != null) {
            quietly(() -> roleService.assignPermissions(roleId, List.of()));
            quietly(() -> roleService.assignGroups(roleId, List.of()));
            roleService.deleteById(roleId);
        }
        if (groupId != null) {
            quietly(() -> permissionGroupService.assignPermissions(groupId, List.of()));
            quietly(() -> permissionGroupService.deleteById(groupId));
        }
        for (Long permissionId : permissionIds == null ? new Long[0] : permissionIds) {
            if (permissionId != null) {
                permissionService.deleteById(permissionId);
            }
        }
        if (userId != null) {
            fixture.deleteUser(userId);
        }
    }

    private void quietly(Runnable action) {
        try {
            action.run();
        } catch (BizException ignored) {
            // 目标已不存在，清理无需重试
        }
    }
}
