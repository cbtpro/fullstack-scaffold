package com.scaffold.user.controller;

import com.scaffold.auth.AuthManager;
import com.scaffold.auth.model.LoginRequest;
import com.scaffold.auth.model.LoginResult;
import com.scaffold.auth.model.UserInfo;
import com.scaffold.captcha.CaptchaService;
import com.scaffold.common.api.Result;
import com.scaffold.common.api.ResultCode;
import com.scaffold.common.exception.BizException;
import com.scaffold.common.web.ClientIpResolver;
import com.scaffold.user.dto.ChangePasswordRequest;
import com.scaffold.user.log.LoginLogRecorder;
import com.scaffold.user.security.LoginLockoutService;
import com.scaffold.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录/登出/改密端点。登录前先消费图形验证码（原子取出，输错同样作废），
 * 再调用 {@link AuthManager} 完成鉴权，与具体实现（JWT/Sa-Token）解耦。
 *
 * <p>登录失败锁定在此层织入：{@link AuthManager} 的具体实现只负责校验与签发，
 * 计数与锁定与鉴权实现无关，放在入口处才能同时覆盖 JWT 与 Sa-Token 两条链路。
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@Tag(name = "鉴权", description = "登录/登出/修改密码")
public class LoginController {

    private final AuthManager authManager;
    private final CaptchaService captchaService;
    private final UserService userService;
    private final LoginLockoutService loginLockoutService;
    private final LoginLogRecorder loginLogRecorder;
    private final int trustedProxyCount;

    public LoginController(AuthManager authManager,
                           CaptchaService captchaService,
                           UserService userService,
                           LoginLockoutService loginLockoutService,
                           LoginLogRecorder loginLogRecorder,
                           @Value("${scaffold.client-ip.trusted-proxy-count:1}") int trustedProxyCount) {
        this.authManager = authManager;
        this.captchaService = captchaService;
        this.userService = userService;
        this.loginLockoutService = loginLockoutService;
        this.loginLogRecorder = loginLogRecorder;
        this.trustedProxyCount = trustedProxyCount;
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "校验验证码与账号密码并颁发 token")
    public Result<LoginResult> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String username = request.getUsername();
        if (!captchaService.verify(request.getCaptchaId(), request.getCaptchaCode())) {
            loginLogRecorder.record(username, LoginLogRecorder.EVENT_LOGIN, false, "验证码错误", httpRequest);
            throw new BizException(ResultCode.CAPTCHA_INVALID);
        }
        try {
            loginLockoutService.assertNotLocked(username);
        } catch (BizException e) {
            // 锁定期内的尝试同样入账，越是被拒绝的登录越需要审计
            loginLogRecorder.record(username, LoginLogRecorder.EVENT_LOGIN, false, e.getMessage(), httpRequest);
            throw e;
        }
        try {
            LoginResult result = authManager.login(request);
            loginLockoutService.reset(username);
            loginLogRecorder.record(username, LoginLogRecorder.EVENT_LOGIN, true, "登录成功", httpRequest);
            return Result.success(result, "登录成功");
        } catch (BizException e) {
            // 仅统计凭据校验失败：验证码错误在上一步已拦截，不计入账号失败次数
            loginLockoutService.recordFailure(username, clientIp(httpRequest));
            loginLogRecorder.record(username, LoginLogRecorder.EVENT_LOGIN, false, e.getMessage(), httpRequest);
            throw e;
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "退出登录")
    public Result<Void> logout(HttpServletRequest request) {
        String header = request.getHeader(authManager.getTokenHeaderName());
        String token = authManager.parseToken(header);
        String username = null;
        if (token != null) {
            username = resolveUsernameQuietly(token);
            authManager.logout(token);
        }
        if (username != null) {
            loginLogRecorder.record(username, LoginLogRecorder.EVENT_LOGOUT, true, "退出登录", request);
        }
        return Result.success();
    }

    @PostMapping("/password")
    @Operation(summary = "本人修改密码", description = "校验原密码后更新；用户身份从 token 解析，请求体不接受 userId")
    public Result<Void> changePassword(HttpServletRequest request,
                                       @RequestBody ChangePasswordRequest body) {
        UserInfo user = currentUser(request);
        userService.changePassword(user.getId(), body.oldPassword(), body.newPassword());
        return Result.success();
    }

    /**
     * 从请求头 token 解析当前登录用户，未认证直接抛业务异常。
     */
    private UserInfo currentUser(HttpServletRequest request) {
        String header = request.getHeader(authManager.getTokenHeaderName());
        String token = authManager.parseToken(header);
        if (token == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return authManager.getLoginUser(token);
    }

    /**
     * 静默解析 token 中的用户名。登出必须始终成功，
     * 因此 token 过期/非法时只放弃记录用户名，不把异常抛给调用方。
     */
    private String resolveUsernameQuietly(String token) {
        try {
            UserInfo user = authManager.getLoginUser(token);
            return user == null ? null : user.getUsername();
        } catch (BizException e) {
            return null;
        }
    }

    private String clientIp(HttpServletRequest request) {
        return ClientIpResolver.resolve(request.getHeader("X-Forwarded-For"),
                request.getRemoteAddr(), trustedProxyCount);
    }
}
