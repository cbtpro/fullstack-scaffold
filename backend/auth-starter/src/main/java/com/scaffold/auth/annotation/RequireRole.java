package com.scaffold.auth.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口角色校验。标注在 Controller 方法上，由 {@code PermissionAspect} 切面拦截，
 * 校验当前登录用户的 {@code roles} 是否包含指定角色编码。
 *
 * <p>使用示例：
 * <pre>{@code
 * @RequireRole("ADMIN")
 * @DeleteMapping("/{id}")
 * public Result<Void> delete(@PathVariable Long id) { ... }
 * }</pre>
 *
 * <p>JWT 模式下由 auth-starter 的 AOP 切面执行；
 * Sa-Token 模式下建议直接使用 {@code @SaCheckRole}。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /** 角色编码，如 {@code ADMIN} */
    String value();

    /** 多角色时的逻辑：AND 全部满足 / OR 任一满足。默认 OR（任一角色即可）。 */
    Logical logical() default Logical.OR;
}
