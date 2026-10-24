package com.scaffold.id;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Snowflake 发号器默认实现。
 *
 * <p>位布局（高位 → 低位）：{@code 0 | timestamp | workerId | sequence}，
 * 其中 timestamp 位宽 = 63 − workerIdBits − sequenceBits（默认 41）。
 *
 * <p>并发：单 JVM 内用单一锁串行化 {@code lastTimestamp / sequence} 的读写，
 * 保证同一节点内严格递增；不同节点靠 workerId 区分，因此不重复的前提是
 * workerId 不冲突（FIXED 模式由部署保证，LEASE 模式由租约注册表保证）。
 *
 * <p>不保证跨节点严格时间顺序：ID 只承载生成时刻，节点间时钟偏差与同毫秒内
 * 的先后关系无法从 ID 本身推出。需要严格顺序的场景请使用
 * {@link GlobalSequenceAllocator} 分配的 {@code sort_seq}。
 */
public final class DefaultSnowflakeIdGenerator implements SnowflakeIdGenerator {

    private final Clock clock;
    private final WorkerIdProvider workerIdProvider;
    private final long epochMillis;
    private final int workerIdBits;
    private final int sequenceBits;
    private final int timestampBits;
    private final long maxWorkerId;
    private final long maxSequence;
    private final long maxTimestampDelta;
    private final int timestampShift;
    private final int workerIdShift;
    private final long clockRollbackToleranceMs;
    private final long maxSequenceWaitMs;
    private final ClockRollbackStrategy rollbackStrategy;
    private final TimestampBookkeeper bookkeeper;

    private final Object lock = new Object();
    private long lastTimestamp = -1L;
    private long sequence = 0L;
    /** 最近一次检测到的回拨幅度，用于健康检查；无回拨时为 0 */
    private volatile long lastRollbackMs = 0L;

    public DefaultSnowflakeIdGenerator(Clock clock, SnowflakeProperties properties, WorkerIdProvider workerIdProvider) {
        this(clock, properties, workerIdProvider, null);
    }

    /**
     * @param bookkeeper 持久化水位，可为 null。非 null 时启动即从其中读取水位，
     *                   使跨进程重启的时钟回拨也能被检测到。
     */
    public DefaultSnowflakeIdGenerator(Clock clock,
                                       SnowflakeProperties properties,
                                       WorkerIdProvider workerIdProvider,
                                       TimestampBookkeeper bookkeeper) {
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
        this.workerIdProvider = Objects.requireNonNull(workerIdProvider, "workerIdProvider 不能为空");
        Objects.requireNonNull(properties, "properties 不能为空");

        this.epochMillis = properties.getEpochMillis();
        this.workerIdBits = properties.getWorkerIdBits();
        this.sequenceBits = properties.getSequenceBits();
        this.timestampBits = 63 - workerIdBits - sequenceBits;
        this.clockRollbackToleranceMs = properties.getClockRollbackToleranceMs();
        this.maxSequenceWaitMs = properties.getMaxSequenceWaitMs();
        this.rollbackStrategy = properties.getClockRollbackStrategy();
        this.bookkeeper = bookkeeper;

        if (workerIdBits <= 0 || sequenceBits <= 0) {
            throw new IllegalArgumentException("workerIdBits 与 sequenceBits 必须为正数");
        }
        if (timestampBits < 1) {
            throw new IllegalArgumentException(
                    "位宽不合法：workerIdBits=" + workerIdBits + " + sequenceBits=" + sequenceBits + " 超过 62 位");
        }

        this.maxWorkerId = (1L << workerIdBits) - 1L;
        this.maxSequence = (1L << sequenceBits) - 1L;
        this.maxTimestampDelta = (1L << timestampBits) - 1L;
        this.timestampShift = workerIdBits + sequenceBits;
        this.workerIdShift = sequenceBits;

        if (bookkeeper != null) {
            long persisted = bookkeeper.load();
            if (persisted > 0) {
                // 不在这里抛异常：交给首次 nextId() 按策略统一处理，
                // 这样「启动时刻时钟尚未追平」与「运行期回拨」走同一条路径
                this.lastTimestamp = persisted;
            }
        }
    }

    @Override
    public long nextId() {
        synchronized (lock) {
            return nextIdLocked();
        }
    }

    @Override
    public List<Long> nextIds(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count 不能为负数: " + count);
        }
        if (count == 0) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>(count);
        synchronized (lock) {
            for (int i = 0; i < count; i++) {
                ids.add(nextIdLocked());
            }
        }
        return ids;
    }

    private long nextIdLocked() {
        int workerId = workerIdProvider.getWorkerId();
        long now = clock.millis();

        if (now < lastTimestamp) {
            long diff = lastTimestamp - now;
            lastRollbackMs = diff;
            switch (rollbackStrategy) {
                case THROW -> throw new SnowflakeIdException(SnowflakeIdErrorCode.CLOCK_MOVED_BACKWARDS,
                        "检测到时钟回拨 " + diff + "ms（策略=THROW，不等待）：lastTimestamp="
                                + lastTimestamp + ", now=" + now);
                case WAIT_FOREVER -> {
                    while (clock.millis() < lastTimestamp) {
                        Thread.onSpinWait();
                    }
                    now = clock.millis();
                }
                case WAIT_THEN_THROW -> {
                    if (diff > clockRollbackToleranceMs) {
                        throw new SnowflakeIdException(SnowflakeIdErrorCode.CLOCK_MOVED_BACKWARDS,
                                "时钟回拨 " + diff + "ms 超过容忍阈值 " + clockRollbackToleranceMs
                                        + "ms：lastTimestamp=" + lastTimestamp + ", now=" + now);
                    }
                    now = waitUntil(lastTimestamp, SnowflakeIdErrorCode.CLOCK_MOVED_BACKWARDS,
                            "等待时钟追平到 lastTimestamp");
                }
            }
        }

        if (now == lastTimestamp) {
            sequence++;
            if (sequence > maxSequence) {
                now = waitUntil(lastTimestamp + 1, SnowflakeIdErrorCode.SEQUENCE_EXHAUSTED,
                        "同毫秒序列号耗尽，等待下一毫秒");
                sequence = 0L;
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = now;
        return compose(now, workerId, sequence);
    }

    /**
     * 自旋等待时钟推进到 target，最多等待 {@code maxSequenceWaitMs}。
     * 等待预算用 {@code System.nanoTime()} 计量，与被测时钟解耦，
     * 因此手动时钟不推进时能确定性地超时失败，而不是无限挂起。
     */
    private long waitUntil(long target, SnowflakeIdErrorCode errorCode, String reason) {
        long start = System.nanoTime();
        long budgetNanos = maxSequenceWaitMs * 1_000_000L;
        long now = clock.millis();
        while (now < target) {
            if (System.nanoTime() - start > budgetNanos) {
                throw new SnowflakeIdException(errorCode,
                        reason + " 超时：已等待 " + maxSequenceWaitMs + "ms，target=" + target + "，当前时钟=" + now);
            }
            Thread.onSpinWait();
            now = clock.millis();
        }
        return now;
    }

    private long compose(long now, long workerId, long seq) {
        long delta = now - epochMillis;
        if (delta < 0) {
            throw new SnowflakeIdException(SnowflakeIdErrorCode.TIMESTAMP_BEFORE_EPOCH,
                    "当前时间早于固定纪元：now=" + now + ", epoch=" + epochMillis);
        }
        if (delta > maxTimestampDelta) {
            throw new SnowflakeIdException(SnowflakeIdErrorCode.TIMESTAMP_OVERFLOW,
                    "时间戳偏移 " + delta + "ms 超出 " + timestampBits + " 位可表示范围（上限 "
                            + maxTimestampDelta + "ms）");
        }
        if (workerId < 0 || workerId > maxWorkerId) {
            throw new SnowflakeIdException(SnowflakeIdErrorCode.INVALID_WORKER_ID,
                    "workerId=" + workerId + " 超出 [0, " + maxWorkerId + "]");
        }
        return (delta << timestampShift) | (workerId << workerIdShift) | seq;
    }

    @Override
    public ParsedSnowflakeId parse(long id) {
        if (id <= 0) {
            throw new SnowflakeIdException(SnowflakeIdErrorCode.INVALID_ID, "ID 必须为正数: " + id);
        }
        long delta = id >>> timestampShift;
        int workerId = (int) ((id >>> workerIdShift) & maxWorkerId);
        int seq = (int) (id & maxSequence);
        return new ParsedSnowflakeId(id, delta, Instant.ofEpochMilli(epochMillis + delta), workerId, seq);
    }

    @Override
    public boolean validate(long id) {
        if (id <= 0) {
            return false;
        }
        long delta = id >>> timestampShift;
        if (delta > maxTimestampDelta) {
            return false;
        }
        long workerId = (id >>> workerIdShift) & maxWorkerId;
        if (workerId > maxWorkerId) {
            return false;
        }
        long seq = id & maxSequence;
        return seq <= maxSequence;
    }

    /** 配置的 workerId 位宽。 */
    public int getWorkerIdBits() {
        return workerIdBits;
    }

    /** 配置的序列号位宽。 */
    public int getSequenceBits() {
        return sequenceBits;
    }

    /** 时间戳位宽，默认 41。 */
    public int getTimestampBits() {
        return timestampBits;
    }

    /** 固定纪元毫秒值。 */
    public long getEpochMillis() {
        return epochMillis;
    }

    /** 单节点每毫秒可生成的 ID 上限，默认 4096。 */
    public long getMaxSequence() {
        return maxSequence;
    }

    /** 当前 workerId，用于诊断与日志。 */
    public int currentWorkerId() {
        return workerIdProvider.getWorkerId();
    }

    /** 当前 Worker 状态是否有效（租约模式下续约失败会变为 false）。 */
    public boolean isWorkerValid() {
        return workerIdProvider.isValid();
    }

    // ========================================================================
    // 水位与健康检查
    // ========================================================================

    /**
     * 当前时间戳水位，即上一次发号所用的时间戳（毫秒绝对值）。
     * 后台快照线程定期把该值交给 {@link TimestampBookkeeper} 持久化。
     * 尚未发号时返回 -1。
     */
    public long currentTimestampWatermark() {
        synchronized (lock) {
            return lastTimestamp;
        }
    }

    /** 最近一次检测到的时钟回拨幅度（毫秒），从未发生回拨时为 0。 */
    public long getLastRollbackMs() {
        return lastRollbackMs;
    }

    /** 当前时钟与水位的差值（毫秒）。负值表示时钟落后于水位，即处于回拨状态。 */
    public long clockLagMs() {
        synchronized (lock) {
            return clock.millis() - lastTimestamp;
        }
    }

    /** 配置的时钟回拨处理策略。 */
    public ClockRollbackStrategy getRollbackStrategy() {
        return rollbackStrategy;
    }

    /** 是否启用了持久化水位（跨重启回拨检测）。 */
    public boolean isWatermarkPersisted() {
        return bookkeeper != null;
    }
}
