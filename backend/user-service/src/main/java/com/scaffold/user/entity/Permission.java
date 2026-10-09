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
 * 权限实体。对应 {@code sys_permission} 表，覆盖菜单/按钮/接口三类。
 */
@Data
@TableName("sys_permission")
public class Permission implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父节点 id，0 为根 */
    private Long parentId;

    /** 权限编码：menu:user / user:create / api:user:list */
    private String code;

    private String name;

    /** 类型：1菜单 2按钮 3接口 */
    private Integer type;

    /** 前端路由 path，仅菜单用 */
    private String path;

    /** 前端组件相对路径，仅菜单用 */
    private String component;

    /** 图标名，仅菜单用 */
    private String icon;

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
