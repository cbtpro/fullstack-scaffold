package com.scaffold.auth.aspect;

import com.scaffold.auth.AuthManager;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 权限/角色校验切面的装配入口。
 *
 * <p>{@link PermissionAspect} 位于 starter 包（{@code com.scaffold.auth.aspect}），
 * 业务应用的组件扫描通常只覆盖自身包，{@code @Component} 不会被扫到，
 * 因此必须在这里显式声明为 Bean，否则 {@code @RequirePermission} 形同虚设。
 *
 * <p>仅 Servlet Web 应用且类路径存在 AspectJ 时装配：gateway 是 WebFlux，不触发。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(Aspect.class)
public class PermissionAspectConfiguration {

    @Bean
    public PermissionAspect permissionAspect(ObjectProvider<AuthManager> authManagerProvider) {
        return new PermissionAspect(authManagerProvider);
    }
}
