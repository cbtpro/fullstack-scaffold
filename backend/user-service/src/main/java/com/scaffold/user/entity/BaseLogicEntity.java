package com.scaffold.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.scaffold.user.enums.DeleteFlag;
import com.scaffold.user.enums.EnableStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 可逻辑删除、带启用状态的实体基类。
 *
 * <p>在 {@link BaseEntity} 基础上追加：
 * <ul>
 *   <li>{@code status}：{@link EnableStatus} 枚举，DB 存 1启用/0禁用</li>
 *   <li>{@code updateTime}：插入与更新时自动填充</li>
 *   <li>{@code deleted}：{@link DeleteFlag} 枚举 + {@link TableLogic} 逻辑删除，序列化隐藏</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BaseLogicEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 启用状态：{@link EnableStatus}，DB 存 1启用 0禁用 */
    private EnableStatus status;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除，仅持久化层使用，序列化时隐藏 */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    @JsonIgnore
    private DeleteFlag deleted;
}
