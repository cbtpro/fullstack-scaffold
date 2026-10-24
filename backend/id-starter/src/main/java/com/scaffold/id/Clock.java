package com.scaffold.id;

/**
 * 可注入时钟。便于用确定性时钟做单元测试与基准测试。
 */
@FunctionalInterface
public interface Clock {

    /** 当前毫秒时间戳（epoch millis）。 */
    long millis();
}
