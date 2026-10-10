package com.scaffold.user.device;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 设备登记实现：MySQL {@code user_device} 表持久化。
 * 同一用户的指纹唯一（uk_user_fp），重复登录只触碰不新增。
 */
@Service
@RequiredArgsConstructor
public class UserDeviceRegistry implements DeviceRegistry {

    private final UserDeviceMapper deviceMapper;
    private final TrustedDevicePolicy trustedDevicePolicy;

    @Override
    public DeviceInfo registerOrTouch(Long userId, String fingerprint, String ip) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<UserDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDevice::getUserId, userId)
                .eq(UserDevice::getFingerprint, fingerprint);
        UserDevice device = deviceMapper.selectOne(wrapper);
        if (device == null) {
            device = new UserDevice();
            device.setUserId(userId);
            device.setDeviceId(UUID.randomUUID().toString().replace("-", ""));
            device.setFingerprint(fingerprint);
            device.setRiskLevel(trustedDevicePolicy.isTrusted(userId, fingerprint)
                    ? RiskLevel.TRUSTED.name()
                    : RiskLevel.NORMAL.name());
            device.setCreateTime(now);
        }
        device.setIp(ip);
        device.setLastLoginTime(now);
        deviceMapper.insertOrUpdate(device);
        return new DeviceInfo(device.getDeviceId(), RiskLevel.valueOf(device.getRiskLevel()));
    }
}
