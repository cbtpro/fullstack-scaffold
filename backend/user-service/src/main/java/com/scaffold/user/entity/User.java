package com.scaffold.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户实体。对应 {@code fs_user.user} 表。
 * 公共字段（id/status/createTime/updateTime/deleted）见 {@link BaseLogicEntity}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user")
public class User extends BaseLogicEntity {

    private String username;

    /** 密码密文，序列化时隐藏 */
    @JsonIgnore
    private String password;

    private String nickname;

    private String email;

    private String phone;
}
