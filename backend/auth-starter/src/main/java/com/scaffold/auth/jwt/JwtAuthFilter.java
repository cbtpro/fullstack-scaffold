package com.scaffold.auth.jwt;

import com.scaffold.auth.AuthManager;
import com.scaffold.auth.AuthProperties;
import com.scaffold.auth.device.DeviceBindingMatcher;
import com.scaffold.auth.model.UserInfo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * JWT 鉴权过滤器：从请求头解析 token → 加载 {@link UserInfo} → 写入 SecurityContext。
 * 仅在 servlet web 环境（{@code auth.type=JWT}）激活。
 *
 * <p>token 绑定设备指纹后，此处比对请求头指纹与 token 内指纹，
 * 不一致不写认证信息（由 SecurityFilterChain 统一返 401），
 * 防止绕过网关直连业务服务时 token 被异地重放。
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final AuthManager authManager;
    private final AuthProperties props;

    public JwtAuthFilter(AuthManager authManager, AuthProperties props) {
        this.authManager = authManager;
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(authManager.getTokenHeaderName());
        String token = authManager.parseToken(header);
        if (token != null && authManager.check(token)) {
            try {
                UserInfo user = authManager.getLoginUser(token);
                String requestFingerprint = request.getHeader(props.getDevice().getFingerprintHeader());
                if (DeviceBindingMatcher.matches(props, user, requestFingerprint)) {
                    List<SimpleGrantedAuthority> authorities = user == null || user.getPermissions() == null
                            ? Collections.emptyList()
                            : user.getPermissions().stream().map(SimpleGrantedAuthority::new).toList();
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(user, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    SecurityContextHolder.clearContext();
                }
            } catch (Exception ignored) {
                // token 无效则放行，由 SecurityFilterChain 决定 401
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
