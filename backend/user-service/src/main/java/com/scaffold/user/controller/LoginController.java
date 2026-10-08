package com.scaffold.user.controller;

import com.scaffold.auth.AuthManager;
import com.scaffold.auth.model.LoginRequest;
import com.scaffold.auth.model.LoginUser;
import com.scaffold.common.api.Result;
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
 * 登录/登出端点。调用 {@link AuthManager} 完成鉴权，与具体实现（JWT/Sa-Token）解耦。
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "鉴权", description = "登录/登出")
public class LoginController {

    private final AuthManager authManager;

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "校验账号密码并颁发 token")
    public Result<LoginUser> login(@RequestBody LoginRequest request) {
        LoginUser user = authManager.login(request);
        return Result.success(user, "登录成功");
    }

    @PostMapping("/logout")
    @Operation(summary = "退出登录")
    public Result<Void> logout(HttpServletRequest request) {
        String token = authManager.parseToken(request);
        if (token != null) {
            authManager.logout(token);
        }
        return Result.success();
    }
}
