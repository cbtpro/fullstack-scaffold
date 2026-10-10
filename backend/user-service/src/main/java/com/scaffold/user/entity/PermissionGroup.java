package com.scaffold.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 权限组实体。对应 {@code sys_permission_group} 表。
 * 权限组将一组权限打包成可复用单元，角色动态绑定后解析时 UNION 组内权限。
 */
@Data
@TableName("sys_permission_group")
public class PermissionGroup implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 权限组编码，业务侧引用此值 */
    private String code;

    private String name;

    private String description;

    private Integer sort;

    /** 状态：1启用 0禁用 */
    private Integer status;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除，仅持久化层使用，序列化时隐藏 */
    @TableLogic
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    @JsonIgnore
    private Integer deleted;
}
