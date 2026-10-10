package com.scaffold.auth.device;

import com.scaffold.auth.AuthProperties;
import com.scaffold.auth.model.UserInfo;

/**
 * 设备指纹绑定比对。token 内绑定指纹后，请求头必须携带一致指纹，
 * 防止 token 被复制到其他设备重放。网关与 user-service 过滤器共用同一判定口径。
 */
public final class DeviceBindingMatcher {

    private DeviceBindingMatcher() {
    }

    /**
     * 判定请求指纹是否与 token 绑定指纹一致。
     *
     * <p>规则：
     * <ul>
     *   <li>{@code auth.device.binding-check=false} 时关闭校验，直接放行</li>
     *   <li>token 未绑定指纹（历史 token / 未上报指纹的客户端）平滑放行</li>
     *   <li>token 已绑定指纹：请求未携带或不一致一律拒绝</li>
     * </ul>
     *
     * @param props             鉴权配置
     * @param user              token 还原出的用户信息
     * @param requestFingerprint 请求头中的设备指纹，可为 {@code null}
     * @return {@code true} 校验通过
     */
    public static boolean matches(AuthProperties props, UserInfo user, String requestFingerprint) {
        if (!props.getDevice().isBindingCheck()) {
            return true;
        }
        String bound = user == null ? null : user.getDeviceFingerprint();
        if (bound == null || bound.isEmpty()) {
            return true;
        }
        return bound.equals(requestFingerprint);
    }
}
