package com.scaffold.auth.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.scaffold.common.api.ResultCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 下游服务的网关信任过滤器（Servlet）。
 *
 * <p>强制所有外部请求经网关进入：网关转发时注入共享密钥头，直连业务服务的请求
 * 因携带不出正确密钥被拒绝，从而无法伪造 {@code X-User-Id} 等网关注入的内部头。
 *
 * <p>放行：容器健康检查与 OpenAPI 文档资源；ERROR 分发由 {@link OncePerRequestFilter} 默认跳过。
 */
public class GatewayTrustFilter extends OncePerRequestFilter {

    /** 不校验网关密钥的路径前缀：actuator 健康检查、文档资源（本身已公开） */
    private static final List<String> PERMIT_PREFIXES = List.of(
            "/actuator",
            "/doc.html",
            "/swagger-ui",
            "/v3/api-docs",
            "/webjars",
            "/favicon.ico"
    );

    private final String trustHeader;
    private final String trustSecret;

    public GatewayTrustFilter(String trustHeader, String trustSecret) {
        this.trustHeader = trustHeader;
        this.trustSecret = trustSecret;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String prefix : PERMIT_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String presented = request.getHeader(trustHeader);
        if (!StringUtils.hasText(presented) || !constantTimeEquals(presented, trustSecret)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"code\":" + ResultCode.FORBIDDEN.getCode()
                    + ",\"message\":\"拒绝直连业务服务，请通过网关访问\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 常量时间比较，避免密钥比较的计时侧信道。
     */
    private boolean constantTimeEquals(String a, String b) {
        if (!StringUtils.hasText(a) || !StringUtils.hasText(b)) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
