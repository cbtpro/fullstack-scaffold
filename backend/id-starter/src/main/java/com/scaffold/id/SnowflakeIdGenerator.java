package com.scaffold.id;

import java.util.List;

/**
 * 全局 Snowflake 主键生成器。
 *
 * <p>位布局固定为（高位 → 低位）：
 * <pre>
 * 0 | 41-bit timestamp | 10-bit workerId | 12-bit sequence
 * </pre>
 * 时间戳为「当前毫秒 − 固定纪元」，纪元默认 {@code 2026-01-01T00:00:00Z}。
 * 业务类型不占用任何 ID 位，业务含义由表本身承载。
 *
 * <p>实现保证：
 * <ul>
 *   <li>单 JVM 多线程安全，单节点严格递增</li>
 *   <li>不同 workerId 的节点之间全局唯一（前提是 workerId 不冲突）</li>
 *   <li>时钟回拨在容忍阈值内自旋等待，超出阈值抛 {@link SnowflakeIdException}</li>
 *   <li>同毫秒序列号耗尽时等待下一毫秒，等待超时抛 {@link SnowflakeIdException}</li>
 * </ul>
 *
 * <p>不保证：跨节点的严格时间顺序。ID 只包含生成时刻，
 * 不同节点的系统时钟存在偏差，同毫秒内节点之间的先后无法从 ID 推出。
 */
public interface SnowflakeIdGenerator {

    /**
     * 生成下一个 ID。
     *
     * @return 全局唯一、单节点单调递增的 64 位 ID
     * @throws SnowflakeIdException 时钟回拨超限、序列号耗尽、时间戳溢出或 Worker 租约失效
     */
    long nextId();

    /**
     * 批量生成。返回的列表严格递增且互不重复。
     *
     * @param count 生成数量，{@code 0} 返回空列表
     * @throws IllegalArgumentException count 为负
     * @throws SnowflakeIdException 同 {@link #nextId()}
     */
    List<Long> nextIds(int count);

    /**
     * 解析 ID 的位字段。
     *
     * @throws SnowflakeIdException id 非正数（符号位为 1 或为 0）
     */
    ParsedSnowflakeId parse(long id);

    /**
     * 结构性校验：符号位、时间戳区间、workerId 与 sequence 是否落在位宽范围内。
     *
     * <p>只做位结构校验，不能判断 ID 是否由本集群签发，也不能判断是否重复。
     */
    boolean validate(long id);

    /**
     * 当前节点的 Worker 状态是否有效。
     *
     * <p>固定 workerId 模式恒为 {@code true}；租约模式下续约失败会变为
     * {@code false}，此时 {@link #nextId()} 抛
     * {@link SnowflakeIdErrorCode#WORKER_LEASE_EXPIRED}。
     * 供健康检查暴露，使租约丢失能被监控发现而不是等到写库失败。
     */
    default boolean isWorkerValid() {
        return true;
    }
}
