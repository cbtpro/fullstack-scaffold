package com.scaffold.gateway.filter;

import com.scaffold.auth.AuthManager;
import com.scaffold.auth.AuthProperties;
import com.scaffold.auth.model.UserInfo;
import com.scaffold.common.api.ResultCode;
import com.scaffold.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 网关全局鉴权过滤器（reactive）。
 *
 * <p>不依赖 servlet {@code SecurityFilterChain}，从请求头解析 token → 调
 * {@link AuthManager#check} / {@link AuthManager#getLoginUser} 校验，
 * 成功后写 {@code X-User-Id} 透传下游，失败直接返 401 JSON。
 *
 * <p>放行：登录、OpenAPI 文档。{@code AuthManager} 由 auth-starter 按 {@code auth.type} 装配，
 * gateway（WebFlux）下 {@code JwtSecurityConfig} 因 {@code @ConditionalOnWebApplication(SERVLET)} 不触发。
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    /** 透传下游的用户标识头 */
    public static final String USER_ID_HEADER = "X-User-Id";
    /** 透传下游的用户名头 */
    public static final String USERNAME_HEADER = "X-Username";

    private static final String[] PERMIT_PATHS = {
            "/api/auth/login",
            "/api/auth/logout",
            "/doc.html",
            "/swagger-ui",
            "/v3/api-docs",
            "/webjars",
            "/favicon.ico"
    };

    private final AuthManager authManager;
    private final AuthProperties authProperties;

    public AuthGlobalFilter(AuthManager authManager, AuthProperties authProperties) {
        this.authManager = authManager;
        this.authProperties = authProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (isPermitted(path)) {
            return chain.filter(exchange);
        }

        String header = exchange.getRequest().getHeaders().getFirst(authProperties.getTokenHeader());
        String token = extractToken(header);
        if (token == null || token.isEmpty()) {
            return unauthorized(exchange, "缺少 token");
        }
        if (!authManager.check(token)) {
            return unauthorized(exchange, "token 无效或已过期");
        }

        try {
            UserInfo user = authManager.getLoginUser(token);
            ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .header(USER_ID_HEADER, String.valueOf(user.getId()))
                    .header(USERNAME_HEADER, user.getUsername() == null ? "" : user.getUsername())
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (BizException e) {
            return unauthorized(exchange, e.getMessage());
        }
    }

    private boolean isPermitted(String path) {
        for (String prefix : PERMIT_PATHS) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String extractToken(String header) {
        if (header == null || header.isEmpty()) {
            return null;
        }
        String prefix = authProperties.getTokenPrefix();
        if (prefix != null && !prefix.isEmpty() && header.startsWith(prefix)) {
            return header.substring(prefix.length()).trim();
        }
        return header.trim();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":" + ResultCode.UNAUTHORIZED.getCode()
                + ",\"message\":\"" + message + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // 在路由转发之前鉴权
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
