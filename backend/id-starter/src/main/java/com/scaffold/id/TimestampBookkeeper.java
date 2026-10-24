package com.scaffold.id;

/**
 * 时间戳水位记账。记录本节点已发号到的最大时间戳，用于检测跨重启的时钟回拨。
 *
 * <p>为什么需要它：{@link DefaultSnowflakeIdGenerator} 内存里的
 * {@code lastTimestamp} 随进程消失。若节点在 T1 发号、进程重启、系统时钟被
 * 回拨到 T0（T0 &lt; T1），新进程的内存水位是 -1，检测不到回拨，
 * 就会生成时间戳段小于 T1 的 ID——与重启前已发出的 ID 撞在同一时间区间，
 * 单节点内不再单调。持久化水位把这个洞补上。
 *
 * <p>性能约定：本接口只在两个时机被访问——启动时 {@link #load()} 一次，
 * 之后由后台线程按固定间隔 {@link #save(long)}。发号主路径不触碰它，
 * 因此不影响吞吐量。
 */
public interface TimestampBookkeeper {

    /**
     * 读取持久化的水位。
     *
     * @return 上次记录的最大时间戳（纪元无关的毫秒绝对值）；无记录返回 -1
     */
    long load();

    /**
     * 写入水位。允许实现做节流或异步化。
     *
     * @param timestampMillis 本次要记录的最大时间戳
     */
    void save(long timestampMillis);

    /** 关闭底层资源。 */
    default void close() {
    }
}
