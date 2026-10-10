package com.scaffold.user.device;

/**
 * 设备风险等级。
 *
 * <ul>
 *   <li>{@code TRUSTED}：可信登录列表命中的设备，免二次验证</li>
 *   <li>{@code NORMAL}：普通设备</li>
 *   <li>{@code RISKY}：风险设备（预留）</li>
 *   <li>{@code REVOKED}：已吊销，登录直接拒绝</li>
 * </ul>
 */
public enum RiskLevel {
    TRUSTED, NORMAL, RISKY, REVOKED
}
