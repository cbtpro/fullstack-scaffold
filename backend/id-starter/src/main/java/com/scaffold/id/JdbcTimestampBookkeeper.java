package com.scaffold.id;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 数据库版时间戳水位记账。使用本系统已有的 MySQL，不引入额外基础设施。
 *
 * <p>依赖 {@code sys_id_watermark} 表：
 * <pre>
 * CREATE TABLE sys_id_watermark (
 *   node_key        VARCHAR(128) NOT NULL PRIMARY KEY,
 *   worker_id       INT          NOT NULL,
 *   last_timestamp  BIGINT       NOT NULL,
 *   update_time     DATETIME(3)  NOT NULL
 * );
 * </pre>
 *
 * <p>写入用 {@code GREATEST(last_timestamp, ?)} 而不是直接覆盖：
 * 多进程共享同一 node_key（配置错误时的症状）时水位只会前进，不会互相回退。
 *
 * <p>本对象只在启动读取与后台快照时被访问，不在发号主路径上。
 */
@Slf4j
public class JdbcTimestampBookkeeper implements TimestampBookkeeper {

    private final JdbcTemplate jdbcTemplate;
    private final String nodeKey;
    private final int workerId;

    public JdbcTimestampBookkeeper(JdbcTemplate jdbcTemplate, String nodeKey, int workerId) {
        this.jdbcTemplate = jdbcTemplate;
        this.nodeKey = nodeKey;
        this.workerId = workerId;
    }

    @Override
    public long load() {
        try {
            Long value = jdbcTemplate.queryForObject(
                    "SELECT last_timestamp FROM sys_id_watermark WHERE node_key = ?", Long.class, nodeKey);
            return value == null ? -1L : value;
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return -1L;
        } catch (RuntimeException e) {
            // 表不存在或库不可用时不能阻断启动：退回「无历史水位」，
            // 并在日志里说明跨重启回拨检测本节点不可用
            log.warn("[id-starter] 读取时间戳水位失败（nodeKey={}），跨重启时钟回拨检测不可用：{}",
                    nodeKey, e.getMessage());
            return -1L;
        }
    }

    @Override
    public void save(long timestampMillis) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO sys_id_watermark (node_key, worker_id, last_timestamp, update_time) "
                            + "VALUES (?, ?, ?, NOW(3)) "
                            + "ON DUPLICATE KEY UPDATE last_timestamp = GREATEST(last_timestamp, VALUES(last_timestamp)), "
                            + "worker_id = VALUES(worker_id), update_time = NOW(3)",
                    nodeKey, workerId, timestampMillis);
        } catch (RuntimeException e) {
            log.warn("[id-starter] 写入时间戳水位失败（nodeKey={}, ts={}）：{}", nodeKey, timestampMillis, e.getMessage());
        }
    }

    /** 本记账使用的节点标识。 */
    public String getNodeKey() {
        return nodeKey;
    }
}
