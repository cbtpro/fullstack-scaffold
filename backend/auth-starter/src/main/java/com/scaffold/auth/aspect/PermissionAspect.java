package com.scaffold.auth.aspect;

import com.scaffold.auth.annotation.Logical;
import com.scaffold.auth.annotation.RequirePermission;
import com.scaffold.auth.annotation.RequireRole;
import com.scaffold.auth.model.UserInfo;
import com.scaffold.common.api.ResultCode;
import com.scaffold.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * 权限/角色校验切面。仅在 servlet web 应用激活（user-service），gateway 是 WebFlux 不触发。
 *
 * <p>本 Bean 由 {@link PermissionAspectConfiguration} 显式装配（starter 包不在业务应用的
 * 组件扫描范围内，不能依赖 {@code @Component}）。
 *
 * <p>策略：
 * <ol>
 *   <li>优先从 {@link SecurityContextHolder} 取 {@link UserInfo}（JWT 模式下 JwtAuthFilter 已写入）</li>
 *   <li>取不到时退化为从当前请求头解析 token → {@code authManager.getLoginUser(token)} 还原</li>
 * </ol>
 * 这样既复用 JWT 鉴权链路写入的 SecurityContext，也兼容未走 JwtAuthFilter 的场景（如 Sa-Token 模式下
 * 切面会从请求头取 token 走 AuthManager 还原 UserInfo）。
 *
 * <p>超级权限通配符：当用户的 {@code permissions} 含 {@code *:*:*} 时直接放行。
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class PermissionAspect {

    /** 超级权限通配符，匹配任意 {@code @RequirePermission} 值 */
    private static final String SUPER_PERMISSION = "*:*:*";

    private final ObjectProvider<com.scaffold.auth.AuthManager> authManagerProvider;

    @Around("@annotation(requirePermission)")
    public Object aroundPermission(ProceedingJoinPoint pjp, RequirePermission requirePermission) throws Throwable {
        UserInfo user = currentUser();
        List<String> perms = user == null ? null : user.getPermissions();
        if (perms == null || perms.isEmpty()) {
            throw new BizException(ResultCode.FORBIDDEN, "无权限访问：" + requirePermission.value());
        }
        if (perms.contains(SUPER_PERMISSION)) {
            return pjp.proceed();
        }
        boolean ok = Logical.AND.equals(requirePermission.logical())
                ? perms.contains(requirePermission.value())
                : perms.contains(requirePermission.value());
        if (!ok) {
            throw new BizException(ResultCode.FORBIDDEN, "无权限访问：" + requirePermission.value());
        }
        return pjp.proceed();
    }

    @Around("@annotation(requireRole)")
    public Object aroundRole(ProceedingJoinPoint pjp, RequireRole requireRole) throws Throwable {
        UserInfo user = currentUser();
        List<String> roles = user == null ? null : user.getRoles();
        if (roles == null || roles.isEmpty()) {
            throw new BizException(ResultCode.FORBIDDEN, "无角色权限：" + requireRole.value());
        }
        boolean ok = roles.contains(requireRole.value());
        if (!ok) {
            throw new BizException(ResultCode.FORBIDDEN, "无角色权限：" + requireRole.value());
        }
        return pjp.proceed();
    }

    private UserInfo currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && !(auth instanceof AnonymousAuthenticationToken)
                && auth.getPrincipal() instanceof UserInfo info) {
            return info;
        }
        // 退化路径：从请求头解析 token，调 AuthManager 还原 UserInfo
        com.scaffold.auth.AuthManager authManager = authManagerProvider.getIfAvailable();
        if (authManager == null) {
            return null;
        }
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        String header = request.getHeader(authManager.getTokenHeaderName());
        String token = authManager.parseToken(header);
        if (token == null) {
            return null;
        }
        try {
            return authManager.getLoginUser(token);
        } catch (Exception e) {
            return null;
        }
    }
}
