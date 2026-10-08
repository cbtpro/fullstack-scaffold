package com.scaffold.auth;

import com.scaffold.auth.model.LoginUser;

/**
 * 用户加载器。由业务方实现，提供「按用户名查用户 + 加密密码 + 权限」的能力，
 * 让 auth-starter 与具体业务存储解耦。实现 Bean 注册到容器后，
 * auth-starter 自动按 {@code auth.type} 装配对应 {@link AuthManager}。
 */
@FunctionalInterface
public interface AuthUserLoader {

    /**
     * 按用户名加载用户（含加密密码、权限）。
     * 不存在返回 {@code null}，由调用方处理为「用户不存在」。
     */
    LoginUser loadUserByUsername(String username);
}
