package com.scaffold.captcha;

/**
 * 验证码挑战（下发给前端的载荷）。
 *
 * @param captchaId 验证码 id，登录时原样回传
 * @param image     base64 data URL 图片
 */
public record CaptchaChallenge(String captchaId, String image) {
}
