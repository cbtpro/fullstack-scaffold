package com.scaffold.user.device;

/**
 * 设备登记抽象。登录链路经 {@code LoginTokenEnhancer} 调用，
 * 实现方负责指纹的登记/触碰与风险等级裁决，签发逻辑不感知存储细节。
 */
public interface DeviceRegistry {

    /**
     * 登记或触碰设备：已有指纹更新最近登录 IP/时间，新指纹发新 deviceId 并按
     * {@link TrustedDevicePolicy} 裁决风险等级。
     *
     * @param userId      用户 id
     * @param fingerprint 设备指纹（sha256 hex）
     * @param ip          登录 IP（可为 {@code null}）
     * @return 设备信息（deviceId + 风险等级）
     */
    DeviceInfo registerOrTouch(Long userId, String fingerprint, String ip);
}
