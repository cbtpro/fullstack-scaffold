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
     * 本人查看资料。与 {@link #getById} 的区别是语义限定为「当前登录人」，
     * 调用方从 token 取 id 后传入，不接受任意 id。
     */
    User getProfile(Long userId);

    /**
     * 本人维护资料：仅允许修改昵称、邮箱、手机。
     *
     * @param userId 当前登录用户 id（由 token 解析）
     */
    User updateProfile(Long userId, com.scaffold.user.dto.ProfileUpdateRequest request);

    /**
     * 本人修改密码：校验原密码后更新为新密码。
     *
     * @param userId      当前登录用户 id
     * @param oldPassword 原密码（明文，由调用方通过 HTTPS 传输）
     * @param newPassword 新密码（明文）
     */
    void changePassword(Long userId, String oldPassword, String newPassword);

    /**
     * 管理员重置指定用户密码（无需原密码）。
     *
     * @param userId      目标用户 id
     * @param newPassword 新密码（明文）
     */
    void resetPassword(Long userId, String newPassword);

    /**
     * 按用户名查询（含密码），供登录校验。{@link AuthUserLoader} 契约。
     */
    @Override
    LoginUser loadUserByUsername(String username);
}
