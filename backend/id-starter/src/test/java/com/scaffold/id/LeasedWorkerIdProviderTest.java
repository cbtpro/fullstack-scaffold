package com.scaffold.id;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Worker ID 租约提供者测试：分配、重启复用、冲突检测、租约失效后停止发号。
 *
 * <p>使用内存版 {@link WorkerIdLease} 替身，验证的是 {@link LeasedWorkerIdProvider}
 * 的租约语义本身，与具体存储实现无关。
 */
class LeasedWorkerIdProviderTest {

    /** 内存版租约替身。 */
    private static final class FakeLease implements WorkerIdLease {
        final Map<Integer, String> holders = new ConcurrentHashMap<>();
        volatile boolean renewSucceeds = true;
        int maxWorkerId;

        FakeLease(int maxWorkerId) {
            this.maxWorkerId = maxWorkerId;
        }

        @Override
        public int acquire(String nodeKey, int maxWorkerId) {
            this.maxWorkerId = maxWorkerId;
            for (Map.Entry<Integer, String> e : holders.entrySet()) {
                if (nodeKey.equals(e.getValue())) {
                    return e.getKey();
                }
            }
            for (int i = 0; i <= maxWorkerId; i++) {
                if (holders.putIfAbsent(i, nodeKey) == null) {
                    return i;
                }
            }
            throw new SnowflakeIdException(SnowflakeIdErrorCode.WORKER_ID_UNAVAILABLE, "无可用 workerId");
        }

        @Override
        public boolean renew(int workerId, String nodeKey) {
            return renewSucceeds && nodeKey.equals(holders.get(workerId));
        }

        @Override
        public void release(int workerId, String nodeKey) {
            holders.remove(workerId, nodeKey);
        }
    }

    private static DefaultSnowflakeIdGenerator generatorWith(WorkerIdProvider provider, Clock clock) {
        SnowflakeProperties props = new SnowflakeProperties();
        return new DefaultSnowflakeIdGenerator(clock, props, provider);
    }

    @Test
    @DisplayName("不同节点分配到不同 workerId")
    void distinctWorkerIdPerNode() {
        FakeLease lease = new FakeLease(1023);
        try (LeasedWorkerIdProvider p1 = new LeasedWorkerIdProvider(lease, "node-a", 1023, 60_000);
             LeasedWorkerIdProvider p2 = new LeasedWorkerIdProvider(lease, "node-b", 1023, 60_000)) {
            assertEquals(0, p1.getWorkerId());
            assertEquals(1, p2.getWorkerId());
        }
    }

    @Test
    @DisplayName("节点重启复用同一 workerId（同一 nodeKey 再次 acquire 不另占）")
    void restartReusesSameWorkerId() {
        FakeLease lease = new FakeLease(1023);
        int first;
        try (LeasedWorkerIdProvider p1 = new LeasedWorkerIdProvider(lease, "node-a", 1023, 60_000)) {
            first = p1.getWorkerId();
        }
        try (LeasedWorkerIdProvider p2 = new LeasedWorkerIdProvider(lease, "node-a", 1023, 60_000)) {
            assertEquals(first, p2.getWorkerId(), "重启后应复用原 workerId");
            assertEquals(1, lease.holders.size(), "重启不应占用新的 workerId");
        }
    }

    @Test
    @DisplayName("workerId 全部占满时抛 WORKER_ID_UNAVAILABLE（冲突检测）")
    void noWorkerIdAvailable() {
        FakeLease lease = new FakeLease(0);
        lease.holders.put(0, "other-node");
        SnowflakeIdException ex = assertThrows(SnowflakeIdException.class,
                () -> new LeasedWorkerIdProvider(lease, "node-a", 0, 60_000));
        assertEquals(SnowflakeIdErrorCode.WORKER_ID_UNAVAILABLE, ex.getErrorCode());
    }

    @Test
    @DisplayName("租约失效后停止发号，而不是继续用可能已被接管的 workerId")
    void stopsIssuingWhenLeaseLost() throws Exception {
        FakeLease lease = new FakeLease(1023);
        MutableClock clock = new MutableClock(SnowflakeProperties.DEFAULT_EPOCH_MILLIS + 1);
        try (LeasedWorkerIdProvider provider = new LeasedWorkerIdProvider(lease, "node-a", 1023, 50)) {
            DefaultSnowflakeIdGenerator gen = generatorWith(provider, clock);
            assertTrue(gen.validate(gen.nextId()), "租约有效时可以发号");

            lease.renewSucceeds = false;
            long deadline = System.currentTimeMillis() + 5_000;
            while (provider.isValid() && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertFalse(provider.isValid(), "续约失败后应标记为失效");

            SnowflakeIdException ex = assertThrows(SnowflakeIdException.class, gen::nextId);
            assertEquals(SnowflakeIdErrorCode.WORKER_LEASE_EXPIRED, ex.getErrorCode());
        }
    }

    @Test
    @DisplayName("发号器在租约有效期间产出的 ID 合法且带正确 workerId")
    void idsCarryLeasedWorkerId() {
        FakeLease lease = new FakeLease(1023);
        try (LeasedWorkerIdProvider p1 = new LeasedWorkerIdProvider(lease, "node-a", 1023, 60_000);
             LeasedWorkerIdProvider p2 = new LeasedWorkerIdProvider(lease, "node-b", 1023, 60_000)) {
            MutableClock clock = new MutableClock(SnowflakeProperties.DEFAULT_EPOCH_MILLIS + 1);
            DefaultSnowflakeIdGenerator g1 = generatorWith(p1, clock);
            DefaultSnowflakeIdGenerator g2 = generatorWith(p2, clock);

            Map<Integer, Integer> seen = new HashMap<>();
            for (int i = 0; i < 100; i++) {
                seen.merge(g1.parse(g1.nextId()).workerId(), 1, Integer::sum);
                seen.merge(g2.parse(g2.nextId()).workerId(), 1, Integer::sum);
            }
            assertEquals(2, seen.size(), "两个节点应使用两个不同的 workerId");
            assertTrue(seen.containsKey(0) && seen.containsKey(1));
        }
    }
}
