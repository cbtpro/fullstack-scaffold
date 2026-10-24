package com.scaffold.id;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;

/**
 * 基于数据库表的 Worker ID 租约实现。使用本系统已有的 MySQL，不引入额外基础设施。
 *
 * <p>依赖 {@code sys_worker_node} 表：
 * <pre>
 * CREATE TABLE sys_worker_node (
 *   worker_id      INT          NOT NULL PRIMARY KEY,
 *   node_key       VARCHAR(128) NOT NULL,
 *   lease_expire_at DATETIME(3) NOT NULL,
 *   heartbeat_at   DATETIME(3)  NOT NULL,
 *   UNIQUE KEY uk_node_key (node_key)
 * );
 * </pre>
 *
 * <p>冲突检测依赖两处约束：{@code worker_id} 主键保证同一 workerId 同时只被一行持有，
 * {@code node_key} 唯一键保证同一节点重启后复用原有 workerId 而不是另占一个。
 * 续约语句带 {@code AND node_key = ?}，租约被他人接管后续约必然失败。
 */
@Slf4j
public class JdbcWorkerIdLease implements WorkerIdLease {

    private final JdbcTemplate jdbcTemplate;
    private final long leaseTtlMs;

    public JdbcWorkerIdLease(JdbcTemplate jdbcTemplate, long leaseTtlMs) {
        this.jdbcTemplate = jdbcTemplate;
        this.leaseTtlMs = leaseTtlMs;
    }

    @Override
    public int acquire(String nodeKey, int maxWorkerId) {
        requireNodeKey(nodeKey);
        // 先回收过期租约，使 workerId 可被复用（节点重启后复用安全）
        jdbcTemplate.update("DELETE FROM sys_worker_node WHERE lease_expire_at < NOW(3)");

        Integer existing = queryWorkerIdByNodeKey(nodeKey);
        if (existing != null) {
            Timestamp expireAt = new Timestamp(System.currentTimeMillis() + leaseTtlMs);
            int renewed = jdbcTemplate.update(
                    "UPDATE sys_worker_node SET lease_expire_at = ?, heartbeat_at = NOW(3) WHERE node_key = ?",
                    expireAt, nodeKey);
            if (renewed == 1) {
                log.info("[id-starter] 复用已有 workerId={} nodeKey={}", existing, nodeKey);
                return existing;
            }
        }

        for (int candidate = 0; candidate <= maxWorkerId; candidate++) {
            try {
                jdbcTemplate.update(
                        "INSERT INTO sys_worker_node (worker_id, node_key, lease_expire_at, heartbeat_at) VALUES (?, ?, ?, NOW(3))",
                        candidate, nodeKey, new Timestamp(System.currentTimeMillis() + leaseTtlMs));
                log.info("[id-starter] 分配新 workerId={} nodeKey={}", candidate, nodeKey);
                return candidate;
            } catch (DuplicateKeyException ignored) {
                // 该 workerId 已被占用，尝试下一个
            }
        }
        throw new SnowflakeIdException(SnowflakeIdErrorCode.WORKER_ID_UNAVAILABLE,
                "没有可用的 workerId：0.." + maxWorkerId + " 全部被占用");
    }

    @Override
    public boolean renew(int workerId, String nodeKey) {
        requireNodeKey(nodeKey);
        int rows = jdbcTemplate.update(
                "UPDATE sys_worker_node SET lease_expire_at = ?, heartbeat_at = NOW(3) WHERE worker_id = ? AND node_key = ?",
                new Timestamp(System.currentTimeMillis() + leaseTtlMs), workerId, nodeKey);
        return rows == 1;
    }

    @Override
    public void release(int workerId, String nodeKey) {
        requireNodeKey(nodeKey);
        jdbcTemplate.update("DELETE FROM sys_worker_node WHERE worker_id = ? AND node_key = ?", workerId, nodeKey);
    }

    private Integer queryWorkerIdByNodeKey(String nodeKey) {
        try {
            return jdbcTemplate.queryForObject("SELECT worker_id FROM sys_worker_node WHERE node_key = ?",
                    Integer.class, nodeKey);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }

    private static void requireNodeKey(String nodeKey) {
        if (nodeKey == null || nodeKey.isBlank()) {
            throw new SnowflakeIdException(SnowflakeIdErrorCode.INVALID_WORKER_ID,
                    "LEASE 模式必须配置 scaffold.id.node-key");
        }
    }
}
