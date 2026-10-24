package com.scaffold.user;

import com.scaffold.id.DefaultSnowflakeIdGenerator;
import com.scaffold.id.FixedWorkerIdProvider;
import com.scaffold.id.JdbcWorkerIdLease;
import com.scaffold.id.SnowflakeIdErrorCode;
import com.scaffold.id.SnowflakeIdException;
import com.scaffold.id.SnowflakeProperties;
import com.scaffold.id.SystemClock;
import com.scaffold.id.WorkerIdLease;
import com.scaffold.user.support.ItDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Worker ID 租约的真实 MySQL 并发测试。
 *
 * <p>覆盖原先只在内存桩上验证过的行为：并发争抢、续约、租约被接管后的失效、
 * 过期回收复用、节点重启复用、workerId 耗尽。全部跑在真实 MySQL 上，
 * 靠 {@code worker_id} 主键与 {@code node_key} 唯一键作为冲突检测的物理约束。
 *
 * <p>实例不可用时整体跳过，见 {@link ItDatabase}。
 */
@SpringBootTest(properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "captcha.cache-mode=memory",
        "test-data.exit-after-run=false"
})
@ActiveProfiles("test")
@EnabledIf("databaseReachable")
class WorkerIdLeaseIntegrationTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        ItDatabase.register(registry);
    }

    @SuppressWarnings("unused")
    static boolean databaseReachable() {
        return ItDatabase.isReachable();
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private WorkerIdLease lease;

    /** 本轮测试使用的 nodeKey 前缀，清理时按前缀删除，避免影响其他用例。 */
    private String nodeKeyPrefix;

    @BeforeEach
    void setUp() {
        nodeKeyPrefix = "it-lease-" + UUID.randomUUID().toString().substring(0, 8) + "-";
        // TTL 设短一点，让「过期回收」用例不必真等 30s
        lease = new JdbcWorkerIdLease(jdbcTemplate, 3_000L);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM sys_worker_node WHERE node_key LIKE ?", nodeKeyPrefix + "%");
    }

    // ------------------------------------------------------------------
    // 并发争抢
    // ------------------------------------------------------------------

    @Test
    @DisplayName("16 个节点并发申请 workerId，结果互不重复")
    void concurrentAcquireYieldsUniqueWorkerIds() throws Exception {
        int nodeCount = 16;
        Set<Integer> acquired = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(nodeCount);
        CountDownLatch ready = new CountDownLatch(nodeCount);
        CountDownLatch done = new CountDownLatch(nodeCount);
        List<Throwable> errors = new ArrayList<>();

        try {
            for (int i = 0; i < nodeCount; i++) {
                final String nodeKey = nodeKeyPrefix + "node-" + i;
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        ready.await(); // 尽量同时发起，制造真实争抢
                        acquired.add(lease.acquire(nodeKey, 1023));
                    } catch (Throwable t) {
                        synchronized (errors) {
                            errors.add(t);
                        }
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertTrue(done.await(30, TimeUnit.SECONDS), "并发申请未在 30s 内完成");
        } finally {
            pool.shutdownNow();
        }

        assertTrue(errors.isEmpty(), "并发申请出现异常: " + errors);
        assertEquals(nodeCount, acquired.size(), "并发申请出现重复 workerId: " + acquired);
        // 交叉验证：库里实际持有的行数与申请到的数量一致
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_worker_node WHERE node_key LIKE ?", Integer.class, nodeKeyPrefix + "%");
        assertEquals(nodeCount, rows, "库中的租约行数与申请数不一致");
    }

    @Test
    @DisplayName("同一 nodeKey 重复申请复用同一 workerId（模拟节点重启）")
    void sameNodeKeyReusesWorkerId() {
        String nodeKey = nodeKeyPrefix + "restartable";
        int first = lease.acquire(nodeKey, 1023);
        int second = lease.acquire(nodeKey, 1023);
        assertEquals(first, second, "同一节点重启后应复用原 workerId，而不是另占一个");

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_worker_node WHERE node_key = ?", Integer.class, nodeKey);
        assertEquals(1, rows, "同一 nodeKey 只应存在一行租约");
    }

    // ------------------------------------------------------------------
    // 续约与失效
    // ------------------------------------------------------------------

    @Test
    @DisplayName("持有者续约成功，租约到期时间被推后")
    void renewExtendsLease() {
        String nodeKey = nodeKeyPrefix + "renewable";
        int workerId = lease.acquire(nodeKey, 1023);
        Timestamp before = jdbcTemplate.queryForObject(
                "SELECT lease_expire_at FROM sys_worker_node WHERE node_key = ?", Timestamp.class, nodeKey);

        assertTrue(lease.renew(workerId, nodeKey), "持有者续约应成功");

        Timestamp after = jdbcTemplate.queryForObject(
                "SELECT lease_expire_at FROM sys_worker_node WHERE node_key = ?", Timestamp.class, nodeKey);
        assertTrue(after.after(before) || after.equals(before), "续约后到期时间不应提前");
    }

    @Test
    @DisplayName("租约被他人接管后，原持有者续约失败（续约语句带 node_key 条件）")
    void renewFailsAfterTakeover() {
        String nodeKey = nodeKeyPrefix + "victim";
        String otherNodeKey = nodeKeyPrefix + "taker";
        int workerId = lease.acquire(nodeKey, 1023);

        // 模拟接管：直接把该 workerId 的持有者改成另一个 nodeKey
        jdbcTemplate.update("UPDATE sys_worker_node SET node_key = ? WHERE worker_id = ?", otherNodeKey, workerId);

        assertTrue(lease.renew(workerId, otherNodeKey), "新持有者续约应成功");
        assertEquals(false, lease.renew(workerId, nodeKey), "原持有者续约应失败");
    }

    @Test
    @DisplayName("非持有者对不存在的 workerId 续约返回 false，不抛异常")
    void renewUnknownWorkerReturnsFalse() {
        assertEquals(false, lease.renew(999, nodeKeyPrefix + "ghost"));
    }

    // ------------------------------------------------------------------
    // 过期回收
    // ------------------------------------------------------------------

    @Test
    @DisplayName("租约过期后被回收，workerId 可被其他节点重新获得")
    void expiredLeaseIsReclaimed() {
        String nodeKey = nodeKeyPrefix + "expiring";
        int workerId = lease.acquire(nodeKey, 1023);

        // 把到期时间拨到过去
        jdbcTemplate.update("UPDATE sys_worker_node SET lease_expire_at = ? WHERE node_key = ?",
                new Timestamp(System.currentTimeMillis() - 60_000L), nodeKey);

        String otherNodeKey = nodeKeyPrefix + "claimer";
        int reclaimed = lease.acquire(otherNodeKey, 0); // 上限设为 0：只能拿到被回收的那个

        assertEquals(workerId, reclaimed, "过期租约应被回收并重新分配（上限 0 时只剩回收的那个可用）");
        Integer ghost = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_worker_node WHERE node_key = ?", Integer.class, nodeKey);
        assertEquals(0, ghost, "过期行应已被删除");
    }

    // ------------------------------------------------------------------
    // 释放
    // ------------------------------------------------------------------

    @Test
    @DisplayName("release 后 workerId 立即可被复用")
    void releaseFreesWorkerId() {
        String nodeKey = nodeKeyPrefix + "releaser";
        int workerId = lease.acquire(nodeKey, 1023);
        lease.release(workerId, nodeKey);

        int reused = lease.acquire(nodeKeyPrefix + "next", 0);
        assertEquals(workerId, reused, "释放后的 workerId 应立刻可复用");
    }

    // ------------------------------------------------------------------
    // 耗尽与参数校验
    // ------------------------------------------------------------------

    @Test
    @DisplayName("workerId 全部占用时抛 WORKER_ID_UNAVAILABLE")
    void exhaustedThrows() {
        // 先占用 worker_id = 0
        jdbcTemplate.update(
                "INSERT INTO sys_worker_node (worker_id, node_key, lease_expire_at, heartbeat_at) VALUES (0, ?, ?, NOW(3))",
                nodeKeyPrefix + "occupier", new Timestamp(System.currentTimeMillis() + 60_000L));

        SnowflakeIdException e = assertThrows(SnowflakeIdException.class,
                () -> lease.acquire(nodeKeyPrefix + "late", 0));
        assertEquals(SnowflakeIdErrorCode.WORKER_ID_UNAVAILABLE, e.getErrorCode());
    }

    @Test
    @DisplayName("nodeKey 为空时拒绝申请，避免无标识节点占用租约")
    void blankNodeKeyRejected() {
        SnowflakeIdException e = assertThrows(SnowflakeIdException.class, () -> lease.acquire("  ", 1023));
        assertEquals(SnowflakeIdErrorCode.INVALID_WORKER_ID, e.getErrorCode());
    }

    // ------------------------------------------------------------------
    // 租约时序参数
    // ------------------------------------------------------------------

    @Test
    @DisplayName("默认租约时序满足 续约间隔 <= TTL/2，单轮续约失败不会立刻丢租约")
    void defaultLeaseTimingIsSafe() {
        SnowflakeProperties props = new SnowflakeProperties();
        assertTrue(props.getLeaseRenewIntervalMs() <= props.getLeaseTtlMs() / 2,
                "续约间隔 " + props.getLeaseRenewIntervalMs() + "ms 必须 <= TTL/2 = "
                        + (props.getLeaseTtlMs() / 2) + "ms");
    }

    @Test
    @DisplayName("持有期内 workerId 不冲突：两个节点各发一批 ID，workerId 段不同")
    void distinctWorkerIdsProduceDistinctIdSpaces() {
        int workerA = lease.acquire(nodeKeyPrefix + "a", 1023);
        int workerB = lease.acquire(nodeKeyPrefix + "b", 1023);
        assertNotEquals(workerA, workerB, "两个不同节点不应拿到同一 workerId");

        SnowflakeProperties props = new SnowflakeProperties();
        DefaultSnowflakeIdGenerator genA =
                new DefaultSnowflakeIdGenerator(new SystemClock(), props, new FixedWorkerIdProvider(workerA));
        DefaultSnowflakeIdGenerator genB =
                new DefaultSnowflakeIdGenerator(new SystemClock(), props, new FixedWorkerIdProvider(workerB));

        assertEquals(workerA, genA.parse(genA.nextId()).workerId());
        assertEquals(workerB, genB.parse(genB.nextId()).workerId());
    }
}
