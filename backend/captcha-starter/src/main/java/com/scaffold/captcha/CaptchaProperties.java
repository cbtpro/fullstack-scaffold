package com.scaffold.captcha;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 验证码配置。前缀 {@code captcha}。
 * <pre>
 * captcha:
 *   enabled: true        # 总开关
 *   code-mode: random    # 生成模式：random 随机数字（默认）
 *   cache-mode: redis    # 缓存模式：redis（默认）/ memory
 *   length: 6            # 验证码位数
 *   ttl-minutes: 2       # 有效期，分钟
 *   width: 140           # 图片宽
 *   height: 40           # 图片高
 *   key-prefix: "captcha:"   # redis key 前缀
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "captcha")
public class CaptchaProperties {

    /** 是否启用验证码 */
    private boolean enabled = true;

    /** 生成模式，扩展新实现时按此值条件装配 */
    private String codeMode = "random";

    /** 缓存模式：redis / memory */
    private String cacheMode = "redis";

    /** 验证码位数 */
    private int length = 6;

    /** 有效期，分钟 */
    private long ttlMinutes = 2L;

    /** 图片宽（像素） */
    private int width = 140;

    /** 图片高（像素） */
    private int height = 40;

    /** redis 缓存 key 前缀 */
    private String keyPrefix = "captcha:";
}
