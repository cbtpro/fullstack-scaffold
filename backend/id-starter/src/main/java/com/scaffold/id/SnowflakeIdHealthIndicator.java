package com.scaffold.id;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * 发号器健康检查。把「时钟是否落后于水位」「Worker 是否仍持有有效租约」
 * 暴露给 Actuator，使时钟回拨与租约丢失能被监控发现，而不是等到写库失败。
 *
 * <p>{@code DOWN} 的两种情况：
 * <ul>
 *   <li>{@code clockLagMs < 0}：时钟落后于水位，即当前处于回拨状态，
 *       下一次发号会按 {@link ClockRollbackStrategy} 等待或抛异常。</li>
 *   <li>{@code workerValid=false}：租约模式下续约失败，发号器已停止发号。</li>
 * </ul>
 *
 * <p>仅在 classpath 存在 Spring Boot Actuator 时装配。
 */
public class SnowflakeIdHealthIndicator implements HealthIndicator {

    private final SnowflakeIdGenerator generator;

    public SnowflakeIdHealthIndicator(SnowflakeIdGenerator generator) {
        this.generator = generator;
    }

    @Override
    public Health health() {
        Health.Builder builder = generator.isWorkerValid() ? Health.up() : Health.down();

        int workerId = -1;
        long clockLag = Long.MIN_VALUE;
        long lastRollback = 0;
        String strategy = "UNKNOWN";
        boolean persisted = false;

        if (generator instanceof DefaultSnowflakeIdGenerator d) {
            workerId = d.currentWorkerId();
            clockLag = d.clockLagMs();
            lastRollback = d.getLastRollbackMs();
            strategy = d.getRollbackStrategy().name();
            persisted = d.isWatermarkPersisted();
        }

        if (clockLag != Long.MIN_VALUE && clockLag < 0) {
            builder.down();
        }

        return builder
                .withDetail("workerId", workerId)
                .withDetail("workerValid", generator.isWorkerValid())
                .withDetail("clockLagMs", clockLag == Long.MIN_VALUE ? "n/a" : clockLag)
                .withDetail("lastRollbackMs", lastRollback)
                .withDetail("rollbackStrategy", strategy)
                .withDetail("watermarkPersisted", persisted)
                .build();
    }
}
