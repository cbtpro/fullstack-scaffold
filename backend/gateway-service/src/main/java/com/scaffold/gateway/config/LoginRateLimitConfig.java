package com.scaffold.gateway.config;

import com.scaffold.common.web.ClientIpResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

/**
 * 登录限流的限流键解析。
 *
 * <p>以客户端 IP 为令牌桶维度。地址取自 {@link ClientIpResolver}（默认只信任最后一跳），
 * 因此客户端自行伪造 {@code X-Forwarded-For} 无法把限流键改写成别人或随机值来绕过限流。
 */
@Configuration
public class LoginRateLimitConfig {

    private final int trustedProxyCount;

    public LoginRateLimitConfig(
            @Value("${scaffold.client-ip.trusted-proxy-count:1}") int trustedProxyCount) {
        this.trustedProxyCount = trustedProxyCount;
    }

    @Bean
    public KeyResolver loginRateLimitKeyResolver() {
        return exchange -> Mono.just(clientIp(exchange.getRequest()));
    }

    private String clientIp(ServerHttpRequest request) {
        return ClientIpResolver.resolve(
                request.getHeaders().getFirst("X-Forwarded-For"),
                request.getRemoteAddress() == null
                        ? null
                        : request.getRemoteAddress().getAddress().getHostAddress(),
                trustedProxyCount);
    }
}
