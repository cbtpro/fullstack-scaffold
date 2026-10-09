package com.scaffold.auth;

import com.scaffold.auth.model.LoginRequest;
import com.scaffold.auth.model.LoginResult;
import com.scaffold.auth.model.UserInfo;

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
 *
 * <p>响应契约统一为 DTO：
 * <ul>
 *   <li>{@link #login} 返回 {@link LoginResult}，对齐前端 {@code { token, user, expireAt }} 嵌套结构</li>
 *   <li>{@link #getLoginUser} 返回 {@link UserInfo}，对齐前端 {@code User} 类型</li>
 * </ul>
 * 鉴权内部流转仍使用 {@code LoginUser}（含 password、token、expireAt），不对外暴露。
 */
public interface AuthManager {

    /**
     * 登录。失败抛 {@link com.scaffold.common.exception.BizException}。
     */
    LoginResult login(LoginRequest req);

    /**
     * 根据 token 还原登录用户信息。token 失效抛异常。
     */
    UserInfo getLoginUser(String token);

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
