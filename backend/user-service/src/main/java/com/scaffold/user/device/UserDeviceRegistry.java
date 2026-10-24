package com.scaffold.user.device;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scaffold.id.GlobalSequenceAllocator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 设备登记实现：MySQL {@code user_device} 表持久化。
 * 同一用户的指纹唯一（uk_user_fp），重复登录只触碰不新增。
 *
 * <p>新设备登记时向 {@link GlobalSequenceAllocator} 申请 {@code sort_seq}：
 * 设备列表是审计类数据，需要一个跨节点稳定的注册序号。
 * 序号代表分配顺序，不等于事务提交顺序。
 */
@Service
@RequiredArgsConstructor
public class UserDeviceRegistry implements DeviceRegistry {

    /** sort_seq 业务键，与 sys_sequence.biz_key 对应 */
    public static final String DEVICE_SEQ_KEY = "user_device";

    private final UserDeviceMapper deviceMapper;
    private final TrustedDevicePolicy trustedDevicePolicy;
    private final GlobalSequenceAllocator sequenceAllocator;

    @Override
    public DeviceInfo registerOrTouch(Long userId, String fingerprint, String ip) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<UserDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDevice::getUserId, userId)
                .eq(UserDevice::getFingerprint, fingerprint);
        UserDevice device = deviceMapper.selectOne(wrapper);
        if (device == null) {
            device = new UserDevice();
            device.setSortSeq(sequenceAllocator.next(DEVICE_SEQ_KEY));
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
