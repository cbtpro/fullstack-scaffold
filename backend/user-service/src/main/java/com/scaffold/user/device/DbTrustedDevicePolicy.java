package com.scaffold.user.device;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 基于数据库的可信设备策略实现。
 *
 * <p>查询 {@code user_device} 表的 {@code risk_level} 字段，
 * 命中且为 {@link RiskLevel#TRUSTED} 时视为可信。
 * 用户可通过设备管理界面将设备标记为可信，下次登录即被此策略识别。
 *
 * <p>使用 {@link Primary} 确保与 {@link DefaultTrustedDevicePolicy} 并存时被优先注入。
 * 后续接入外部可信登录列表时，提供新实现替换此 Bean 即可，
 * 登录签发与请求校验链路无需改动（开闭原则）。
 */
@Component
@Primary
@RequiredArgsConstructor
public class DbTrustedDevicePolicy implements TrustedDevicePolicy {

    private final UserDeviceMapper deviceMapper;

    @Override
    public boolean isTrusted(Long userId, String fingerprint) {
        if (userId == null || fingerprint == null) {
            return false;
        }
        LambdaQueryWrapper<UserDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDevice::getUserId, userId)
                .eq(UserDevice::getFingerprint, fingerprint)
                .select(UserDevice::getRiskLevel);
        UserDevice device = deviceMapper.selectOne(wrapper);
        return device != null && RiskLevel.TRUSTED.name().equals(device.getRiskLevel());
    }
}
