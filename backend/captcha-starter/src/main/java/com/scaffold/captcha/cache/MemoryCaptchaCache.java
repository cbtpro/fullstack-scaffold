package com.scaffold.captcha.cache;

import com.scaffold.captcha.CaptchaCache;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存验证码缓存（降级实现，{@code captcha.cache-mode=memory} 时装配）。
 * 单机部署/本地调试用；写入时顺手清理过期条目，避免长驻进程内存膨胀。
 */
public class MemoryCaptchaCache implements CaptchaCache {

    /** 缓存条目：验证码文本 + 过期时间戳（毫秒） */
    private record Entry(String code, long expireAt) {
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public void save(String id, String code, Duration ttl) {
        purgeExpired();
        store.put(id, new Entry(code, System.currentTimeMillis() + ttl.toMillis()));
    }

    @Override
    public String consume(String id) {
        Entry entry = store.remove(id);
        if (entry == null) {
            return null;
        }
        return entry.expireAt() >= System.currentTimeMillis() ? entry.code() : null;
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Entry>> iterator = store.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expireAt() < now) {
                iterator.remove();
            }
        }
    }
}
