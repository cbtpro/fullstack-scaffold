package com.scaffold.captcha;

import java.time.Duration;
import java.util.UUID;

/**
 * 验证码门面服务：编排生成器、渲染器、缓存三层 Bean。
 *
 * <p>生成：id + 随机文本 → 缓存 → 渲染为图片。
 * 校验：{@link CaptchaCache#consume} 原子消费后再比对，验证码一次性使用，
 * 输错同样作废，防暴力枚举与重放。
 */
public class CaptchaService {

    private final CaptchaCodeGenerator generator;
    private final CaptchaImageRenderer renderer;
    private final CaptchaCache cache;
    private final CaptchaProperties props;

    public CaptchaService(CaptchaCodeGenerator generator,
                          CaptchaImageRenderer renderer,
                          CaptchaCache cache,
                          CaptchaProperties props) {
        this.generator = generator;
        this.renderer = renderer;
        this.cache = cache;
        this.props = props;
    }

    /**
     * 生成验证码挑战。
     */
    public CaptchaChallenge generate() {
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        String code = generator.generate();
        cache.save(captchaId, code, Duration.ofMinutes(props.getTtlMinutes()));
        return new CaptchaChallenge(captchaId, renderer.render(code));
    }

    /**
     * 校验验证码：原子消费后比对（忽略大小写）。
     *
     * @return {@code true} 校验通过
     */
    public boolean verify(String captchaId, String input) {
        if (captchaId == null || captchaId.isBlank() || input == null || input.isBlank()) {
            return false;
        }
        String expected = cache.consume(captchaId);
        return expected != null && expected.equalsIgnoreCase(input.trim());
    }
}
