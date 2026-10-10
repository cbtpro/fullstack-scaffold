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

    /** 图形验证码 id（/auth/captcha 下发），登录时原样回传 */
    private String captchaId;

    /** 用户输入的验证码 */
    private String captchaCode;

    /** 设备指纹（前端采集信号后 sha256），登录时上报用于 token 绑定；不上报则不绑定 */
    private String deviceFingerprint;
}
