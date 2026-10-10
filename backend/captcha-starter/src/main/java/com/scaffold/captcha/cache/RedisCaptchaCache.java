package com.scaffold.captcha.cache;

import com.scaffold.captcha.CaptchaCache;
import com.scaffold.captcha.CaptchaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Redis 验证码缓存（默认实现）。多实例部署下验证码全局可见。
 *
 * <p>{@code consume} 使用 {@code GETDEL} 原子取出并删除（要求 Redis 6.2+，
 * docker-compose 内为 Redis 7）；
 * 更旧的 Redis 版本可替换为 Lua 脚本（GET + DEL）实现同等原子性。
 */
@RequiredArgsConstructor
public class RedisCaptchaCache implements CaptchaCache {

    private final StringRedisTemplate redisTemplate;
    private final CaptchaProperties props;

    @Override
    public void save(String id, String code, Duration ttl) {
        redisTemplate.opsForValue().set(props.getKeyPrefix() + id, code, ttl);
    }

    @Override
    public String consume(String id) {
        return redisTemplate.opsForValue().getAndDelete(props.getKeyPrefix() + id);
    }
}
