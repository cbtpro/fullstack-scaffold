package com.scaffold.user.service;

import com.scaffold.auth.AuthUserLoader;
import com.scaffold.auth.model.LoginUser;
import com.scaffold.common.api.PageQuery;
import com.scaffold.common.api.PageResult;
import com.scaffold.user.entity.User;

/**
 * 用户业务接口。同时实现 {@link AuthUserLoader}，让 auth-starter 自动注入此实现支持登录。
 */
public interface UserService extends AuthUserLoader {

    /**
     * 分页查询用户（不含密码）。
     */
    PageResult<User> page(PageQuery query, String keyword);

    /**
     * 按主键查询。
     */
    User getById(Long id);

    /**
     * 创建。密码由实现类加密存储。
     */
    User create(User user);

    /**
     * 更新。密码为空表示不修改密码。
     */
    User update(User user);

    /**
     * 逻辑删除。
     */
    boolean deleteById(Long id);

    /**
     * 查询用户已分配的角色 id 列表。
     */
    java.util.List<Long> listRoleIds(Long userId);

    /**
     * 为用户分配角色（全量替换，先删后插）。
     */
    void assignRoles(Long userId, java.util.List<Long> roleIds);

    /**
     * 按用户名查询（含密码），供登录校验。{@link AuthUserLoader} 契约。
     */
    @Override
    LoginUser loadUserByUsername(String username);
}
