package com.scaffold.captcha;

/**
 * 验证码文本生成器。三层 Bean 之一，与渲染、缓存互不依赖，可独立替换：
 * 默认提供 {@code random} 随机数字实现，扩展算式/字符/滑动验证时新增实现类即可。
 */
public interface CaptchaCodeGenerator {

    /**
     * 生成验证码文本。
     *
     * @return 验证码内容（如 6 位数字）
     */
    String generate();
}
