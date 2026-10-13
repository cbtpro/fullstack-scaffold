package com.scaffold.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 权限实体。对应 {@code sys_permission} 表，覆盖菜单/按钮/接口三类。
 * 公共字段（id/status/createTime/updateTime/deleted）见 {@link BaseLogicEntity}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class Permission extends BaseLogicEntity {

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
}
