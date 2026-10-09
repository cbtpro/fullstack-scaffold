package com.scaffold.user.util;

import com.scaffold.user.dto.MenuNode;
import com.scaffold.user.entity.Permission;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 扁平权限列表 → 菜单树构建器。{@link Permission} 通过 {@code parentId} 形成父子关系，
 * 这里聚合成前端可用的 {@link MenuNode} 树。
 *
 * <p>纯静态工具，无 Spring 依赖，便于单测。
 */
public final class MenuTreeBuilder {

    private MenuTreeBuilder() {
    }

    /**
     * 构建菜单树。输入必须已按 type=1（菜单）过滤。
     */
    public static List<MenuNode> build(List<Permission> flat) {
        if (flat == null || flat.isEmpty()) {
            return new ArrayList<>();
        }
        // 按 parentId 分桶，保留父子顺序
        Map<Long, List<Permission>> byParent = new LinkedHashMap<>();
        for (Permission p : flat) {
            Long parentId = p.getParentId() == null ? 0L : p.getParentId();
            byParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(p);
        }
        return buildChildren(0L, byParent);
    }

    private static List<MenuNode> buildChildren(Long parentId, Map<Long, List<Permission>> byParent) {
        List<Permission> level = byParent.getOrDefault(parentId, new ArrayList<>());
        level.sort(Comparator.comparingInt(p -> p.getSort() == null ? 0 : p.getSort()));
        List<MenuNode> nodes = new ArrayList<>(level.size());
        for (Permission p : level) {
            MenuNode node = new MenuNode();
            node.setPath(p.getPath());
            node.setName(p.getCode());
            node.setComponent(p.getComponent());
            MenuNode.MenuMeta meta = new MenuNode.MenuMeta();
            meta.setTitle(p.getName());
            meta.setIcon(p.getIcon());
            node.setMeta(meta);
            List<MenuNode> children = buildChildren(p.getId(), byParent);
            if (!children.isEmpty()) {
                node.setChildren(children);
            }
            nodes.add(node);
        }
        return nodes;
    }
}
