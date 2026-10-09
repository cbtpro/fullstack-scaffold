package com.scaffold.auth.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口权限校验。标注在 Controller 方法上，由 {@code PermissionAspect} 切面拦截，
 * 校验当前登录用户（来自 token 解析后的 {@link com.scaffold.auth.model.UserInfo}）
 * 的 {@code permissions} 是否包含指定编码。
 *
 * <p>使用示例：
 * <pre>{@code
 * @RequirePermission("user:create")
 * @PostMapping
 * public Result<User> create(@RequestBody User user) { ... }
 * }</pre>
 *
 * <p>JWT 模式下由 auth-starter 的 AOP 切面执行；
 * Sa-Token 模式下建议直接使用 {@code @SaCheckPermission}，由 sa-token 拦截器校验。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** 权限编码，如 {@code user:create}；通配符 {@code *:*:*} 视为超级权限，匹配任意值 */
    String value();

    /** 多权限时的逻辑：AND 全部满足 / OR 任一满足。默认 AND。 */
    Logical logical() default Logical.AND;
}
