package com.scaffold.auth.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 鉴权内部会话用户。由 {@code AuthUserLoader} 返回（含 BCrypt 密文），
 * 经 {@code AuthManager} 完成 token 签发/校验后回填 token、expireAt。
 *
 * <p>该类仅用于鉴权内部流转，<b>不直接作为响应体</b>：
 * <ul>
 *   <li>登录响应统一走 {@link LoginResult}（{@code token + user + expireAt} 嵌套结构，与前端契约一致）</li>
 *   <li>当前用户信息响应统一走 {@link UserInfo}</li>
 * </ul>
 * 字段集合与 user-service 的 {@code User} 实体对齐，{@code password} 用 {@link JsonIgnore} 隐藏。
 */
@Data
public class LoginUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户主键，对应 {@code User.id}，JWT subject 与 Sa-Token loginId 共用 */
    private Long id;

    private String username;
    private String nickname;
    private String avatar;
    private String email;
    private String phone;

    /** 状态：1启用 0禁用 */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** BCrypt 密文，仅鉴权内部使用，序列化时隐藏 */
    @JsonIgnore
    private String password;

    /** 权限标识 */
    private List<String> permissions = Collections.emptyList();

    /** 角色标识 */
    private List<String> roles = Collections.emptyList();

    /** 登录成功后由 AuthManager 写入 */
    private String token;

    /** 过期时间戳（毫秒） */
    private Long expireAt;
}
