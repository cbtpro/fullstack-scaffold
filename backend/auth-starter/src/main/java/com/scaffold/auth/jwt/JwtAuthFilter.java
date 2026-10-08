package com.scaffold.auth.jwt;

import com.scaffold.auth.AuthManager;
import com.scaffold.auth.model.LoginUser;
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
 * JWT 鉴权过滤器：从请求头解析 token → 加载 LoginUser → 写入 SecurityContext。
 * 仅在 servlet web 环境（{@code auth.type=JWT}）激活。
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final AuthManager authManager;

    public JwtAuthFilter(AuthManager authManager) {
        this.authManager = authManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(authManager.getTokenHeaderName());
        String token = authManager.parseToken(header);
        if (token != null && authManager.check(token)) {
            try {
                LoginUser user = authManager.getLoginUser(token);
                List<SimpleGrantedAuthority> authorities = user.getPermissions() == null
                        ? Collections.emptyList()
                        : user.getPermissions().stream().map(SimpleGrantedAuthority::new).toList();
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(user, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception ignored) {
                // token 无效则放行，由 SecurityFilterChain 决定 401
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
