package com.scaffold.auth.jwt;

import com.scaffold.auth.AuthProperties;
import com.scaffold.auth.AuthUserLoader;
import com.scaffold.auth.LoginTokenEnhancer;
import com.scaffold.auth.model.LoginRequest;
import com.scaffold.auth.model.LoginResult;
import com.scaffold.auth.model.LoginUser;
import com.scaffold.auth.model.UserInfo;
import com.scaffold.common.api.ResultCode;
import com.scaffold.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JWT 鉴权契约测试。
 *
 * <p>这里守的是「token 里放了什么、能还原成什么、什么情况下必须被拒」——
 * 网关与业务服务都靠这些 claims 判身份，一旦签发/还原口径不一致，
 * 表现为某些接口随机 401 或权限判断与库里不符，且很难在集成测试里定位。
 * 纯内存用例，不起 Spring 上下文。
 */
class JwtAuthManagerTest {

    private static final String SECRET = "unit-test-secret-at-least-32-bytes-long!!";
    /** 真实雪花主键量级，顺带覆盖 Long 在 claims 中的往返 */
    private static final long USER_ID = 104879054015991814L;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    private JwtAuthManager manager(AuthUserLoader loader, long expireMinutes) {
        AuthProperties props = new AuthProperties();
        props.getJwt().setSecret(SECRET);
        props.getJwt().setExpireMinutes(expireMinutes);
        return new JwtAuthManager(props, provider(loader), provider(null), encoder);
    }

    private LoginUser adminUser() {
        LoginUser user = new LoginUser();
        user.setId(USER_ID);
        user.setUsername("admin");
        user.setNickname("管理员");
        user.setEmail("admin@test.local");
        user.setStatus(1);
        user.setPassword(encoder.encode("123456"));
        user.setRoles(List.of("ADMIN"));
        user.setPermissions(List.of("*:*:*"));
        return user;
    }

    private LoginRequest request(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    @Test
    @DisplayName("登录成功：返回 token 与 expireAt，且响应体不含密码")
    void loginIssuesTokenWithoutPassword() {
        LoginResult result = manager(name -> adminUser(), 120).login(request("admin", "123456"));

        assertThat(result.getToken()).isNotBlank();
        assertThat(result.getExpireAt()).isGreaterThan(System.currentTimeMillis());

        UserInfo user = result.getUser();
        assertThat(user.getId()).isEqualTo(USER_ID);
        assertThat(user.getUsername()).isEqualTo("admin");
        assertThat(user.getRoles()).containsExactly("ADMIN");
        assertThat(user.getPermissions()).containsExactly("*:*:*");
        // UserInfo 不含 password 字段；这里额外确认没有别的字段冒充它
        assertThat(user.toString()).doesNotContain("123456");
    }

    @Test
    @DisplayName("token 可还原成用户信息：长整型主键、角色与权限原样保留")
    void tokenRoundTripRestoresClaims() {
        JwtAuthManager manager = manager(name -> adminUser(), 120);
        String token = manager.login(request("admin", "123456")).getToken();

        assertThat(manager.check(token)).isTrue();
        UserInfo restored = manager.getLoginUser(token);
        assertThat(restored.getId()).isEqualTo(USER_ID);
        assertThat(restored.getUsername()).isEqualTo("admin");
        assertThat(restored.getNickname()).isEqualTo("管理员");
        assertThat(restored.getEmail()).isEqualTo("admin@test.local");
        assertThat(restored.getRoles()).containsExactly("ADMIN");
        assertThat(restored.getPermissions()).containsExactly("*:*:*");
        assertThat(restored.getCreateTime()).isNotNull();
    }

    @Test
    @DisplayName("密码错误、缺密码与用户不存在均按未认证拒绝，不泄露账号是否存在")
    void loginRejectsBadCredentials() {
        // 未知用户名必须由 loader 返回 null，否则测不到「用户不存在」这条分支
        JwtAuthManager manager = manager(name -> "admin".equals(name) ? adminUser() : null, 120);

        assertThatThrownBy(() -> manager.login(request("admin", "wrong-password")))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(ResultCode.UNAUTHORIZED.getCode()));
        assertThatThrownBy(() -> manager.login(request("admin", null)))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> manager.login(request("ghost", "123456")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    @DisplayName("库里密码为空时同样按未认证拒绝，而不是放行")
    void loginRejectsUserWithoutStoredPassword() {
        JwtAuthManager manager = manager(name -> {
            LoginUser user = adminUser();
            user.setPassword(null);
            return user;
        }, 120);

        assertThatThrownBy(() -> manager.login(request("admin", "123456")))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("无 AuthUserLoader 时登录直接失败，而不是签发空身份 token")
    void loginWithoutLoaderFails() {
        assertThatThrownBy(() -> manager(null, 120).login(request("admin", "123456")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("AuthUserLoader");
    }

    @Test
    @DisplayName("篡改过的 token 与过期 token 一律判无效")
    void invalidAndExpiredTokensAreRejected() {
        JwtAuthManager manager = manager(name -> adminUser(), 120);
        String token = manager.login(request("admin", "123456")).getToken();

        // 改动签名段最后一个字符即破坏签名
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");
        assertThat(manager.check(tampered)).isFalse();
        assertThatThrownBy(() -> manager.getLoginUser(tampered)).isInstanceOf(BizException.class);

        // 换密钥签发的 token 不能被本实例接受（避免多环境共用密钥时互相认账）
        AuthProperties otherProps = new AuthProperties();
        otherProps.getJwt().setSecret("another-unit-test-secret-32-bytes-long!!!!");
        JwtAuthManager other = new JwtAuthManager(otherProps, provider(name -> adminUser()), provider(null), encoder);
        assertThat(manager.check(other.login(request("admin", "123456")).getToken())).isFalse();

        // 有效期设为负数，签发即过期
        assertThat(manager(name -> adminUser(), -1).check(
                manager(name -> adminUser(), -1).login(request("admin", "123456")).getToken())).isFalse();
    }

    @Test
    @DisplayName("parseToken 兼容带前缀与不带前缀的 Authorization 头")
    void parseTokenHandlesPrefix() {
        JwtAuthManager manager = manager(name -> adminUser(), 120);

        assertThat(manager.parseToken("Bearer abc.def.ghi")).isEqualTo("abc.def.ghi");
        assertThat(manager.parseToken("abc.def.ghi")).isEqualTo("abc.def.ghi");
        assertThat(manager.parseToken(null)).isNull();
        assertThat(manager.parseToken("")).isNull();
        assertThat(manager.getTokenHeaderName()).isEqualTo("Authorization");
    }

    @Test
    @DisplayName("logout 为无操作：JWT 无状态，token 在过期前仍有效")
    void logoutIsNoOp() {
        JwtAuthManager manager = manager(name -> adminUser(), 120);
        String token = manager.login(request("admin", "123456")).getToken();

        manager.logout(token);
        assertThat(manager.check(token)).isTrue();
    }

    @Test
    @DisplayName("设备指纹由扩展点在签发时回填并写入 claims")
    void enhancerFillsDeviceFingerprint() {
        AuthProperties props = new AuthProperties();
        props.getJwt().setSecret(SECRET);
        LoginTokenEnhancer enhancer = (user, request) -> user.setDeviceFingerprint("fp-abc");
        JwtAuthManager manager = new JwtAuthManager(props,
                provider(name -> adminUser()), provider(enhancer), encoder);

        String token = manager.login(request("admin", "123456")).getToken();
        assertThat(manager.getLoginUser(token).getDeviceFingerprint()).isEqualTo("fp-abc");
    }

    /** 只提供「按需取 Bean」语义的最小 ObjectProvider，避免为单元测试引入 Spring 容器 */
    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                if (value == null) {
                    throw new IllegalStateException("无 Bean 可提供");
                }
                return value;
            }

            @Override
            public T getObject() {
                return getObject(new Object[0]);
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }
        };
    }
}
