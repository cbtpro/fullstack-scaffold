package com.scaffold.user.controller;

import com.scaffold.captcha.CaptchaChallenge;
import com.scaffold.captcha.CaptchaService;
import com.scaffold.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 图形验证码端点。校验逻辑与缓存由 captcha-starter 提供，此处仅做协议适配。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "验证码", description = "登录图形验证码")
public class CaptchaController {

    private final CaptchaService captchaService;

    @GetMapping("/captcha")
    @Operation(summary = "获取验证码", description = "返回验证码 id 与 base64 图片，默认 2 分钟有效，一次性使用")
    public Result<CaptchaChallenge> captcha() {
        return Result.success(captchaService.generate());
    }
}
