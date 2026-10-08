package com.scaffold.auth;

import com.scaffold.auth.jwt.JwtAuthManager;
import com.scaffold.auth.jwt.JwtSecurityConfig;
import com.scaffold.auth.satoken.SaTokenAuthManager;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 鉴权自动装配入口。通过 {@code auth.type} 一行切换 JWT / Sa-Token。
 *
 * <p>装配规则：
 * <ul>
 *   <li>{@code auth.type=JWT}（默认）：装配 {@link JwtAuthManager}，并通过 {@link Import} 引入
 *       {@link JwtSecurityConfig}（受其自身 {@code @ConditionalOnWebApplication(SERVLET)} 隔离，gateway 不触发）</li>
 *   <li>{@code auth.type=SA_TOKEN}：装配 {@link SaTokenAuthManager}，业务侧 Application 需排除
 *       {@code SecurityAutoConfiguration} 以避免与 sa-token 拦截器冲突</li>
 * </ul>
 * 两种模式均要求业务方提供 {@link AuthUserLoader} Bean。
 */
@AutoConfiguration
@EnableConfigurationProperties(AuthProperties.class)
@Import(JwtSecurityConfig.class)
public class AuthAutoConfiguration {

    /**
     * BCrypt 密码编码器，业务方未自定义时提供默认实现。
     */
    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    @ConditionalOnClass(BCryptPasswordEncoder.class)
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * JWT 鉴权实现。
     */
    @Bean
    @ConditionalOnProperty(prefix = "auth", name = "type", havingValue = "JWT", matchIfMissing = true)
    @ConditionalOnClass(BCryptPasswordEncoder.class)
    @ConditionalOnBean(AuthUserLoader.class)
    public AuthManager jwtAuthManager(AuthProperties props,
                                      AuthUserLoader loader,
                                      PasswordEncoder encoder) {
        return new JwtAuthManager(props, loader, encoder);
    }

    /**
     * Sa-Token 鉴权实现。
     */
    @Bean
    @ConditionalOnProperty(prefix = "auth", name = "type", havingValue = "SA_TOKEN")
    @ConditionalOnClass(StpUtil.class)
    @ConditionalOnBean(AuthUserLoader.class)
    public AuthManager saTokenAuthManager(AuthProperties props,
                                          AuthUserLoader loader,
                                          PasswordEncoder encoder) {
        return new SaTokenAuthManager(props, loader, encoder);
    }
}
