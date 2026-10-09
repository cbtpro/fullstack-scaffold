package com.scaffold.auth.model;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 用户信息响应 DTO。与前端 {@code @fs/types User} 契约对齐：
 * 字段名、类型一一对应，序列化后 JSON 可直接被前端 User 类型解析。
 *
 * <p>由 {@link LoginUser} 转换而来，剥离 {@code password / token / expireAt} 等鉴权内部字段。
 * 用于 {@code /user/info} 等只读端点。
 */
@Data
public class UserInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String email;
    private String phone;

    /** 角色标识集合，前端按此判定动态路由可见性 */
    private List<String> roles = Collections.emptyList();

    /** 权限标识集合，前端按钮指令按此判定可见性 */
    private List<String> permissions = Collections.emptyList();

    /** 状态：1启用 0禁用 */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 从 {@link LoginUser} 构建只读用户信息，剥离敏感字段。
     */
    public static UserInfo from(LoginUser source) {
        if (source == null) {
            return null;
        }
        UserInfo info = new UserInfo();
        info.id = source.getId();
        info.username = source.getUsername();
        info.nickname = source.getNickname();
        info.avatar = source.getAvatar();
        info.email = source.getEmail();
        info.phone = source.getPhone();
        info.roles = source.getRoles() == null ? Collections.emptyList() : source.getRoles();
        info.permissions = source.getPermissions() == null ? Collections.emptyList() : source.getPermissions();
        info.status = source.getStatus();
        info.createTime = source.getCreateTime();
        info.updateTime = source.getUpdateTime();
        return info;
    }
}
