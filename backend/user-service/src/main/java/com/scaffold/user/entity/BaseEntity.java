package com.scaffold.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体公共基类：自增主键 + 创建时间。
 *
 * <p>所有持久化实体共享的最小字段集。{@code createTime} 由
 * {@code MetaObjectHandler} 在插入时自动填充。不含逻辑删除/启用状态，
 * 以便只追加、物理删除的实体（如登录设备）也能继承；需要这两类字段的
 * 实体请继承 {@link BaseLogicEntity}。
 */
@Data
public abstract class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
