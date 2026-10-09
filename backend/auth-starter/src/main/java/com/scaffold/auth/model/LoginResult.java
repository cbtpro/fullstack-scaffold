package com.scaffold.auth.model;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录响应 DTO。与前端 {@code @fs/types LoginResult} 契约对齐：
 * <pre>{@code
 * { token: string, user: User, expireAt?: number }
 * }</pre>
 *
 * <p>由 {@link LoginUser} 转换而来，剥离 {@code password}，将用户字段收拢到 {@link UserInfo}，
 * 暴露 token 与 expireAt 给客户端管理本地过期。
 */
@Data
public class LoginResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 会话 token，前端写入 Authorization 头：{@code Bearer <token>} */
    private String token;

    /** 当前登录用户信息，前端写入本地 store 并据此渲染路由与按钮 */
    private UserInfo user;

    /** 过期时间戳（毫秒），可选，前端可据此做提前刷新策略 */
    private Long expireAt;

    public static LoginResult of(LoginUser source) {
        if (source == null) {
            return null;
        }
        LoginResult result = new LoginResult();
        result.token = source.getToken();
        result.user = UserInfo.from(source);
        result.expireAt = source.getExpireAt();
        return result;
    }
}
