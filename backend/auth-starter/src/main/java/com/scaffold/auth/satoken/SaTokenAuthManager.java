package com.scaffold.auth.satoken;

import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpUtil;
import com.scaffold.auth.AuthManager;
import com.scaffold.auth.AuthProperties;
import com.scaffold.auth.AuthUserLoader;
import com.scaffold.auth.LoginTokenEnhancer;
import com.scaffold.auth.model.LoginRequest;
import com.scaffold.auth.model.LoginResult;
import com.scaffold.auth.model.LoginUser;
import com.scaffold.auth.model.UserInfo;
import com.scaffold.common.api.ResultCode;
import com.scaffold.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Sa-Token 鉴权实现。
 *
 * <p>login 把 {@link LoginUser} 存入 token session，{@link #getLoginUser} 按token值取回，
 * {@link #logout} 注销会话。{@link #parseToken} 与 JWT 实现一致，从 {@code Authorization} 头取去前缀 token，
 * 兼容前端统一协议，gateway 的 reactive 过滤器也能直接复用。
 * {@link AuthUserLoader} 用 {@link ObjectProvider} 延迟获取，gateway 不需提供 loader。
 *
 * <p>响应统一走 {@link LoginResult} / {@link UserInfo}，与前端契约对齐。
 */
@Slf4j
public class SaTokenAuthManager implements AuthManager {

    private static final String SESSION_KEY = "loginUser";

    private final AuthProperties props;
    private final ObjectProvider<AuthUserLoader> loaderProvider;
    private final ObjectProvider<LoginTokenEnhancer> enhancerProvider;
    private final PasswordEncoder encoder;

    public SaTokenAuthManager(AuthProperties props,
                              ObjectProvider<AuthUserLoader> loaderProvider,
                              ObjectProvider<LoginTokenEnhancer> enhancerProvider,
                              PasswordEncoder encoder) {
        this.props = props;
        this.loaderProvider = loaderProvider;
        this.enhancerProvider = enhancerProvider;
        this.encoder = encoder;
    }

    @Override
    public LoginResult login(LoginRequest req) {
        AuthUserLoader loader = loaderProvider.getIfAvailable();
        if (loader == null) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "AuthUserLoader 未实现，无法登录");
        }
        LoginUser user = loader.loadUserByUsername(req.getUsername());
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "用户不存在");
        }
        if (user.getPassword() == null || !encoder.matches(req.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "密码错误");
        }
        // 签发前扩展点：与 JWT 实现同口径，设备登记/风控校验由实现方完成
        LoginTokenEnhancer enhancer = enhancerProvider.getIfAvailable();
        if (enhancer != null) {
            enhancer.enhance(user, req);
        }

        SaLoginModel loginModel = new SaLoginModel();
        loginModel.setTimeout(props.getSaToken().getTimeout());

        StpUtil.login(user.getId(), loginModel);
        StpUtil.getTokenSession().set(SESSION_KEY, user);

        user.setPassword(null);
        user.setToken(StpUtil.getTokenValue());
        user.setExpireAt(System.currentTimeMillis() + props.getSaToken().getTimeout() * 1000L);
        return LoginResult.of(user);
    }

    @Override
    public UserInfo getLoginUser(String token) {
        try {
            Object data = StpUtil.getTokenSessionByToken(token).get(SESSION_KEY);
            if (data instanceof LoginUser user) {
                user.setToken(token);
                return UserInfo.from(user);
            }
            throw new BizException(ResultCode.UNAUTHORIZED, "token 会话数据缺失");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.UNAUTHORIZED, "token 无效或已过期");
        }
    }

    @Override
    public boolean check(String token) {
        try {
            StpUtil.getTokenSessionByToken(token).get(SESSION_KEY);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void logout(String token) {
        StpUtil.logoutByTokenValue(token);
    }

    @Override
    public String getTokenHeaderName() {
        return props.getTokenHeader();
    }

    @Override
    public String parseToken(String authHeader) {
        if (authHeader == null || authHeader.isEmpty()) {
            return null;
        }
        String prefix = props.getTokenPrefix();
        if (prefix != null && !prefix.isEmpty() && authHeader.startsWith(prefix)) {
            return authHeader.substring(prefix.length()).trim();
        }
        return authHeader.trim();
    }
}
