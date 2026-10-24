package com.scaffold.id;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Snowflake 发号器配置，前缀 {@code scaffold.id}。
 *
 * <p>默认位布局：1 符号位 + 41 位时间戳 + 10 位 workerId + 12 位序列号。
 */
@ConfigurationProperties(prefix = "scaffold.id")
public class SnowflakeProperties {

    /** 固定纪元 {@code 2026-01-01T00:00:00Z} 的毫秒值 */
    public static final long DEFAULT_EPOCH_MILLIS = 1767225600000L;

    /** 是否启用本 Starter */
    private boolean enabled = true;

    /** 固定纪元毫秒值 */
    private long epochMillis = DEFAULT_EPOCH_MILLIS;

    /** workerId 位宽 */
    private int workerIdBits = 10;

    /** 序列号位宽 */
    private int sequenceBits = 12;

    /** Worker ID 分配模式：FIXED 固定配置 / LEASE 租约协调 */
    private WorkerIdMode workerIdMode = WorkerIdMode.FIXED;

    /** FIXED 模式下的 workerId，取值 [0, 2^workerIdBits-1] */
    private int workerId = 0;

    /** LEASE 模式下的节点标识，用于续约与冲突检测 */
    private String nodeKey = "";

    /**
     * 时钟回拨容忍阈值（毫秒）。仅在 {@link #clockRollbackStrategy} 为
     * {@link ClockRollbackStrategy#WAIT_THEN_THROW} 时作为等待上限使用。
     */
    private long clockRollbackToleranceMs = 1000L;

    /**
     * 时钟回拨处理策略。默认 {@code WAIT_THEN_THROW}：
     * 容忍窗口内等待追平，超时抛 {@link SnowflakeIdErrorCode#CLOCK_MOVED_BACKWARDS}。
     */
    private ClockRollbackStrategy clockRollbackStrategy = ClockRollbackStrategy.WAIT_THEN_THROW;

    /**
     * 是否启用时间戳水位持久化。开启后节点重启也能检测到时钟回拨——
     * 内存水位随进程消失，重启后若时钟被回拨，仅靠内存检测不出来。
     * 需要 {@code sys_id_watermark} 表与 JdbcTemplate。
     */
    private boolean persistTimestampWatermark = true;

    /**
     * 水位快照间隔（毫秒）。后台线程按此间隔把当前水位写入
     * {@link TimestampBookkeeper}。发号主路径不写库，因此不影响吞吐量；
     * 代价是进程被强杀时最多丢失该间隔内的水位。
     */
    private long watermarkSnapshotIntervalMs = 5000L;

    /**
     * 等待下一毫秒的最大时长（毫秒）。时钟回拨等待与序列号耗尽等待共用此上限，
     * 超时抛 {@link SnowflakeIdErrorCode#SEQUENCE_EXHAUSTED} 或 {@code CLOCK_MOVED_BACKWARDS}。
     */
    private long maxSequenceWaitMs = 1000L;

    /** LEASE 模式租约时长（毫秒） */
    private long leaseTtlMs = 30_000L;

    /** LEASE 模式续约间隔（毫秒） */
    private long leaseRenewIntervalMs = 10_000L;

    public enum WorkerIdMode {
        /** 固定 Worker ID，不做集群协调 */
        FIXED,
        /** 基于 WorkerIdLease 的租约协调 */
        LEASE
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getEpochMillis() {
        return epochMillis;
    }

    public void setEpochMillis(long epochMillis) {
        this.epochMillis = epochMillis;
    }

    public int getWorkerIdBits() {
        return workerIdBits;
    }

    public void setWorkerIdBits(int workerIdBits) {
        this.workerIdBits = workerIdBits;
    }

    public int getSequenceBits() {
        return sequenceBits;
    }

    public void setSequenceBits(int sequenceBits) {
        this.sequenceBits = sequenceBits;
    }

    public WorkerIdMode getWorkerIdMode() {
        return workerIdMode;
    }

    public void setWorkerIdMode(WorkerIdMode workerIdMode) {
        this.workerIdMode = workerIdMode;
    }

    public int getWorkerId() {
        return workerId;
    }

    public void setWorkerId(int workerId) {
        this.workerId = workerId;
    }

    public String getNodeKey() {
        return nodeKey;
    }

    public void setNodeKey(String nodeKey) {
        this.nodeKey = nodeKey;
    }

    public long getClockRollbackToleranceMs() {
        return clockRollbackToleranceMs;
    }

    public void setClockRollbackToleranceMs(long clockRollbackToleranceMs) {
        this.clockRollbackToleranceMs = clockRollbackToleranceMs;
    }

    public ClockRollbackStrategy getClockRollbackStrategy() {
        return clockRollbackStrategy;
    }

    public void setClockRollbackStrategy(ClockRollbackStrategy clockRollbackStrategy) {
        this.clockRollbackStrategy = clockRollbackStrategy;
    }

    public boolean isPersistTimestampWatermark() {
        return persistTimestampWatermark;
    }

    public void setPersistTimestampWatermark(boolean persistTimestampWatermark) {
        this.persistTimestampWatermark = persistTimestampWatermark;
    }

    public long getWatermarkSnapshotIntervalMs() {
        return watermarkSnapshotIntervalMs;
    }

    public void setWatermarkSnapshotIntervalMs(long watermarkSnapshotIntervalMs) {
        this.watermarkSnapshotIntervalMs = watermarkSnapshotIntervalMs;
    }

    public long getMaxSequenceWaitMs() {
        return maxSequenceWaitMs;
    }

    public void setMaxSequenceWaitMs(long maxSequenceWaitMs) {
        this.maxSequenceWaitMs = maxSequenceWaitMs;
    }

    public long getLeaseTtlMs() {
        return leaseTtlMs;
    }

    public void setLeaseTtlMs(long leaseTtlMs) {
        this.leaseTtlMs = leaseTtlMs;
    }

    public long getLeaseRenewIntervalMs() {
        return leaseRenewIntervalMs;
    }

    public void setLeaseRenewIntervalMs(long leaseRenewIntervalMs) {
        this.leaseRenewIntervalMs = leaseRenewIntervalMs;
    }
}
