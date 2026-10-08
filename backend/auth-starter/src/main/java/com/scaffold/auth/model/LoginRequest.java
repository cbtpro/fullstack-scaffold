package com.scaffold.auth.model;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录请求入参。
 */
@Data
public class LoginRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String username;
    private String password;
}
