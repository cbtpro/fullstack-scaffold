package com.scaffold.id.jmh;

import com.scaffold.id.DefaultSnowflakeIdGenerator;
import com.scaffold.id.FixedWorkerIdProvider;
import com.scaffold.id.SnowflakeIdGenerator;
import com.scaffold.id.SnowflakeProperties;
import com.scaffold.id.SystemClock;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Snowflake 发号器 JMH 基准。
 *
 * <p>运行方式（test classpath）：
 * <pre>
 * java -cp &lt;test-classpath&gt; com.scaffold.id.jmh.SnowflakeIdBenchmark
 * </pre>
 *
 * <p>输出吞吐量（ops/ms）与采样延迟百分位（含 p99）。
 * 所有数据均来自本机实际运行，不做任何估算。
 */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class SnowflakeIdBenchmark {

    private static final int WORKER_COUNT = 8;
    private static final int BATCH_SIZE = 100;
    /** 超过 4096（12 位序列号上限），用于观察跨毫秒行为 */
    private static final int OVERFLOW_BATCH = 5000;

    private SnowflakeIdGenerator single;
    private SnowflakeIdGenerator[] workers;
    private final AtomicInteger workerCursor = new AtomicInteger();
    private final ThreadLocal<SnowflakeIdGenerator> threadWorker = new ThreadLocal<>();

    @Setup
    public void setup() {
        SnowflakeProperties props = new SnowflakeProperties();
        single = new DefaultSnowflakeIdGenerator(new SystemClock(), props, new FixedWorkerIdProvider(0));
        workers = new SnowflakeIdGenerator[WORKER_COUNT];
        for (int i = 0; i < WORKER_COUNT; i++) {
            workers[i] = new DefaultSnowflakeIdGenerator(new SystemClock(), props, new FixedWorkerIdProvider(i));
        }
    }

    /** 单线程生成。 */
    @Benchmark
    @Threads(1)
    public long nextIdSingleThread() {
        return single.nextId();
    }

    /** 批量生成 100 个。 */
    @Benchmark
    @Threads(1)
    public List<Long> nextIdBatch100() {
        return single.nextIds(BATCH_SIZE);
    }

    /** 单线程连续生成超过每毫秒序列号上限，观察跨毫秒溢出行为。 */
    @Benchmark
    @Threads(1)
    public long nextIdSequenceOverflow() {
        long last = 0;
        for (int i = 0; i < OVERFLOW_BATCH; i++) {
            last = single.nextId();
        }
        return last;
    }

    /** 多线程争用同一个生成器。 */
    @Benchmark
    @Threads(8)
    public long nextIdMultiThread() {
        return single.nextId();
    }

    /** 模拟 8 个不同 Worker 节点各自发号。 */
    @Benchmark
    @Threads(8)
    public long nextIdMultiWorker() {
        SnowflakeIdGenerator gen = threadWorker.get();
        if (gen == null) {
            int index = Math.floorMod(workerCursor.getAndIncrement(), WORKER_COUNT);
            gen = workers[index];
            threadWorker.set(gen);
        }
        return gen.nextId();
    }

    /**
     * 入口：跑完全部基准并输出文本报告。
     */
    public static void main(String[] args) throws Exception {
        Options options = new OptionsBuilder()
                .include(SnowflakeIdBenchmark.class.getSimpleName())
                .forks(1)
                .warmupIterations(3)
                .warmupTime(TimeValue.seconds(1))
                .measurementIterations(5)
                .measurementTime(TimeValue.seconds(2))
                .resultFormat(ResultFormatType.TEXT)
                .result("/tmp/jmh-snowflake-result.txt")
                .jvmArgs("-Xms512m", "-Xmx512m")
                .build();
        new Runner(options).run();
    }
}
