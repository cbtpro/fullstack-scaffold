package com.scaffold.id;

/**
 * 系统时钟。生产默认实现。
 */
public final class SystemClock implements Clock {

    @Override
    public long millis() {
        return System.currentTimeMillis();
    }
}
