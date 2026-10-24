package com.scaffold.id;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于数据库表的全局序号分配器。使用本系统已有的 MySQL，不引入额外基础设施。
 *
 * <p>依赖 {@code sys_sequence} 表：
 * <pre>
 * CREATE TABLE sys_sequence (
 *   biz_key       VARCHAR(64)  NOT NULL PRIMARY KEY,
 *   current_value BIGINT       NOT NULL,
 *   update_time   DATETIME(3)  NOT NULL
 * );
 * </pre>
 *
 * <p>并发安全采用 MySQL 的 {@code LAST_INSERT_ID(expr)} 原子模式：
 * <pre>
 * UPDATE sys_sequence SET current_value = LAST_INSERT_ID(current_value + ?) WHERE biz_key = ?
 * SELECT LAST_INSERT_ID()
 * </pre>
 * {@code LAST_INSERT_ID(expr)} 是连接级变量，UPDATE 语句本身由行锁串行化，
 * 因此即使不开启事务，多个连接也不会读到彼此的中间值。
 * 三条语句在 {@code ConnectionCallback} 内复用同一条连接，保证取到的是自己刚写入的值。
 *
 * <p>为什么不用「先 UPDATE 再 SELECT current_value」：UPDATE 提交后别的连接可能已经继续递增，
 * 回读会拿到别人的值，导致重复。这正是原先实现并发下出现重复序号的原因。
 *
 * <p>序号允许出现空洞（分配后未使用不回退），这是有意为之：
 * 保证唯一与单调已经足够支撑排序，回退会引入额外的锁竞争。
 */
@Slf4j
public class JdbcSequenceAllocator implements GlobalSequenceAllocator {

    private final JdbcTemplate jdbcTemplate;

    public JdbcSequenceAllocator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long next(String key) {
        return allocate(key, 1).get(0);
    }

    @Override
    public List<Long> next(String key, int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count 必须为正数: " + count);
        }
        return allocate(key, count);
    }

    private List<Long> allocate(String key, int count) {
        long high = jdbcTemplate.execute((ConnectionCallback<Long>) conn -> {
            int updated = increment(conn, key, count);
            if (updated == 0) {
                // 计数行尚不存在，先插入再递增
                insertIfAbsent(conn, key);
                updated = increment(conn, key, count);
                if (updated == 0) {
                    throw new IllegalStateException("序号分配失败，未能递增计数行: " + key);
                }
            }
            return readLastInsertId(conn);
        });
        List<Long> values = new ArrayList<>(count);
        for (long v = high - count + 1; v <= high; v++) {
            values.add(v);
        }
        return values;
    }

    /** 原子递增，并把新值写入当前连接的 LAST_INSERT_ID。 */
    private int increment(Connection conn, String key, int count) throws java.sql.SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE sys_sequence SET current_value = LAST_INSERT_ID(current_value + ?), "
                        + "update_time = NOW(3) WHERE biz_key = ?")) {
            ps.setInt(1, count);
            ps.setString(2, key);
            return ps.executeUpdate();
        }
    }

    private void insertIfAbsent(Connection conn, String key) throws java.sql.SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT IGNORE INTO sys_sequence (biz_key, current_value, update_time) VALUES (?, 0, NOW(3))")) {
            ps.setString(1, key);
            ps.executeUpdate();
        }
    }

    private long readLastInsertId(Connection conn) throws java.sql.SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT LAST_INSERT_ID()");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new IllegalStateException("读取 LAST_INSERT_ID() 失败");
            }
            return rs.getLong(1);
        }
    }
}
