package com.scaffold.captcha;

import java.time.Duration;

/**
 * 验证码缓存。三层 Bean 之一，默认 Redis 实现，无 Redis 环境可切 {@code memory} 模式。
 *
 * <p>生命周期：生成时 {@link #save}，校验时 {@link #consume} 原子取出并删除——
 * 无论输入对错都消费，防止同一验证码被反复尝试。
 */
public interface CaptchaCache {

    /**
     * 保存验证码。
     *
     * @param id   验证码 id（全局唯一）
     * @param code 验证码文本
     * @param ttl  有效期
     */
    void save(String id, String code, Duration ttl);

    /**
     * 原子取出并删除验证码（幂等：同一 id 仅首次能取出）。
     *
     * @param id 验证码 id
     * @return 验证码文本；不存在或已过期返回 {@code null}
     */
    String consume(String id);
}
