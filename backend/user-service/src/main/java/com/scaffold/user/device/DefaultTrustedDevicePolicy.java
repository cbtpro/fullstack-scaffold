package com.scaffold.user.device;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 可信设备策略兜底实现：无可信设备策略 Bean 时启用，一律按普通设备处理。
 *
 * <p>正常环境下 {@link DbTrustedDevicePolicy} 已注册为 {@link org.springframework.context.annotation.Primary} Bean，
 * 此实现退让不生效。仅当移除 Db 实现或独立使用 device 包（无 DB 依赖）时作为安全兜底。
 */
@Component
@ConditionalOnMissingBean(TrustedDevicePolicy.class)
public class DefaultTrustedDevicePolicy implements TrustedDevicePolicy {

    @Override
    public boolean isTrusted(Long userId, String fingerprint) {
        return false;
    }
}
