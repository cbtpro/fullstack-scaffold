package com.scaffold.id;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Snowflake 发号器自动装配。
 *
 * <p>装配规则：
 * <ul>
 *   <li>{@code scaffold.id.enabled=false}：整个装配退出</li>
 *   <li>{@code scaffold.id.worker-id-mode=FIXED}（默认）：装配 {@link FixedWorkerIdProvider}，
 *       使用配置里的固定 workerId，不做集群协调</li>
 *   <li>{@code scaffold.id.worker-id-mode=LEASE}：装配 {@link LeasedWorkerIdProvider}，
 *       需要容器内存在 {@link WorkerIdLease} Bean；本项目提供 {@link JdbcWorkerIdLease}</li>
 *   <li>{@code scaffold.id.persist-timestamp-watermark=true}（默认）且存在 JdbcTemplate：
 *       装配 {@link JdbcTimestampBookkeeper} 与后台快照调度器，用于跨重启时钟回拨检测</li>
 * </ul>
 */
@AutoConfiguration(after = {JdbcTemplateAutoConfiguration.class, TransactionAutoConfiguration.class})
@EnableConfigurationProperties(SnowflakeProperties.class)
@ConditionalOnProperty(prefix = "scaffold.id", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SnowflakeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock snowflakeClock() {
        return new SystemClock();
    }

    @Bean
    @ConditionalOnMissingBean(WorkerIdProvider.class)
    @ConditionalOnProperty(prefix = "scaffold.id", name = "worker-id-mode", havingValue = "FIXED", matchIfMissing = true)
    public FixedWorkerIdProvider fixedWorkerIdProvider(SnowflakeProperties properties) {
        int workerId = properties.getWorkerId();
        int maxWorkerId = (int) ((1L << properties.getWorkerIdBits()) - 1L);
        if (workerId > maxWorkerId) {
            throw new SnowflakeIdException(SnowflakeIdErrorCode.INVALID_WORKER_ID,
                    "scaffold.id.worker-id=" + workerId + " 超出 [0, " + maxWorkerId + "]");
        }
        return new FixedWorkerIdProvider(workerId);
    }

    @Bean
    @ConditionalOnMissingBean(WorkerIdProvider.class)
    @ConditionalOnProperty(prefix = "scaffold.id", name = "worker-id-mode", havingValue = "LEASE")
    @ConditionalOnBean(WorkerIdLease.class)
    public LeasedWorkerIdProvider leasedWorkerIdProvider(SnowflakeProperties properties, WorkerIdLease lease) {
        assertLeaseTiming(properties);
        int maxWorkerId = (int) ((1L << properties.getWorkerIdBits()) - 1L);
        return new LeasedWorkerIdProvider(lease, properties.getNodeKey(), maxWorkerId,
                properties.getLeaseRenewIntervalMs());
    }

    @Bean
    @ConditionalOnMissingBean(TimestampBookkeeper.class)
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnProperty(prefix = "scaffold.id", name = "persist-timestamp-watermark", havingValue = "true", matchIfMissing = true)
    public TimestampBookkeeper jdbcTimestampBookkeeper(JdbcTemplate jdbcTemplate,
                                                       SnowflakeProperties properties,
                                                       WorkerIdProvider workerIdProvider) {
        String nodeKey = properties.getNodeKey() == null || properties.getNodeKey().isBlank()
                ? "worker-" + workerIdProvider.getWorkerId()
                : properties.getNodeKey();
        return new JdbcTimestampBookkeeper(jdbcTemplate, nodeKey, workerIdProvider.getWorkerId());
    }

    @Bean
    @ConditionalOnMissingBean(SnowflakeIdGenerator.class)
    public SnowflakeIdGenerator snowflakeIdGenerator(Clock clock,
                                                     SnowflakeProperties properties,
                                                     WorkerIdProvider workerIdProvider,
                                                     ObjectProvider<TimestampBookkeeper> bookkeeper) {
        return new DefaultSnowflakeIdGenerator(clock, properties, workerIdProvider, bookkeeper.getIfAvailable());
    }

    /**
     * 水位后台快照。仅在确实装配了持久化 {@link TimestampBookkeeper} 时生效。
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnBean({JdbcTemplate.class, TimestampBookkeeper.class})
    @ConditionalOnProperty(prefix = "scaffold.id", name = "persist-timestamp-watermark", havingValue = "true", matchIfMissing = true)
    public SnowflakeWatermarkSnapshotter snowflakeWatermarkSnapshotter(SnowflakeIdGenerator generator,
                                                                       TimestampBookkeeper bookkeeper,
                                                                       SnowflakeProperties properties) {
        if (!(generator instanceof DefaultSnowflakeIdGenerator cast)) {
            throw new IllegalStateException(
                    "水位快照需要 DefaultSnowflakeIdGenerator，当前类型为 " + generator.getClass().getName());
        }
        SnowflakeWatermarkSnapshotter snapshotter =
                new SnowflakeWatermarkSnapshotter(cast, bookkeeper, properties.getWatermarkSnapshotIntervalMs());
        snapshotter.start();
        return snapshotter;
    }

    /**
     * MyBatis-Plus 桥接。仅在 classpath 存在 mybatis-plus 时装配。
     */
    @Bean
    @ConditionalOnMissingBean(IdentifierGenerator.class)
    @ConditionalOnClass(IdentifierGenerator.class)
    public SnowflakeIdentifierGenerator snowflakeIdentifierGenerator(SnowflakeIdGenerator generator) {
        return new SnowflakeIdentifierGenerator(generator);
    }

    /**
     * 数据库版 Worker ID 租约。仅在 LEASE 模式且存在 JdbcTemplate 时装配。
     */
    @Bean
    @ConditionalOnMissingBean(WorkerIdLease.class)
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnProperty(prefix = "scaffold.id", name = "worker-id-mode", havingValue = "LEASE")
    public JdbcWorkerIdLease jdbcWorkerIdLease(JdbcTemplate jdbcTemplate, SnowflakeProperties properties) {
        assertLeaseTiming(properties);
        return new JdbcWorkerIdLease(jdbcTemplate, properties.getLeaseTtlMs());
    }

    /**
     * 全局序号分配器。仅在存在 JdbcTemplate 时装配。
     */
    @Bean
    @ConditionalOnMissingBean(GlobalSequenceAllocator.class)
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnBean(JdbcTemplate.class)
    public JdbcSequenceAllocator jdbcSequenceAllocator(JdbcTemplate jdbcTemplate) {
        return new JdbcSequenceAllocator(jdbcTemplate);
    }

    /**
     * 发号器健康检查。仅在 classpath 存在 Actuator 时装配。
     */
    @Bean
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnMissingBean(name = "snowflakeIdHealthIndicator")
    public SnowflakeIdHealthIndicator snowflakeIdHealthIndicator(SnowflakeIdGenerator generator) {
        return new SnowflakeIdHealthIndicator(generator);
    }

    /**
     * 租约时序校验：续约间隔必须明显小于租约时长，否则一次续约失败就会丢租约。
     * 要求 renew &lt;= ttl / 2，留至少一次重试余地。
     */
    private static void assertLeaseTiming(SnowflakeProperties properties) {
        long ttl = properties.getLeaseTtlMs();
        long renew = properties.getLeaseRenewIntervalMs();
        if (ttl <= 0) {
            throw new SnowflakeIdException(SnowflakeIdErrorCode.INVALID_WORKER_ID,
                    "scaffold.id.lease-ttl-ms 必须为正数，当前=" + ttl);
        }
        if (renew <= 0) {
            throw new SnowflakeIdException(SnowflakeIdErrorCode.INVALID_WORKER_ID,
                    "scaffold.id.lease-renew-interval-ms 必须为正数，当前=" + renew);
        }
        if (renew > ttl / 2) {
            throw new SnowflakeIdException(SnowflakeIdErrorCode.INVALID_WORKER_ID,
                    "scaffold.id.lease-renew-interval-ms=" + renew + " 必须 <= lease-ttl-ms/2=" + (ttl / 2)
                            + "，否则单次续约失败即丢失租约");
        }
    }
}
