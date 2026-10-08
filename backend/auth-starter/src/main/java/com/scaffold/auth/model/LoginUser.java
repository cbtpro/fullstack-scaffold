package com.scaffold.auth.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 登录成功后的会话用户。也用作 {@code AuthUserLoader} 的返回载体，
 * {@code password} 字段用 {@link JsonIgnore} 隐藏，不参与序列化与响应。
 */
@Data
public class LoginUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;
    private String username;
    private String nickname;
    /** 登录成功后回填，密码模式为 BCrypt 密文 */
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
