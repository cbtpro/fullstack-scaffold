package com.scaffold.id;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 水位快照调度器：按固定间隔把发号器的时间戳水位写入 {@link TimestampBookkeeper}。
 *
 * <p>为什么单独拆一个类而不是让发号器自己起线程：发号器是纯计算组件，
 * 起线程会让它带上生命周期与并发复杂度，也会让 JMH 基准难以控制变量。
 * 把调度放外面，发号器只暴露一个 {@link DefaultSnowflakeIdGenerator#currentTimestampWatermark()} 读方法。
 *
 * <p>只在水位真的前进时才写库，节点空闲时不会产生写压力。
 */
@Slf4j
public class SnowflakeWatermarkSnapshotter implements AutoCloseable {

    private final DefaultSnowflakeIdGenerator generator;
    private final TimestampBookkeeper bookkeeper;
    private final long intervalMs;
    private final ScheduledExecutorService scheduler;
    private final AtomicLong lastSaved = new AtomicLong(Long.MIN_VALUE);
    private final AtomicLong saveCount = new AtomicLong();

    public SnowflakeWatermarkSnapshotter(DefaultSnowflakeIdGenerator generator,
                                         TimestampBookkeeper bookkeeper,
                                         long intervalMs) {
        this.generator = generator;
        this.bookkeeper = bookkeeper;
        this.intervalMs = intervalMs;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "snowflake-watermark-snapshot");
            t.setDaemon(true);
            return t;
        });
    }

    /** 启动后台快照。 */
    public void start() {
        if (intervalMs <= 0) {
            log.info("[id-starter] 水位快照间隔<=0，不启动后台快照");
            return;
        }
        scheduler.scheduleWithFixedDelay(this::snapshotSafely, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        log.info("[id-starter] 时间戳水位快照已启动，间隔 {} ms", intervalMs);
    }

    /** 立即执行一次快照（主要用于测试与关闭前收尾）。 */
    public void snapshotNow() {
        snapshotSafely();
    }

    private void snapshotSafely() {
        long watermark = generator.currentTimestampWatermark();
        if (watermark <= 0) {
            return;
        }
        long previous = lastSaved.get();
        if (watermark <= previous) {
            return;
        }
        if (lastSaved.compareAndSet(previous, watermark)) {
            bookkeeper.save(watermark);
            saveCount.incrementAndGet();
        }
    }

    /** 已实际写库的快照次数。 */
    public long getSaveCount() {
        return saveCount.get();
    }

    @Override
    public void close() {
        snapshotSafely();
        scheduler.shutdownNow();
    }
}
