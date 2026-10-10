package com.scaffold.captcha;

import com.scaffold.captcha.cache.MemoryCaptchaCache;
import com.scaffold.captcha.cache.RedisCaptchaCache;
import com.scaffold.captcha.generator.RandomCodeGenerator;
import com.scaffold.captcha.render.AwtPngImageRenderer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 验证码自动装配。三层 Bean 均可通过配置切换或由业务方自定义 Bean 覆盖：
 *
 * <ul>
 *   <li>生成器：{@code captcha.code-mode=random}（默认）装配随机数字实现</li>
 *   <li>渲染器：默认 AWT PNG，业务方可注册 {@link CaptchaImageRenderer} Bean 覆盖</li>
 *   <li>缓存：{@code captcha.cache-mode=redis}（默认）/ {@code memory}；
 *       redis 实现要求 classpath 存在 {@link StringRedisTemplate}</li>
 * </ul>
 * 总开关 {@code captcha.enabled=false} 时整个装配退出。
 */
@AutoConfiguration(after = RedisAutoConfiguration.class)
@EnableConfigurationProperties(CaptchaProperties.class)
@ConditionalOnProperty(prefix = "captcha", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CaptchaAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "captcha", name = "code-mode", havingValue = "random", matchIfMissing = true)
    public CaptchaCodeGenerator randomCodeGenerator(CaptchaProperties props) {
        return new RandomCodeGenerator(props);
    }

    @Bean
    @ConditionalOnMissingBean(CaptchaImageRenderer.class)
    public CaptchaImageRenderer awtPngImageRenderer(CaptchaProperties props) {
        return new AwtPngImageRenderer(props);
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnProperty(prefix = "captcha", name = "cache-mode", havingValue = "redis", matchIfMissing = true)
    public CaptchaCache redisCaptchaCache(StringRedisTemplate redisTemplate, CaptchaProperties props) {
        return new RedisCaptchaCache(redisTemplate, props);
    }

    @Bean
    @ConditionalOnProperty(prefix = "captcha", name = "cache-mode", havingValue = "memory")
    public CaptchaCache memoryCaptchaCache() {
        return new MemoryCaptchaCache();
    }

    @Bean
    @ConditionalOnMissingBean
    public CaptchaService captchaService(CaptchaCodeGenerator generator,
                                         CaptchaImageRenderer renderer,
                                         CaptchaCache cache,
                                         CaptchaProperties props) {
        return new CaptchaService(generator, renderer, cache, props);
    }
}
