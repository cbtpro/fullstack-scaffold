package com.scaffold.auth;

import com.scaffold.auth.model.LoginRequest;
import com.scaffold.auth.model.LoginUser;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 鉴权抽象。由 {@code auth-starter} 根据 {@code auth.type} 自动装配实现：
 * <ul>
 *   <li>{@code JWT}：{@code JwtAuthManager}（Spring Security + jjwt）</li>
 *   <li>{@code SA_TOKEN}：{@code SaTokenAuthManager}（Sa-Token）</li>
 * </ul>
 * 业务侧仅依赖此接口，通过配置一行切换底层实现。
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
     * 从 HTTP 请求头解析 token，不存在返回 {@code null}。
     */
    String parseToken(HttpServletRequest req);
}
