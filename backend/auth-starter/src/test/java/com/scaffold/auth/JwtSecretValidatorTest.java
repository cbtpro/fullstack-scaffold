package com.scaffold.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JWT 密钥启动校验测试（P0-4 fail-fast 的护栏）。
 *
 * <p>规则本身很朴素，但一旦失效就是「生产带着仓库里公开的密钥对外服务」，
 * 而且没有任何运行期症状，所以按 profile 矩阵逐格固化。
 * 用例与 {@code RequiredPropertiesEnvironmentPostProcessor} 一起构成
 * 「非开发环境必须显式提供凭据」的两道闸门。
 */
class JwtSecretValidatorTest {

    private static final String STRONG_SECRET = "a-production-grade-secret-of-32-byte-minimum";
    private static final String SHORT_SECRET = "too-short";

    private JwtSecretValidator validator(String secret, String... activeProfiles) {
        AuthProperties props = new AuthProperties();
        props.getJwt().setSecret(secret);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfiles);
        return new JwtSecretValidator(props, environment);
    }

    @Test
    @DisplayName("开发档（无 profile / dev / test / local）使用默认密钥只告警，不阻断启动")
    void defaultSecretAllowedInDevProfiles() {
        assertThatCode(() -> validator(AuthProperties.DEFAULT_JWT_SECRET).afterPropertiesSet())
                .doesNotThrowAnyException();
        assertThatCode(() -> validator(AuthProperties.DEFAULT_JWT_SECRET, "dev").afterPropertiesSet())
                .doesNotThrowAnyException();
        assertThatCode(() -> validator(AuthProperties.DEFAULT_JWT_SECRET, "test").afterPropertiesSet())
                .doesNotThrowAnyException();
        assertThatCode(() -> validator(SHORT_SECRET, "local").afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("非开发档使用默认密钥或弱密钥拒绝启动，并说明原因与修复方式")
    void weakSecretRejectedOutsideDevProfiles() {
        assertThatThrownBy(() -> validator(AuthProperties.DEFAULT_JWT_SECRET, "prod").afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("内置默认密钥")
                .hasMessageContaining("AUTH_JWT_SECRET");

        assertThatThrownBy(() -> validator(SHORT_SECRET, "prod").afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不足 32 字节");
    }

    @Test
    @DisplayName("只声明了非开发档时才拒绝；同时声明 dev 视作开发环境")
    void anyDevProfileKeepsValidationLenient() {
        // Spring 允许同时激活多个 profile，只要含开发档就按开发对待
        assertThatCode(() -> validator(AuthProperties.DEFAULT_JWT_SECRET, "prod", "dev").afterPropertiesSet())
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator(AuthProperties.DEFAULT_JWT_SECRET, "staging").afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("强密钥在任何环境都放行")
    void strongSecretAlwaysAccepted() {
        assertThatCode(() -> validator(STRONG_SECRET, "prod").afterPropertiesSet())
                .doesNotThrowAnyException();
        assertThatCode(() -> validator(STRONG_SECRET).afterPropertiesSet()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("密钥为空在任何环境都拒绝启动")
    void blankSecretAlwaysRejected() {
        assertThatThrownBy(() -> validator(null, "prod").afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未配置");
        assertThatThrownBy(() -> validator("  ", "dev").afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("显式逃生舱 allow-default-secret=true 放行，但仍属风险操作")
    void escapeHatchExplicitlyAllowed() {
        AuthProperties props = new AuthProperties();
        props.getJwt().setSecret(AuthProperties.DEFAULT_JWT_SECRET);
        props.getJwt().setAllowDefaultSecret(true);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatCode(() -> new JwtSecretValidator(props, environment).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Sa-Token 模式不校验 JWT 密钥")
    void saTokenModeSkipsValidation() {
        AuthProperties props = new AuthProperties();
        props.setType(AuthProperties.AuthType.SA_TOKEN);
        props.getJwt().setSecret(null);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatCode(() -> new JwtSecretValidator(props, environment).afterPropertiesSet())
                .doesNotThrowAnyException();
    }
}
