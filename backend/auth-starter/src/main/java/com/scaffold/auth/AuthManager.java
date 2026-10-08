package com.scaffold.auth;

import com.scaffold.auth.model.LoginRequest;
import com.scaffold.auth.model.LoginUser;

/**
 * 鉴权抽象。由 {@code auth-starter} 根据 {@code auth.type} 自动装配实现：
 * <ul>
 *   <li>{@code JWT}：{@code JwtAuthManager}（Spring Security + jjwt）</li>
 *   <li>{@code SA_TOKEN}：{@code SaTokenAuthManager}（Sa-Token）</li>
 * </ul>
 * 业务侧仅依赖此接口，通过配置一行切换底层实现。
 *
 * <p>接口不绑定 servlet/reactive 类型，调用方自行从请求头取值后传入
 * {@link #parseToken(String)}，gateway（WebFlux）与 user-service（Servlet）均可复用。
 */
public interface AuthManager {

    /**
     * 登录。失败抛 {@link com.scaffold.common.exception.BizException}。
     */
    LoginUser login(LoginRequest req);

    /**
     * 根据 token 还原登录用户。token 失效抛异常。
     */
    LoginUser getLoginUser(String token);

    /**
     * token 是否有效。
     */
    boolean check(String token);

    /**
     * 注销。JWT 为无状态 no-op；Sa-Token 注销会话。
     */
    void logout(String token);

    /**
     * 配置的 token 请求头名称（默认 {@code Authorization}），调用方据此取头值。
     */
    String getTokenHeaderName();

    /**
     * 从请求头原始值解析 token：去前缀、trim，不存在或为空返回 {@code null}。
     */
    String parseToken(String authHeader);
}
