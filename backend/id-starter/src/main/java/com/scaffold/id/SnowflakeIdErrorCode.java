package com.scaffold.id;

/**
 * Snowflake 发号失败的错误分类。
 */
public enum SnowflakeIdErrorCode {

    /** 系统时钟回拨超过容忍阈值，或回拨等待超时 */
    CLOCK_MOVED_BACKWARDS,

    /** 同一毫秒内序列号耗尽且等待下一毫秒超时 */
    SEQUENCE_EXHAUSTED,

    /** 时间戳相对纪元的偏移超出位宽可表示范围（41 位约 69 年） */
    TIMESTAMP_OVERFLOW,

    /** 当前时间早于固定纪元 */
    TIMESTAMP_BEFORE_EPOCH,

    /** workerId 超出位宽范围 */
    INVALID_WORKER_ID,

    /** workerId 已被其他节点持有（冲突检测） */
    WORKER_ID_CONFLICT,

    /** 没有可用的 workerId（1024 个全部被占用） */
    WORKER_ID_UNAVAILABLE,

    /** 节点租约失效，停止发号 */
    WORKER_LEASE_EXPIRED,

    /** ID 位结构非法 */
    INVALID_ID
}
