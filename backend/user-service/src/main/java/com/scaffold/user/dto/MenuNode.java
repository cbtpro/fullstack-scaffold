package com.scaffold.user.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜单节点 DTO。与前端 {@code @fs/types Menu} 契约对齐：
 * 树形结构，{@code meta} 内嵌 title/icon/permissions 等元信息。
 *
 * <p>由 {@link com.scaffold.user.entity.Permission}（type=1）扁平列表构建而成。
 */
@Data
public class MenuNode implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 前端路由 path */
    private String path;

    /** 路由 name，前端注册路由时使用 */
    private String name;

    /** 前端组件相对路径，如 user-list/index */
    private String component;

    /** 路由元信息 */
    private MenuMeta meta;

    /** 子菜单 */
    private List<MenuNode> children = new ArrayList<>();

    @Data
    public static class MenuMeta implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 显示标题 */
        private String title;

        /** 图标名 */
        private String icon;
    }
}
