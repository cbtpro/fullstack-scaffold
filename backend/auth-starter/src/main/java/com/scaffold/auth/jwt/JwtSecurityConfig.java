package com.scaffold.auth.jwt;

import com.scaffold.common.api.ResultCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * JWT 模式下的 Servlet 安全链。
 *
 * <p><b>关键隔离</b>：{@link ConditionalOnWebApplication} 仅在 servlet 应用激活，
 * gateway-service 是 WebFlux，不会触发此配置，避免 {@code spring-mvc} 依赖被强制加载导致启动报错。
 * 同时 {@code @ConditionalOnProperty(auth.type=JWT)} 确保切换到 Sa-Token 时此链不装配，
 * 让 sa-token 的 {@code SaInterceptor} 独占鉴权。
 */
@Configuration
@EnableWebSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "auth", name = "type", havingValue = "JWT", matchIfMissing = true)
public class JwtSecurityConfig {

    /**
     * 放行路径：登录、OpenAPI 文档、静态资源。
     */
    private static final String[] PERMIT_PATHS = {
            "/api/auth/login",
            "/actuator/**",
            "/doc.html",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/webjars/**",
            "/favicon.ico"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthManager jwtAuthManager,
                                                   JwtAuthFilter jwtAuthFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PERMIT_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(eh -> eh.authenticationEntryPoint((request, response, ex) -> {
                    // Spring Security 6.x 默认对未认证请求返回 403，此处修正为 401，
                    // 与网关 AuthGlobalFilter 保持一致，便于前端统一处理鉴权失败
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(
                            "{\"code\":" + ResultCode.UNAUTHORIZED.getCode()
                                    + ",\"message\":\"未登录或token无效\"}");
                }))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtAuthManager jwtAuthManager) {
        return new JwtAuthFilter(jwtAuthManager);
    }
}
