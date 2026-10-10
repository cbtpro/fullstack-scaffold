package com.scaffold.user.device;

import org.springframework.stereotype.Component;

/**
 * 可信设备策略默认实现：全部按普通设备处理。
 * 可信登录列表接入后由新实现替换（注册同类型 Bean 即覆盖）。
 */
@Component
public class DefaultTrustedDevicePolicy implements TrustedDevicePolicy {

    @Override
    public boolean isTrusted(Long userId, String fingerprint) {
        // 可信登录列表尚未接入
        return false;
    }
}
