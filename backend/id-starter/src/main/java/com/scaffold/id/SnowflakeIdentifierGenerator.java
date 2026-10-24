package com.scaffold.id;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;

/**
 * MyBatis-Plus 与统一 Snowflake 服务的桥接。
 *
 * <p>实体侧声明 {@code @TableId(value = "id", type = IdType.ASSIGN_ID)} 后，
 * MyBatis-Plus 在插入前通过本实现取 ID：
 * <ul>
 *   <li>{@code save()} / {@code insert()}：实体 id 为空则填充，非空则保留（接口默认 {@code assignId} 语义）</li>
 *   <li>{@code saveBatch()}：每条记录独立取 ID，同样只在为空时填充</li>
 *   <li>{@code saveOrUpdate()}：id 为空走插入填充，非空走更新，不覆盖已有 ID</li>
 *   <li>{@code updateById()} / {@code removeById()}：按已存在的 id 操作，不重新生成</li>
 * </ul>
 */
public class SnowflakeIdentifierGenerator implements IdentifierGenerator {

    private final SnowflakeIdGenerator delegate;

    public SnowflakeIdentifierGenerator(SnowflakeIdGenerator delegate) {
        this.delegate = delegate;
    }

    @Override
    public Number nextId(Object entity) {
        return delegate.nextId();
    }
}
