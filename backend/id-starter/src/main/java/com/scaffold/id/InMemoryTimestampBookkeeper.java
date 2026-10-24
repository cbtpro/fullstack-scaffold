package com.scaffold.id;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 进程内水位记账。不跨进程生效，用于没有数据库或不希望写库的场景，以及单元测试。
 *
 * <p>与 {@link DefaultSnowflakeIdGenerator} 自带内存水位的区别：本对象是显式注入的
 * 依赖，测试可以拿到它验证「水位被正确记录」这一行为本身。
 */
public final class InMemoryTimestampBookkeeper implements TimestampBookkeeper {

    private final AtomicLong watermark = new AtomicLong(-1L);
    private volatile int saveCount = 0;

    @Override
    public long load() {
        return watermark.get();
    }

    @Override
    public void save(long timestampMillis) {
        watermark.accumulateAndGet(timestampMillis, Math::max);
        saveCount++;
    }

    /** 已调用 {@link #save(long)} 的次数，供测试断言节流是否生效。 */
    public int getSaveCount() {
        return saveCount;
    }
}
