package com.scaffold.id;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Snowflake 发号器单元测试：位布局编解码、并发唯一性、多节点唯一性、异常路径。
 */
class SnowflakeIdGeneratorTest {

    private static final long EPOCH = SnowflakeProperties.DEFAULT_EPOCH_MILLIS;

    private static DefaultSnowflakeIdGenerator generator(Clock clock, int workerId) {
        return generator(clock, workerId, props -> {
        });
    }

    private static DefaultSnowflakeIdGenerator generator(Clock clock, int workerId,
                                                         java.util.function.Consumer<SnowflakeProperties> tweak) {
        SnowflakeProperties props = new SnowflakeProperties();
        tweak.accept(props);
        return new DefaultSnowflakeIdGenerator(clock, props, new FixedWorkerIdProvider(workerId));
    }

    @Test
    @DisplayName("默认参数：41 位时间戳 / 10 位 workerId / 12 位序列号，纪元为 2026-01-01T00:00:00Z")
    void defaultLayout() {
        DefaultSnowflakeIdGenerator gen = generator(new MutableClock(EPOCH + 1), 0);
        assertEquals(41, gen.getTimestampBits());
        assertEquals(10, gen.getWorkerIdBits());
        assertEquals(12, gen.getSequenceBits());
        assertEquals(4095L, gen.getMaxSequence());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z").toEpochMilli(), gen.getEpochMillis());
    }

    @Test
    @DisplayName("时间戳编码：解析出的偏移与绝对时刻与时钟一致")
    void timestampEncoding() {
        long now = EPOCH + 123_456L;
        DefaultSnowflakeIdGenerator gen = generator(new MutableClock(now), 7);
        long id = gen.nextId();

        ParsedSnowflakeId parsed = gen.parse(id);
        assertEquals(123_456L, parsed.timestampDelta());
        assertEquals(Instant.ofEpochMilli(now), parsed.timestamp());
        assertEquals(now, parsed.timestampMillis());
    }

    @Test
    @DisplayName("workerId 编码：解析结果与配置一致")
    void workerIdEncoding() {
        DefaultSnowflakeIdGenerator gen = generator(new MutableClock(EPOCH + 1), 513);
        assertEquals(513, gen.parse(gen.nextId()).workerId());
    }

    @Test
    @DisplayName("序列号编码：同一毫秒内从 0 递增，跨毫秒归零")
    void sequenceEncoding() {
        MutableClock clock = new MutableClock(EPOCH + 1);
        DefaultSnowflakeIdGenerator gen = generator(clock, 1);

        assertEquals(0, gen.parse(gen.nextId()).sequence());
        assertEquals(1, gen.parse(gen.nextId()).sequence());
        assertEquals(2, gen.parse(gen.nextId()).sequence());

        clock.advance(1);
        assertEquals(0, gen.parse(gen.nextId()).sequence(), "跨毫秒后序列号应归零");
    }

    @Test
    @DisplayName("单线程严格递增")
    void monotonicIncreasing() {
        DefaultSnowflakeIdGenerator gen = generator(new SystemClock(), 1);
        long prev = Long.MIN_VALUE;
        for (int i = 0; i < 10_000; i++) {
            long id = gen.nextId();
            assertTrue(id > prev, "第 " + i + " 个 ID 未大于前一个");
            prev = id;
        }
    }

    @Test
    @DisplayName("批量生成：数量正确、严格递增、互不重复")
    void nextIds() {
        DefaultSnowflakeIdGenerator gen = generator(new SystemClock(), 2);
        List<Long> ids = gen.nextIds(1000);
        assertEquals(1000, ids.size());
        Set<Long> unique = new HashSet<>(ids);
        assertEquals(1000, unique.size());
        for (int i = 1; i < ids.size(); i++) {
            assertTrue(ids.get(i) > ids.get(i - 1));
        }
        assertTrue(gen.nextIds(0).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> gen.nextIds(-1));
    }

    @Test
    @DisplayName("多线程唯一性：8 线程各生成 10000 个，全部不重复")
    void multiThreadUniqueness() throws Exception {
        DefaultSnowflakeIdGenerator gen = generator(new SystemClock(), 3);
        int threads = 8;
        int perThread = 10_000;
        Set<Long> all = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Throwable> errors = new ArrayList<>();
        try {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < perThread; i++) {
                            all.add(gen.nextId());
                        }
                    } catch (Throwable e) {
                        synchronized (errors) {
                            errors.add(e);
                        }
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            done.await();
        } finally {
            pool.shutdownNow();
        }
        assertTrue(errors.isEmpty(), "线程内出现异常: " + errors);
        assertEquals(threads * perThread, all.size(), "多线程生成的 ID 出现重复");
    }

    @Test
    @DisplayName("多节点唯一性：1024 个 workerId 在同一时刻各生成 50 个，全部不重复")
    void multiNodeUniqueness() {
        long now = EPOCH + 1_000L;
        Set<Long> all = new HashSet<>();
        for (int workerId = 0; workerId < 1024; workerId++) {
            DefaultSnowflakeIdGenerator gen = generator(new MutableClock(now), workerId);
            for (int i = 0; i < 50; i++) {
                assertTrue(all.add(gen.nextId()),
                        "workerId=" + workerId + " 第 " + i + " 个 ID 与其他节点重复");
            }
        }
        assertEquals(1024 * 50, all.size());
    }

    @Test
    @DisplayName("时钟回拨超过容忍阈值：立即抛 CLOCK_MOVED_BACKWARDS")
    void clockRollbackBeyondTolerance() {
        MutableClock clock = new MutableClock(EPOCH + 1000);
        DefaultSnowflakeIdGenerator gen = generator(clock, 1,
                p -> p.setClockRollbackToleranceMs(1000));
        gen.nextId();
        clock.rewind(5000);

        SnowflakeIdException ex = assertThrows(SnowflakeIdException.class, gen::nextId);
        assertEquals(SnowflakeIdErrorCode.CLOCK_MOVED_BACKWARDS, ex.getErrorCode());
    }

    @Test
    @DisplayName("时钟回拨在容忍阈值内：等待时钟追平后继续发号")
    void clockRollbackWithinTolerance() {
        // 每次读取自动前进 1ms，模拟时钟自然追平
        MutableClock clock = new MutableClock(EPOCH + 1000, 1L);
        DefaultSnowflakeIdGenerator gen = generator(clock, 1,
                p -> p.setClockRollbackToleranceMs(1000));
        long first = gen.nextId();
        clock.rewind(3);

        long second = gen.nextId();
        assertTrue(second > first, "回拨追平后 ID 仍应递增");
        assertTrue(gen.validate(second));
    }

    @Test
    @DisplayName("同毫秒序列号耗尽且时钟不推进：抛 SEQUENCE_EXHAUSTED")
    void sequenceExhausted() {
        MutableClock clock = new MutableClock(EPOCH + 1000);
        DefaultSnowflakeIdGenerator gen = generator(clock, 1,
                p -> p.setMaxSequenceWaitMs(50));
        // 12 位序列号 → 每毫秒 4096 个
        for (int i = 0; i < 4096; i++) {
            gen.nextId();
        }
        SnowflakeIdException ex = assertThrows(SnowflakeIdException.class, gen::nextId);
        assertEquals(SnowflakeIdErrorCode.SEQUENCE_EXHAUSTED, ex.getErrorCode());
    }

    @Test
    @DisplayName("同毫秒序列号耗尽且时钟推进：自动溢出到下一毫秒，ID 保持递增")
    void sequenceExhaustedSpillsToNextMillis() {
        long base = EPOCH + 1000;
        // 前 4097 次读取停留在 base，之后推进 1ms，模拟耗尽后进入下一毫秒
        Clock spillClock = new Clock() {
            private int reads = 0;

            @Override
            public long millis() {
                reads++;
                return reads > 4097 ? base + 1 : base;
            }
        };
        DefaultSnowflakeIdGenerator gen = generator(spillClock, 1);

        long lastOfFirstMillis = 0;
        for (int i = 0; i < 4096; i++) {
            lastOfFirstMillis = gen.nextId();
        }
        assertEquals(4095, gen.parse(lastOfFirstMillis).sequence(), "第 4096 个 ID 应耗尽序列号上限");
        assertEquals(base, gen.parse(lastOfFirstMillis).timestampMillis());

        long firstOfNextMillis = gen.nextId();
        assertEquals(base + 1, gen.parse(firstOfNextMillis).timestampMillis(), "应溢出到下一毫秒");
        assertEquals(0, gen.parse(firstOfNextMillis).sequence(), "新毫秒序列号归零");
        assertTrue(firstOfNextMillis > lastOfFirstMillis);
    }

    @Test
    @DisplayName("时间戳溢出：偏移超出 41 位可表示范围时抛 TIMESTAMP_OVERFLOW")
    void timestampOverflow() {
        MutableClock clock = new MutableClock(EPOCH + (1L << 41));
        DefaultSnowflakeIdGenerator gen = generator(clock, 1);
        SnowflakeIdException ex = assertThrows(SnowflakeIdException.class, gen::nextId);
        assertEquals(SnowflakeIdErrorCode.TIMESTAMP_OVERFLOW, ex.getErrorCode());
    }

    @Test
    @DisplayName("时间早于固定纪元：抛 TIMESTAMP_BEFORE_EPOCH")
    void timestampBeforeEpoch() {
        MutableClock clock = new MutableClock(EPOCH - 1);
        DefaultSnowflakeIdGenerator gen = generator(clock, 1);
        SnowflakeIdException ex = assertThrows(SnowflakeIdException.class, gen::nextId);
        assertEquals(SnowflakeIdErrorCode.TIMESTAMP_BEFORE_EPOCH, ex.getErrorCode());
    }

    @Test
    @DisplayName("workerId 超出 10 位范围：抛 INVALID_WORKER_ID")
    void invalidWorkerId() {
        DefaultSnowflakeIdGenerator gen = generator(new MutableClock(EPOCH + 1), 1024);
        SnowflakeIdException ex = assertThrows(SnowflakeIdException.class, gen::nextId);
        assertEquals(SnowflakeIdErrorCode.INVALID_WORKER_ID, ex.getErrorCode());
        assertThrows(SnowflakeIdException.class, () -> new FixedWorkerIdProvider(-1));
    }

    @Test
    @DisplayName("validate 位结构校验")
    void validate() {
        DefaultSnowflakeIdGenerator gen = generator(new MutableClock(EPOCH + 1), 1);
        long id = gen.nextId();
        assertTrue(gen.validate(id));
        assertFalse(gen.validate(0L));
        assertFalse(gen.validate(-1L));
        assertFalse(gen.validate(Long.MIN_VALUE));
    }

    @Test
    @DisplayName("parse 拒绝非正数")
    void parseRejectsNonPositive() {
        DefaultSnowflakeIdGenerator gen = generator(new MutableClock(EPOCH + 1), 1);
        assertThrows(SnowflakeIdException.class, () -> gen.parse(0L));
        assertThrows(SnowflakeIdException.class, () -> gen.parse(-1L));
    }
}
