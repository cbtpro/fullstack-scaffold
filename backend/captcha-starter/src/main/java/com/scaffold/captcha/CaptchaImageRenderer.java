package com.scaffold.captcha;

/**
 * 验证码图片渲染器。三层 Bean 之一，只负责把文本渲染为可展示的图片：
 * 返回 base64 data URL，前端可直接作为 {@code <img src>} 使用，省去图片下载与存储。
 */
public interface CaptchaImageRenderer {

    /**
     * 渲染验证码图片。
     *
     * @param code 验证码文本
     * @return base64 data URL（如 {@code data:image/png;base64,...}）
     */
    String render(String code);
}
