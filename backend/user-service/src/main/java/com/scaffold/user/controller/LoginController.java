package com.scaffold.user.controller;

import com.scaffold.auth.AuthManager;
import com.scaffold.auth.model.LoginRequest;
import com.scaffold.auth.model.LoginResult;
import com.scaffold.captcha.CaptchaService;
import com.scaffold.common.api.Result;
import com.scaffold.common.api.ResultCode;
import com.scaffold.common.exception.BizException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录/登出端点。登录前先消费图形验证码（原子取出，输错同样作废），
 * 再调用 {@link AuthManager} 完成鉴权，与具体实现（JWT/Sa-Token）解耦。
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "鉴权", description = "登录/登出")
public class LoginController {

    private final AuthManager authManager;
    private final CaptchaService captchaService;

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "校验验证码与账号密码并颁发 token")
    public Result<LoginResult> login(@RequestBody LoginRequest request) {
        if (!captchaService.verify(request.getCaptchaId(), request.getCaptchaCode())) {
            throw new BizException(ResultCode.CAPTCHA_INVALID);
        }
        LoginResult result = authManager.login(request);
        return Result.success(result, "登录成功");
    }

    @PostMapping("/logout")
    @Operation(summary = "退出登录")
    public Result<Void> logout(HttpServletRequest request) {
        String header = request.getHeader(authManager.getTokenHeaderName());
        String token = authManager.parseToken(header);
        if (token != null) {
            authManager.logout(token);
        }
        return Result.success();
    }
}
