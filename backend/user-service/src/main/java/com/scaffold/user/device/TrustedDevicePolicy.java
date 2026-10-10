package com.scaffold.user.device;

/**
 * 可信设备策略扩展点。登记设备时裁决风险等级。
 *
 * <p>当前默认实现一律返回 {@code false}（所有新设备按 NORMAL 处理）。
 * 后续接入"可信登录列表"时，只需提供新实现（如查可信设备表/白名单缓存）注册为 Bean
 * 替换默认实现，登录签发与请求校验链路无需任何改动（开闭原则）。
 */
public interface TrustedDevicePolicy {

    /**
     * 判定是否可信设备。
     *
     * @param userId      登录用户 id
     * @param fingerprint 设备指纹
     * @return {@code true} 可信
     */
    boolean isTrusted(Long userId, String fingerprint);
}
