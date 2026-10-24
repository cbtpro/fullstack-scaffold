package com.scaffold.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scaffold.auth.model.LoginUser;
import com.scaffold.common.api.PageQuery;
import com.scaffold.common.api.PageResult;
import com.scaffold.common.api.ResultCode;
import com.scaffold.common.exception.BizException;
import com.scaffold.id.SnowflakeIdGenerator;
import com.scaffold.user.dto.ProfileUpdateRequest;
import com.scaffold.user.entity.User;
import com.scaffold.user.entity.UserRole;
import com.scaffold.user.enums.EnableStatus;
import com.scaffold.user.mapper.UserMapper;
import com.scaffold.user.mapper.UserRoleMapper;
import com.scaffold.user.service.RbacService;
import com.scaffold.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 用户服务实现。实现 {@link UserService} 与 {@link com.scaffold.auth.AuthUserLoader} 双契约
 * （后者由 {@link UserService} 继承而来）。
 *
 * <p>继承 MyBatis-Plus {@link ServiceImpl} 以复用 {@code save / saveBatch / saveOrUpdate /
 * updateById / removeById / 逻辑删除 / 分页} 等标准能力；主键由
 * {@code SnowflakeIdentifierGenerator} 统一分配，数据库不使用自增。
 *
 * <p>{@link #loadUserByUsername} 返回带密码的 {@link LoginUser}（BCrypt 密文），
 * 由 auth-starter 的 {@code AuthManager} 完成密码校验与 token 签发。
 * 登录链路在此处通过 {@link RbacService} 加载角色与权限，写入 LoginUser → token claims，
 * 后续接口调用直接从 token 还原，无需回查库。
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final PasswordEncoder passwordEncoder;
    private final RbacService rbacService;
    private final UserRoleMapper userRoleMapper;
    private final SnowflakeIdGenerator idGenerator;

    public UserServiceImpl(PasswordEncoder passwordEncoder,
                           RbacService rbacService,
                           UserRoleMapper userRoleMapper,
                           SnowflakeIdGenerator idGenerator) {
        this.passwordEncoder = passwordEncoder;
        this.rbacService = rbacService;
        this.userRoleMapper = userRoleMapper;
        this.idGenerator = idGenerator;
    }

    @Override
    public PageResult<User> page(PageQuery query, String keyword) {
        Page<User> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            // 括号包裹 OR，避免后续追加 AND 条件时被 OR 吞掉
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getNickname, keyword));
        }
        // 普通列表排序：创建时间倒序 + 主键倒序，保证稳定且确定
        wrapper.orderByDesc(User::getCreateTime).orderByDesc(User::getId);
        IPage<User> result = baseMapper.selectPage(page, wrapper);
        clearPassword(result.getRecords());
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    @Override
    public User getById(Long id) {
        User user = baseMapper.selectById(id);
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
        user.setStatus(user.getStatus() == null ? EnableStatus.ENABLED : user.getStatus());
        baseMapper.insert(user);
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
        baseMapper.updateById(user);
        return getById(user.getId());
    }

    @Override
    public boolean deleteById(Long id) {
        return baseMapper.deleteById(id) > 0;
    }

    @Override
    public List<Long> listRoleIds(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return userRoleMapper.selectRoleIdsByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        if (userId == null) {
            return;
        }
        // 先删后插在同一事务内，避免中间态脏数据
        userRoleMapper.deleteByUserId(userId);
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        List<Long> ids = idGenerator.nextIds(roleIds.size());
        List<UserRole> rows = new ArrayList<>(roleIds.size());
        for (int i = 0; i < roleIds.size(); i++) {
            rows.add(new UserRole(ids.get(i), userId, roleIds.get(i)));
        }
        userRoleMapper.insertBatch(rows);
    }

    @Override
    public User getProfile(Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return getById(userId);
    }

    @Override
    public User updateProfile(Long userId, ProfileUpdateRequest request) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        assertProfileFieldLength(request.nickname(), "昵称", 32);
        assertProfileFieldLength(request.email(), "邮箱", 64);
        assertProfileFieldLength(request.phone(), "手机号", 20);

        // 三个字段都为 null 时无字段可改：直接返回当前资料，
        // 否则会拼出没有 SET 子句的 UPDATE 语句，把一次无意义的请求变成 500
        if (request.nickname() == null && request.email() == null && request.phone() == null) {
            return getById(userId);
        }

        // 仅拼装可改字段，避免实体上其它 null 字段被 update 覆盖成空
        User patch = new User();
        patch.setId(userId);
        patch.setNickname(request.nickname());
        patch.setEmail(request.email());
        patch.setPhone(request.phone());
        baseMapper.updateById(patch);
        return getById(userId);
    }

    /** 本人资料字段长度校验：超长值落库会被截断，宁可在此拦下 */
    private void assertProfileFieldLength(String value, String field, int max) {
        if (value != null && value.length() > max) {
            throw new BizException(ResultCode.PARAM_INVALID, field + "长度不能超过 " + max + " 个字符");
        }
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = baseMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (oldPassword == null || !passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BizException(ResultCode.PARAM_INVALID, "原密码错误");
        }
        validateNewPassword(newPassword);
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BizException(ResultCode.PARAM_INVALID, "新密码不能与原密码相同");
        }
        updatePassword(userId, newPassword);
    }

    @Override
    public void resetPassword(Long userId, String newPassword) {
        getById(userId);
        validateNewPassword(newPassword);
        updatePassword(userId, newPassword);
    }

    /** 新密码强度校验：当前策略为长度 6-32，集中一处便于后续扩展复杂度规则 */
    private void validateNewPassword(String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new BizException(ResultCode.PARAM_INVALID, "新密码不能为空");
        }
        int length = newPassword.length();
        if (length < 6 || length > 32) {
            throw new BizException(ResultCode.PARAM_INVALID, "新密码长度需为 6-32 位");
        }
    }

    /** 仅更新密码字段，避免实体上其他 null 字段参与 update */
    private void updatePassword(Long userId, String rawPassword) {
        User patch = new User();
        patch.setId(userId);
        patch.setPassword(passwordEncoder.encode(rawPassword));
        baseMapper.updateById(patch);
    }

    @Override
    public LoginUser loadUserByUsername(String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        User user = baseMapper.selectOne(wrapper);
        if (user == null) {
            return null;
        }
        if (user.getStatus() == EnableStatus.DISABLED) {
            throw new BizException(ResultCode.FORBIDDEN, "用户已被禁用");
        }
        LoginUser loginUser = new LoginUser();
        loginUser.setId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setNickname(user.getNickname());
        loginUser.setEmail(user.getEmail());
        loginUser.setPhone(user.getPhone());
        loginUser.setStatus(user.getStatus() == null ? null : user.getStatus().getCode());
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
        return baseMapper.selectCount(wrapper) > 0;
    }

    private void clearPassword(List<User> users) {
        if (users == null) {
            return;
        }
        users.forEach(u -> u.setPassword(null));
    }
}
