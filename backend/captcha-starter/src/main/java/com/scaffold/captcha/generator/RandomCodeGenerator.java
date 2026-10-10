package com.scaffold.captcha.generator;

import com.scaffold.captcha.CaptchaCodeGenerator;
import com.scaffold.captcha.CaptchaProperties;

import java.security.SecureRandom;

/**
 * 随机数字验证码生成器（默认实现）。{@link SecureRandom} 实例线程安全，全局复用。
 */
public class RandomCodeGenerator implements CaptchaCodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CaptchaProperties props;

    public RandomCodeGenerator(CaptchaProperties props) {
        this.props = props;
    }

    @Override
    public String generate() {
        StringBuilder builder = new StringBuilder(props.getLength());
        for (int i = 0; i < props.getLength(); i++) {
            builder.append(RANDOM.nextInt(10));
        }
        return builder.toString();
    }
}
