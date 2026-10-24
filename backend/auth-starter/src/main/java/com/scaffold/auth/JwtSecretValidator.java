package com.scaffold.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * JWT 密钥启动校验器（fail-fast）。
 *
 * <p>装配在 {@code auth-starter}，gateway（WebFlux）与业务服务（Servlet）启动时均生效。
 * 规则：
 * <ul>
 *   <li>{@code auth.type != JWT}：不校验（Sa-Token 不使用该密钥）</li>
 *   <li>密钥为空：任何环境都拒绝启动</li>
 *   <li>密钥为内置默认值或不足 32 字节：非开发环境拒绝启动；
 *       开发环境（无 profile / dev / test / local）仅打印警告</li>
 *   <li>{@code auth.jwt.allow-default-secret=true}：显式逃生舱，放行但在非开发环境打印风险警告</li>
 * </ul>
 *
 * <p>「未声明任何 profile」按本地开发处理（Spring 默认 profile {@code default}），
 * 生产部署通常显式设置 {@code SPRING_PROFILES_ACTIVE=prod}，此时默认密钥无法静默生效。
 */
@Slf4j
public class JwtSecretValidator implements InitializingBean {

    /** 命中任一 profile 即视为开发环境，默认/弱密钥只告警不阻断 */
    private static final Set<String> DEV_PROFILES = Set.of("default", "dev", "test", "local");

    /** HS256 要求密钥至少 32 字节（256 bit） */
    private static final int MIN_SECRET_BYTES = 32;

    private final AuthProperties props;
    private final Environment environment;

    public JwtSecretValidator(AuthProperties props, Environment environment) {
        this.props = props;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        if (props.getType() != AuthProperties.AuthType.JWT) {
            return;
        }
        String secret = props.getJwt().getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("auth.jwt.secret 未配置，拒绝启动。请通过环境变量 AUTH_JWT_SECRET 指定至少 32 字节的随机密钥");
        }
        if (props.getJwt().isAllowDefaultSecret()) {
            if (!isDevProfile()) {
                log.warn("auth.jwt.allow-default-secret=true 在非开发环境开启，JWT 可被离线伪造，仅限内部演示使用");
            }
            return;
        }

        boolean isDefault = AuthProperties.DEFAULT_JWT_SECRET.equals(secret);
        boolean tooShort = secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES;
        if (!isDefault && !tooShort) {
            return;
        }

        String reason = isDefault ? "当前使用内置默认密钥" : "密钥长度不足 32 字节";
        if (isDevProfile()) {
            log.warn("JWT 安全告警：{}，仅允许开发环境使用；生产环境请通过 AUTH_JWT_SECRET 覆盖", reason);
            return;
        }
        throw new IllegalStateException(reason + "，拒绝启动。请通过环境变量 AUTH_JWT_SECRET 配置足够强度的密钥"
                + "（或在确认为内部演示环境时设置 auth.jwt.allow-default-secret=true）");
    }

    private boolean isDevProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            // 未显式声明 profile 时为 Spring 默认 profile，按本地开发处理
            return true;
        }
        for (String profile : activeProfiles) {
            if (DEV_PROFILES.contains(profile)) {
                return true;
            }
        }
        return false;
    }
}
