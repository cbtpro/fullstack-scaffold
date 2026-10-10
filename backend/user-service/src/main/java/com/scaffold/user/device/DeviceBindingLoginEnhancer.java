package com.scaffold.user.device;

import com.scaffold.auth.LoginTokenEnhancer;
import com.scaffold.auth.model.LoginRequest;
import com.scaffold.auth.model.LoginUser;
import com.scaffold.common.api.ResultCode;
import com.scaffold.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 登录设备绑定增强器：在 token 签发前完成设备登记，
 * 将 deviceId / 设备指纹回填 {@link LoginUser} 写入 token claims；
 * 已吊销设备直接中断登录。
 */
@Component
@RequiredArgsConstructor
public class DeviceBindingLoginEnhancer implements LoginTokenEnhancer {

    private final DeviceRegistry deviceRegistry;

    @Override
    public void enhance(LoginUser user, LoginRequest request) {
        String fingerprint = request.getDeviceFingerprint();
        // 前端未上报指纹时不绑定（兼容 Swagger/脚本直连），仅作平滑降级
        if (fingerprint == null || fingerprint.isBlank()) {
            return;
        }
        String normalized = fingerprint.trim().toLowerCase();
        // 指纹必须为 64 位 sha256 hex，格式非法视为未上报，防脏数据入库
        if (!normalized.matches("^[0-9a-f]{64}$")) {
            return;
        }
        DeviceInfo device = deviceRegistry.registerOrTouch(user.getId(), normalized, resolveClientIp());
        if (device.riskLevel() == RiskLevel.REVOKED) {
            throw new BizException(ResultCode.FORBIDDEN, "该设备已被吊销登录资格");
        }
        user.setDeviceId(device.deviceId());
        user.setDeviceFingerprint(normalized);
    }

    /**
     * 解析客户端真实 IP：经网关转发时取 X-Forwarded-For 链路首段（客户端原始 IP）。
     */
    private String resolveClientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return null;
    }
}
