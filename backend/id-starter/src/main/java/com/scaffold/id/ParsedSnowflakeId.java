package com.scaffold.id;

import java.time.Instant;

/**
 * Snowflake ID 的位字段解析结果。
 *
 * @param id               原始 ID
 * @param timestampDelta   相对固定纪元的毫秒偏移
 * @param timestamp        绝对生成时刻（纪元 + timestampDelta）
 * @param workerId         workerId 位字段
 * @param sequence         同一毫秒内的序列号
 */
public record ParsedSnowflakeId(
        long id,
        long timestampDelta,
        Instant timestamp,
        int workerId,
        int sequence
) {

    /** 绝对生成时刻的毫秒时间戳（epoch millis）。 */
    public long timestampMillis() {
        return timestamp.toEpochMilli();
    }
}
