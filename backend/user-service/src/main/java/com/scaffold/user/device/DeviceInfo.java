package com.scaffold.user.device;

/**
 * 设备登记结果。
 *
 * @param deviceId  设备标识（新指纹发新 id），写入 token claims
 * @param riskLevel 风险等级，签发链路据此拦截已吊销设备
 */
public record DeviceInfo(String deviceId, RiskLevel riskLevel) {
}
