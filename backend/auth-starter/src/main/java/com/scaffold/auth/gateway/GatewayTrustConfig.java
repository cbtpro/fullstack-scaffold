package com.scaffold.auth.gateway;

import com.scaffold.auth.AuthProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;

/**
 * 网关信任过滤器装配（仅 Servlet 业务服务）。
 *
 * <p>与 {@code JwtSecurityConfig} 同样使用 {@link ConditionalOnWebApplication} 隔离：
 * gateway-service 是 WebFlux，不会装配此过滤器。
 *
 * <p>开启条件 {@code auth.gateway.enforce-trust=true}；开启后若未配置
 * {@code auth.gateway.trust-secret} 则 fail-fast，避免「以为开了校验实则空放行」。
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "auth.gateway", name = "enforce-trust", havingValue = "true")
public class GatewayTrustConfig {

    @Bean
    public FilterRegistrationBean<GatewayTrustFilter> gatewayTrustFilterRegistration(AuthProperties props) {
        AuthProperties.Gateway gateway = props.getGateway();
        if (!StringUtils.hasText(gateway.getTrustSecret())) {
            throw new IllegalStateException(
                    "auth.gateway.enforce-trust=true 但 auth.gateway.trust-secret 未配置，拒绝启动。"
                            + "请通过环境变量 AUTH_GATEWAY_SECRET 设置与网关一致的共享密钥");
        }
        GatewayTrustFilter filter = new GatewayTrustFilter(gateway.getTrustHeader(), gateway.getTrustSecret());
        FilterRegistrationBean<GatewayTrustFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setName("gatewayTrustFilter");
        registration.addUrlPatterns("/*");
        // 早于 Spring Security 过滤链（order -100）执行，直连请求在鉴权前即被拒绝
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 50);
        return registration;
    }
}
