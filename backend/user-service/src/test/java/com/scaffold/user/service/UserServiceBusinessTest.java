package com.scaffold.user.service;

import com.scaffold.common.api.PageQuery;
import com.scaffold.common.api.PageResult;
import com.scaffold.common.api.ResultCode;
import com.scaffold.common.exception.BizException;
import com.scaffold.user.dto.ProfileUpdateRequest;
import com.scaffold.user.entity.Role;
import com.scaffold.user.entity.User;
import com.scaffold.user.enums.EnableStatus;
import com.scaffold.user.mapper.UserMapper;
import com.scaffold.user.support.ItDatabase;
import com.scaffold.user.support.TestDataFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 用户账号与个人中心业务规则测试（DB 集成）。
 *
 * <p>覆盖的是「绕过界面也必须守住」的部分：密码存储形态、改密的前置校验、
 * 个人中心只改本人可改字段、逻辑删除后的账号不得再登录。这些规则一旦松动，
 * 界面看不出异常，但账号体系已经不成立。
 *
 * <p>用例自建所需数据（角色也自建，不读种子），跑完由 {@link TestDataFixture} 回收，
 * 因此既能在本地已建库上跑，也能在 CI 那种「只建表、无数据」的库上跑。
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIf("databaseReachable")
class UserServiceBusinessTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        ItDatabase.register(registry);
    }

    @SuppressWarnings("unused")
    static boolean databaseReachable() {
        return ItDatabase.isReachable();
    }

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TestDataFixture fixture;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanup() {
        fixture.cleanup();
    }

    /** 密码只在库里可见（对外接口一律清空该字段），故直接查库 */
    private String storedPassword(Long userId) {
        return userMapper.selectById(userId).getPassword();
    }

    private User newTrackedUser() {
        User created = userService.create(fixture.user(fixture.uniqueUsername(), "Secret#123"));
        fixture.trackUser(created.getId());
        return created;
    }

    @Test
    @DisplayName("创建用户：密码以 BCrypt 存储、对外响应不含明文，且用户名唯一")
    void createStoresBcryptPasswordOnly() {
        String username = fixture.uniqueUsername();
        User created = userService.create(fixture.user(username, "Secret#123"));
        fixture.trackUser(created.getId());

        assertThat(created.getId()).isNotNull();
        assertThat(created.getStatus()).isEqualTo(EnableStatus.ENABLED);
        assertThat(created.getCreateTime()).isNotNull();
        // 对外返回的对象已清空密码字段
        assertThat(created.getPassword()).isNull();

        String stored = storedPassword(created.getId());
        assertThat(stored).startsWith("$2");
        assertThat(stored).isNotEqualTo("Secret#123");
        assertThat(passwordEncoder.matches("Secret#123", stored)).isTrue();

        assertThatThrownBy(() -> userService.create(fixture.user(username, "Secret#123")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户名已存在");
    }

    @Test
    @DisplayName("创建用户：用户名为必填；密码留空时按约定补默认口令，而不是落空密码")
    void createRequiresUsernameOnly() {
        assertThatThrownBy(() -> userService.create(fixture.user(" ", "Secret#123")))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(ResultCode.PARAM_INVALID.getCode()));

        User created = userService.create(fixture.user(fixture.uniqueUsername(), null));
        fixture.trackUser(created.getId());
        // 空密码被替换为约定口令，用默认口令校验应通过
        assertThat(passwordEncoder.matches("123456", storedPassword(created.getId()))).isTrue();
    }

    @Test
    @DisplayName("更新用户：可改昵称/邮箱/手机与状态，停用后立即失去登录资格")
    void updateProfileFieldsAndStatus() {
        String username = fixture.uniqueUsername();
        User created = userService.create(fixture.user(username, "Secret#123"));
        Long id = created.getId();
        fixture.trackUser(id);

        User patch = new User();
        patch.setId(id);
        patch.setUsername(username);
        patch.setNickname("新昵称");
        patch.setEmail("new@it.local");
        patch.setPhone("13900000000");
        patch.setStatus(EnableStatus.DISABLED);
        User updated = userService.update(patch);

        assertThat(updated.getNickname()).isEqualTo("新昵称");
        assertThat(updated.getEmail()).isEqualTo("new@it.local");
        assertThat(updated.getPhone()).isEqualTo("13900000000");
        assertThat(updated.getStatus()).isEqualTo(EnableStatus.DISABLED);
        // 停用账号在加载登录用户时被拒（FORBIDDEN），不是静默返回
        assertThatThrownBy(() -> userService.loadUserByUsername(username))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(ResultCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("个人中心：只改昵称/邮箱/手机；缺省字段不落空，用户名与密码不受影响")
    void updateProfileOnlyTouchesSelfFields() {
        String username = fixture.uniqueUsername();
        User created = userService.create(fixture.user(username, "Secret#123"));
        Long id = created.getId();
        fixture.trackUser(id);
        String passwordBefore = storedPassword(id);

        User profile = userService.updateProfile(id,
                new ProfileUpdateRequest("本人昵称", "self@it.local", "13800000000"));
        assertThat(profile.getNickname()).isEqualTo("本人昵称");
        assertThat(profile.getEmail()).isEqualTo("self@it.local");
        assertThat(profile.getPhone()).isEqualTo("13800000000");
        assertThat(profile.getUsername()).isEqualTo(username);
        // 资料修改不触碰密码
        assertThat(storedPassword(id)).isEqualTo(passwordBefore);

        // 传 null 表示「本次不改该字段」，不应被清成空
        User kept = userService.updateProfile(id, new ProfileUpdateRequest(null, null, null));
        assertThat(kept.getNickname()).isEqualTo("本人昵称");
        assertThat(kept.getPhone()).isEqualTo("13800000000");
        assertThat(kept.getEmail()).isEqualTo("self@it.local");

        // 只改邮箱时，其它字段同样保持
        User onlyEmail = userService.updateProfile(id, new ProfileUpdateRequest(null, "only@it.local", null));
        assertThat(onlyEmail.getEmail()).isEqualTo("only@it.local");
        assertThat(onlyEmail.getNickname()).isEqualTo("本人昵称");
        assertThat(onlyEmail.getPhone()).isEqualTo("13800000000");
    }

    @Test
    @DisplayName("个人中心：超长字段拒绝，边界长度放行")
    void updateProfileValidatesLength() {
        User created = newTrackedUser();
        Long id = created.getId();

        assertThatThrownBy(() -> userService.updateProfile(id, new ProfileUpdateRequest("n".repeat(33), null, null)))
                .isInstanceOf(BizException.class).hasMessageContaining("昵称");
        assertThatThrownBy(() -> userService.updateProfile(id, new ProfileUpdateRequest(null, "a".repeat(65), null)))
                .isInstanceOf(BizException.class).hasMessageContaining("邮箱");
        assertThatThrownBy(() -> userService.updateProfile(id, new ProfileUpdateRequest(null, null, "1".repeat(21))))
                .isInstanceOf(BizException.class).hasMessageContaining("手机号");

        User edge = userService.updateProfile(id,
                new ProfileUpdateRequest("n".repeat(32), "a".repeat(64), "1".repeat(20)));
        assertThat(edge.getNickname()).hasSize(32);
        assertThat(edge.getEmail()).hasSize(64);
        assertThat(edge.getPhone()).hasSize(20);
    }

    @Test
    @DisplayName("改密：原密码错误、新密码为空/越界/与原密码相同均拒绝；成功后仅新密码有效")
    void changePasswordRules() {
        User created = newTrackedUser();
        Long id = created.getId();

        assertThatThrownBy(() -> userService.changePassword(id, "wrong-old", "NewSecret#456"))
                .isInstanceOf(BizException.class).hasMessageContaining("原密码错误");
        assertThatThrownBy(() -> userService.changePassword(id, "Secret#123", "  "))
                .isInstanceOf(BizException.class).hasMessageContaining("新密码不能为空");
        assertThatThrownBy(() -> userService.changePassword(id, "Secret#123", "abc"))
                .isInstanceOf(BizException.class).hasMessageContaining("长度");
        assertThatThrownBy(() -> userService.changePassword(id, "Secret#123", "Secret#123"))
                .isInstanceOf(BizException.class).hasMessageContaining("相同");

        userService.changePassword(id, "Secret#123", "NewSecret#456");
        String stored = storedPassword(id);
        assertThat(passwordEncoder.matches("NewSecret#456", stored)).isTrue();
        assertThat(passwordEncoder.matches("Secret#123", stored)).isFalse();
    }

    @Test
    @DisplayName("重置密码：长度校验同口径；管理员重置后本人原密码立即失效")
    void resetPasswordRules() {
        User created = newTrackedUser();
        Long id = created.getId();

        assertThatThrownBy(() -> userService.resetPassword(id, "abc"))
                .isInstanceOf(BizException.class).hasMessageContaining("长度");

        userService.resetPassword(id, "ResetSecret#789");
        String stored = storedPassword(id);
        assertThat(passwordEncoder.matches("ResetSecret#789", stored)).isTrue();
        assertThat(passwordEncoder.matches("Secret#123", stored)).isFalse();
    }

    @Test
    @DisplayName("逻辑删除：记录不再可查、不再能登录，列表里也查不到")
    void deleteIsLogical() {
        String username = fixture.uniqueUsername();
        User created = userService.create(fixture.user(username, "Secret#123"));
        Long id = created.getId();

        assertThat(userService.deleteById(id)).isTrue();
        assertThatThrownBy(() -> userService.getById(id)).isInstanceOf(BizException.class);
        assertThat(userService.loadUserByUsername(username)).isNull();

        PageQuery query = new PageQuery();
        query.setPage(1);
        query.setSize(10);
        assertThat(userService.page(query, username).getList()).isEmpty();
    }

    @Test
    @DisplayName("角色分配：先清后置，空列表即解绑")
    void assignRolesReplacesExisting() {
        Role roleA = fixture.createRole("it_role_a");
        Role roleB = fixture.createRole("it_role_b");
        User created = newTrackedUser();
        Long id = created.getId();

        userService.assignRoles(id, List.of(roleA.getId(), roleB.getId()));
        assertThat(userService.listRoleIds(id)).containsExactlyInAnyOrder(roleA.getId(), roleB.getId());

        userService.assignRoles(id, List.of(roleB.getId()));
        assertThat(userService.listRoleIds(id)).containsExactly(roleB.getId());

        userService.assignRoles(id, List.of());
        assertThat(userService.listRoleIds(id)).isEmpty();
    }

    @Test
    @DisplayName("加载登录用户：携带角色码与权限码，供鉴权与前端菜单使用")
    void loadUserByUsernameCarriesAuthorities() {
        Role role = fixture.createRole("it_role_load");
        String username = fixture.uniqueUsername();
        User created = userService.create(fixture.user(username, "Secret#123"));
        Long id = created.getId();
        fixture.trackUser(id);
        userService.assignRoles(id, List.of(role.getId()));

        var loginUser = userService.loadUserByUsername(username);
        assertThat(loginUser.getId()).isEqualTo(id);
        assertThat(loginUser.getUsername()).isEqualTo(username);
        assertThat(loginUser.getRoles()).contains(role.getCode());
        // 未分配权限的角色解析出空列表而非 null
        assertThat(loginUser.getPermissions()).isNotNull();
        // 登录链路需要密文做校验，故该入口返回的密码字段是密文而非清空
        assertThat(loginUser.getPassword()).startsWith("$2");
    }

    @Test
    @DisplayName("分页查询：关键字命中用户名，且列表接口不返回密码；删除后不再返回")
    void pageFiltersByKeyword() {
        String username = fixture.uniqueUsername();
        User created = userService.create(fixture.user(username, "Secret#123"));
        Long id = created.getId();

        PageQuery query = new PageQuery();
        query.setPage(1);
        query.setSize(10);
        PageResult<User> page = userService.page(query, username);

        assertThat(page.getList()).extracting(User::getUsername).containsExactly(username);
        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getList()).allSatisfy(u -> assertThat(u.getPassword()).isNull());

        userService.deleteById(id);
        assertThat(userService.page(query, username).getList()).isEmpty();

        fixture.trackUser(id);
    }
}
