package com.scaffold.id;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 时钟回拨兜底：三种策略的行为 + 持久化水位跨重启检测。
 */
@DisplayName("时钟回拨兜底")
class ClockRollbackTest {

    private static final long EPOCH = SnowflakeProperties.DEFAULT_EPOCH_MILLIS;
    private static final long T0 = EPOCH + 1_000_000L;
    /**
     * 回拨 1 小时的用例必须从这个更高的基准出发：从 {@link #T0} 回拨 3600000ms
     * 会跨过固定纪元，先触发 TIMESTAMP_BEFORE_EPOCH 而不是 CLOCK_MOVED_BACKWARDS，
     * 断言就测不到想测的东西了。
     */
    private static final long T0_WITH_HEADROOM = EPOCH + 10_000_000L;

    private static SnowflakeProperties props(ClockRollbackStrategy strategy, long toleranceMs) {
        SnowflakeProperties p = new SnowflakeProperties();
        p.setClockRollbackStrategy(strategy);
        p.setClockRollbackToleranceMs(toleranceMs);
        return p;
    }

    // ---------------- WAIT_THEN_THROW ----------------

    @Test
    @DisplayName("WAIT_THEN_THROW：回拨在容忍窗口内会等待追平，不抛异常")
    void waitThenThrowWithinTolerance() {
        MutableClock clock = new MutableClock(T0, 1L); // 每次读取前进 1ms，等待能自然结束
        DefaultSnowflakeIdGenerator gen =
                new DefaultSnowflakeIdGenerator(clock, props(ClockRollbackStrategy.WAIT_THEN_THROW, 1000),
                        new FixedWorkerIdProvider(1));

        long first = gen.nextId();
        clock.rewind(50); // 回拨 50ms，小于 1000ms 容忍窗口

        long second = gen.nextId();
        assertTrue(second > first, "等待追平后应生成更大的 ID");
        // 回拨 50ms，但 autoAdvance 在首次读取时已吃掉 1ms，故记录值为 49
        assertEquals(49L, gen.getLastRollbackMs(), "应记录本次回拨幅度");
    }

    @Test
    @DisplayName("WAIT_THEN_THROW：回拨超过容忍窗口抛 CLOCK_MOVED_BACKWARDS")
    void waitThenThrowBeyondTolerance() {
        MutableClock clock = new MutableClock(T0);
        DefaultSnowflakeIdGenerator gen =
                new DefaultSnowflakeIdGenerator(clock, props(ClockRollbackStrategy.WAIT_THEN_THROW, 1000),
                        new FixedWorkerIdProvider(1));

        gen.nextId();
        clock.rewind(5000); // 回拨 5s，远超 1s 容忍窗口

        SnowflakeIdException e = assertThrows(SnowflakeIdException.class, gen::nextId);
        assertEquals(SnowflakeIdErrorCode.CLOCK_MOVED_BACKWARDS, e.getErrorCode());
    }

    @Test
    @DisplayName("WAIT_THEN_THROW：容忍窗口内但时钟不推进，等待超时而非无限挂起")
    void waitThenThrowTimesOut() {
        MutableClock clock = new MutableClock(T0); // 不自动前进
        SnowflakeProperties p = props(ClockRollbackStrategy.WAIT_THEN_THROW, 1000);
        p.setMaxSequenceWaitMs(50);
        DefaultSnowflakeIdGenerator gen =
                new DefaultSnowflakeIdGenerator(clock, p, new FixedWorkerIdProvider(1));

        gen.nextId();
        clock.rewind(10); // 在容忍窗口内，但时钟永远停住

        SnowflakeIdException e = assertThrows(SnowflakeIdException.class, gen::nextId);
        assertEquals(SnowflakeIdErrorCode.CLOCK_MOVED_BACKWARDS, e.getErrorCode());
    }

    // ---------------- THROW ----------------

    @Test
    @DisplayName("THROW：回拨 1ms 也立即抛异常，不做任何等待")
    void throwImmediately() {
        MutableClock clock = new MutableClock(T0);
        DefaultSnowflakeIdGenerator gen =
                new DefaultSnowflakeIdGenerator(clock, props(ClockRollbackStrategy.THROW, 1000),
                        new FixedWorkerIdProvider(1));

        gen.nextId();
        clock.rewind(1);

        SnowflakeIdException e = assertThrows(SnowflakeIdException.class, gen::nextId);
        assertEquals(SnowflakeIdErrorCode.CLOCK_MOVED_BACKWARDS, e.getErrorCode());
        assertEquals(1L, gen.getLastRollbackMs());
    }

    // ---------------- WAIT_FOREVER ----------------

    @Test
    @DisplayName("WAIT_FOREVER：回拨 10s 也等待追平，不抛异常")
    void waitForeverRecovers() {
        MutableClock clock = new MutableClock(T0, 10L); // 每次读取前进 10ms
        DefaultSnowflakeIdGenerator gen =
                new DefaultSnowflakeIdGenerator(clock, props(ClockRollbackStrategy.WAIT_FOREVER, 1000),
                        new FixedWorkerIdProvider(1));

        gen.nextId();
        long beforeRewind = clock.peek();
        clock.rewind(10_000); // 远超任何容忍窗口

        long id = gen.nextId();
        assertTrue(id > 0, "等待追平后应正常发号");
        assertTrue(clock.peek() > beforeRewind, "时钟应已追平到回拨前的水位");
    }

    // ---------------- 持久化水位 ----------------

    @Test
    @DisplayName("持久化水位：新进程读到上次水位，重启后回拨可被检出")
    void persistedWatermarkDetectsRestartRollback() {
        InMemoryTimestampBookkeeper bookkeeper = new InMemoryTimestampBookkeeper();

        // 第一个「进程」：发号并把水位交给后台快照持久化
        MutableClock clock1 = new MutableClock(T0_WITH_HEADROOM);
        DefaultSnowflakeIdGenerator gen1 = new DefaultSnowflakeIdGenerator(clock1,
                props(ClockRollbackStrategy.WAIT_THEN_THROW, 1000), new FixedWorkerIdProvider(1), bookkeeper);
        long firstId = gen1.nextId();
        bookkeeper.save(gen1.currentTimestampWatermark());
        long persisted = bookkeeper.load();
        assertTrue(persisted > 0, "水位应被持久化");
        assertEquals(T0_WITH_HEADROOM, persisted);

        // 第二个「进程」：时钟被回拨 1 小时后重启，内存水位丢失，但持久化水位还在
        MutableClock clock2 = new MutableClock(T0_WITH_HEADROOM - 3_600_000L);
        DefaultSnowflakeIdGenerator gen2 = new DefaultSnowflakeIdGenerator(clock2,
                props(ClockRollbackStrategy.WAIT_THEN_THROW, 1000), new FixedWorkerIdProvider(1), bookkeeper);

        SnowflakeIdException e = assertThrows(SnowflakeIdException.class, gen2::nextId,
                "重启后时钟落后于持久化水位，应检出回拨");
        assertEquals(SnowflakeIdErrorCode.CLOCK_MOVED_BACKWARDS, e.getErrorCode());
        assertTrue(firstId > 0);
    }

    @Test
    @DisplayName("持久化水位：未开启时重启后检测不到回拨（说明该项配置确实起作用）")
    void withoutBookkeeperRestartRollbackUndetected() {
        // 第一个进程
        MutableClock clock1 = new MutableClock(T0_WITH_HEADROOM);
        DefaultSnowflakeIdGenerator gen1 = new DefaultSnowflakeIdGenerator(clock1,
                props(ClockRollbackStrategy.WAIT_THEN_THROW, 1000), new FixedWorkerIdProvider(1));
        gen1.nextId();

        // 第二个进程：无 bookkeeper，时钟回拨 1 小时
        MutableClock clock2 = new MutableClock(T0_WITH_HEADROOM - 3_600_000L);
        DefaultSnowflakeIdGenerator gen2 = new DefaultSnowflakeIdGenerator(clock2,
                props(ClockRollbackStrategy.WAIT_THEN_THROW, 1000), new FixedWorkerIdProvider(1));

        long id = gen2.nextId(); // 不抛异常，这正是没有水位持久化时的风险
        assertTrue(id > 0);
        assertFalse(gen2.isWatermarkPersisted());
    }

    @Test
    @DisplayName("水位只前进：写入更小的时间戳不会把水位拉回去")
    void watermarkMonotonic() {
        InMemoryTimestampBookkeeper bookkeeper = new InMemoryTimestampBookkeeper();
        bookkeeper.save(5000);
        bookkeeper.save(3000);
        assertEquals(5000L, bookkeeper.load(), "水位应保持在最大值");
    }

    // ---------------- 健康状态 ----------------

    @Test
    @DisplayName("clockLagMs 在回拨状态下为负，可被健康检查识别为 DOWN")
    void clockLagNegativeOnRollback() {
        MutableClock clock = new MutableClock(T0);
        DefaultSnowflakeIdGenerator gen = new DefaultSnowflakeIdGenerator(clock,
                props(ClockRollbackStrategy.WAIT_THEN_THROW, 1000), new FixedWorkerIdProvider(1));

        gen.nextId();
        clock.rewind(200);
        assertTrue(gen.clockLagMs() < 0, "回拨状态下 clockLagMs 应为负");
        assertEquals(200L, -gen.clockLagMs());
    }

    @Test
    @DisplayName("快照调度器只在水位前进时写库，空闲时不产生写压力")
    void snapshotterSkipsWhenIdle() {
        MutableClock clock = new MutableClock(T0);
        DefaultSnowflakeIdGenerator gen = new DefaultSnowflakeIdGenerator(clock,
                props(ClockRollbackStrategy.WAIT_THEN_THROW, 1000), new FixedWorkerIdProvider(1));
        InMemoryTimestampBookkeeper bookkeeper = new InMemoryTimestampBookkeeper();
        SnowflakeWatermarkSnapshotter snapshotter = new SnowflakeWatermarkSnapshotter(gen, bookkeeper, 1000);

        snapshotter.snapshotNow();
        assertEquals(0, bookkeeper.getSaveCount(), "尚未发号时不应写库");

        gen.nextId();
        snapshotter.snapshotNow();
        assertEquals(1, bookkeeper.getSaveCount(), "首次水位前进应写库一次");

        snapshotter.snapshotNow();
        snapshotter.snapshotNow();
        assertEquals(1, bookkeeper.getSaveCount(), "水位未变化时不应重复写库");

        clock.advance(10);
        gen.nextId();
        snapshotter.snapshotNow();
        assertEquals(2, bookkeeper.getSaveCount(), "水位再次前进才写库");

        snapshotter.close();
    }

    @Test
    @DisplayName("WAIT_FOREVER 策略下连续发号仍然单调递增")
    void waitForeverStillMonotonic() {
        MutableClock clock = new MutableClock(T0, 1L);
        DefaultSnowflakeIdGenerator gen = new DefaultSnowflakeIdGenerator(clock,
                props(ClockRollbackStrategy.WAIT_FOREVER, 1000), new FixedWorkerIdProvider(3));

        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            if (i == 250) {
                clock.rewind(3000);
            }
            ids.add(gen.nextId());
        }
        for (int i = 1; i < ids.size(); i++) {
            assertTrue(ids.get(i) > ids.get(i - 1), "第 " + i + " 个 ID 未严格递增");
        }
    }
}
