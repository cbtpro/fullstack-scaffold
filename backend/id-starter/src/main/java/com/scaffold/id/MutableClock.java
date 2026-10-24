package com.scaffold.id;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 手动可控时钟，供单元测试构造时钟回拨、同毫秒序列号耗尽、时间戳溢出等场景。
 *
 * <p>放在 main 源码而非 test 源码，便于基准测试与业务侧诊断复用。
 */
public final class MutableClock implements Clock {

    private final AtomicLong nowMillis;
    /** 每次 {@link #millis()} 的自动步进量，0 表示不自动步进 */
    private final long autoAdvanceMs;

    public MutableClock(long startMillis) {
        this(startMillis, 0L);
    }

    /**
     * @param startMillis   初始毫秒
     * @param autoAdvanceMs 每次读取后自动前进的毫秒数，用于让自旋等待自然结束
     */
    public MutableClock(long startMillis, long autoAdvanceMs) {
        this.nowMillis = new AtomicLong(startMillis);
        this.autoAdvanceMs = autoAdvanceMs;
    }

    @Override
    public long millis() {
        return autoAdvanceMs == 0L
                ? nowMillis.get()
                : nowMillis.getAndAdd(autoAdvanceMs);
    }

    /** 设置为指定时刻。 */
    public void set(long millis) {
        nowMillis.set(millis);
    }

    /** 前进指定毫秒数。 */
    public void advance(long deltaMs) {
        nowMillis.addAndGet(deltaMs);
    }

    /** 回拨指定毫秒数，用于构造时钟回拨场景。 */
    public void rewind(long deltaMs) {
        nowMillis.addAndGet(-deltaMs);
    }

    /** 当前时刻快照，不受 autoAdvance 影响。 */
    public long peek() {
        return nowMillis.get();
    }
}
