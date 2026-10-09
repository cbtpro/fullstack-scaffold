package com.scaffold.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scaffold.auth.model.LoginUser;
import com.scaffold.common.api.PageQuery;
import com.scaffold.common.api.PageResult;
import com.scaffold.common.api.ResultCode;
import com.scaffold.common.exception.BizException;
import com.scaffold.user.entity.User;
import com.scaffold.user.mapper.UserMapper;
import com.scaffold.user.service.RbacService;
import com.scaffold.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 用户服务实现。实现 {@link UserService} 与 {@link com.scaffold.auth.AuthUserLoader} 双契约。
 *
 * <p>{@link #loadUserByUsername} 返回带密码的 {@link LoginUser}（BCrypt 密文），
 * 由 auth-starter 的 {@code AuthManager} 完成密码校验与 token 签发。
 * 登录链路在此处通过 {@link RbacService} 加载角色与权限，写入 LoginUser → token claims，
 * 后续接口调用直接从 token 还原，无需回查库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final RbacService rbacService;

    @Override
    public PageResult<User> page(PageQuery query, String keyword) {
        Page<User> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(User::getUsername, keyword)
                    .or().like(User::getNickname, keyword);
        }
        wrapper.orderByDesc(User::getId);
        IPage<User> result = userMapper.selectPage(page, wrapper);
        clearPassword(result.getRecords());
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    @Override
    public User getById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        user.setPassword(null);
        return user;
    }

    @Override
    public User create(User user) {
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            throw new BizException(ResultCode.PARAM_INVALID, "用户名不能为空");
        }
        if (existsUsername(user.getUsername(), null)) {
            throw new BizException(ResultCode.PARAM_INVALID, "用户名已存在");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword() == null ? "123456" : user.getPassword()));
        user.setStatus(user.getStatus() == null ? 1 : user.getStatus());
        userMapper.insert(user);
        user.setPassword(null);
        return user;
    }

    @Override
    public User update(User user) {
        if (user.getId() == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "用户 id 不能为空");
        }
        if (user.getUsername() != null && existsUsername(user.getUsername(), user.getId())) {
            throw new BizException(ResultCode.PARAM_INVALID, "用户名已存在");
        }
        // 密码为空不更新密码
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            user.setPassword(null);
        } else {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        userMapper.updateById(user);
        return getById(user.getId());
    }

    @Override
    public boolean deleteById(Long id) {
        return userMapper.deleteById(id) > 0;
    }

    @Override
    public LoginUser loadUserByUsername(String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            return null;
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(ResultCode.FORBIDDEN, "用户已被禁用");
        }
        LoginUser loginUser = new LoginUser();
        loginUser.setId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setNickname(user.getNickname());
        loginUser.setEmail(user.getEmail());
        loginUser.setPhone(user.getPhone());
        loginUser.setStatus(user.getStatus());
        loginUser.setCreateTime(user.getCreateTime());
        loginUser.setUpdateTime(user.getUpdateTime());
        loginUser.setPassword(user.getPassword());
        // 通过 RBAC 服务加载角色与权限编码，写入 LoginUser → token claims
        loginUser.setRoles(rbacService.listRoleCodesByUserId(user.getId()));
        loginUser.setPermissions(rbacService.listPermissionCodesByUserId(user.getId()));
        return loginUser;
    }

    private boolean existsUsername(String username, Long excludeId) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        if (excludeId != null) {
            wrapper.ne(User::getId, excludeId);
        }
        return userMapper.selectCount(wrapper) > 0;
    }

    private void clearPassword(List<User> users) {
        if (users == null) {
            return;
        }
        users.forEach(u -> u.setPassword(null));
    }
}
