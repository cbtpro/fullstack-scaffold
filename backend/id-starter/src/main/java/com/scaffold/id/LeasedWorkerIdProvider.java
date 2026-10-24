package com.scaffold.id;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于租约的 Worker ID 提供者。
 *
 * <p>启动时向 {@link WorkerIdLease} 申请 workerId，后台定时续约。
 * 续约失败即把 {@link #isValid()} 置为 false，发号器下一
 * 次调用立即抛 {@link SnowflakeIdErrorCode#WORKER_LEASE_EXPIRED} 并停止发号，
 * 而不是继续用可能已被其他节点接管的 workerId 发号。
 *
 * <p>关闭时释放租约。
 */
@Slf4j
public final class LeasedWorkerIdProvider implements WorkerIdProvider, AutoCloseable {

    private final WorkerIdLease lease;
    private final String nodeKey;
    private final long renewIntervalMs;
    private final ScheduledExecutorService scheduler;

    private volatile int workerId;
    private final AtomicBoolean valid = new AtomicBoolean(true);

    public LeasedWorkerIdProvider(WorkerIdLease lease, String nodeKey, int maxWorkerId, long renewIntervalMs) {
        this.lease = lease;
        this.nodeKey = nodeKey;
        this.renewIntervalMs = renewIntervalMs;
        this.workerId = lease.acquire(nodeKey, maxWorkerId);
        log.info("[id-starter] 已获取 workerId={} nodeKey={}", workerId, nodeKey);

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "snowflake-lease-renew");
            t.setDaemon(true);
            return t;
        });
        this.scheduler.scheduleAtFixedRate(this::renewQuietly,
                renewIntervalMs, renewIntervalMs, TimeUnit.MILLISECONDS);
    }

    private void renewQuietly() {
        boolean ok;
        try {
            ok = lease.renew(workerId, nodeKey);
        } catch (RuntimeException e) {
            ok = false;
            log.warn("[id-starter] 续约异常 workerId={} nodeKey={}: {}", workerId, nodeKey, e.getMessage());
        }
        if (!ok) {
            if (valid.compareAndSet(true, false)) {
                log.error("[id-starter] Worker 租约失效，停止发号 workerId={} nodeKey={}", workerId, nodeKey);
            }
        }
    }

    @Override
    public int getWorkerId() {
        if (!valid.get()) {
            throw new SnowflakeIdException(SnowflakeIdErrorCode.WORKER_LEASE_EXPIRED,
                    "Worker 租约已失效，停止发号: workerId=" + workerId + ", nodeKey=" + nodeKey);
        }
        return workerId;
    }

    @Override
    public boolean isValid() {
        return valid.get();
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        try {
            lease.release(workerId, nodeKey);
        } catch (RuntimeException e) {
            log.warn("[id-starter] 释放租约失败 workerId={} nodeKey={}: {}", workerId, nodeKey, e.getMessage());
        }
    }
}
