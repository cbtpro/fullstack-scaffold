package com.scaffold.auth;

import com.scaffold.auth.model.LoginRequest;
import com.scaffold.auth.model.LoginUser;

/**
 * 登录 token 增强扩展点。在账号密码校验通过、token 签发之前回调，
 * 实现方可回填 {@code deviceId / deviceFingerprint} 等扩展信息（随 {@code sign} 写入 token claims）。
 *
 * <p>典型实现：设备指纹登记（user-service 的 DeviceBindingLoginEnhancer）、风控校验等。
 * 未提供实现时登录链路保持原样，签发逻辑对此无感知（开闭原则）。
 */
public interface LoginTokenEnhancer {

    /**
     * 签发前回调。可抛出业务异常中断登录（如设备被吊销）。
     *
     * @param user    待签发的登录用户，实现方可回填扩展字段
     * @param request 登录请求原文（含验证码、设备指纹等入参）
     */
    void enhance(LoginUser user, LoginRequest request);
}
