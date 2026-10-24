package com.scaffold.common.web;

/**
 * 客户端 IP 解析（servlet 与 reactive 共用同一判定口径）。
 *
 * <p>网关链路中请求至少经过一层代理，直连对端地址往往是代理自身，
 * 因此需要从 {@code X-Forwarded-For} 取值。XFF 由每一跳在末尾追加自己看到的对端地址，
 * 前面的段是客户端可伪造的，只有我方可信代理追加的段才可信。
 *
 * <p>取法：从末尾往前数第 {@code trustedProxyCount} 段。跳数为 1 表示只信任
 * 最后一跳（即最后一个可信代理追加的地址），客户端自行伪造的段一律不采信；
 * 链路前面还有额外代理（如 nginx → gateway）时，把跳数调大即可取到更靠前的真实地址。
 */
public final class ClientIpResolver {

    /** 地址不可解析时的占位值，避免限流/审计键落到 null */
    public static final String UNKNOWN = "unknown";

    private ClientIpResolver() {
    }

    /**
     * @param forwardedFor      {@code X-Forwarded-For} 原始值，多段以逗号分隔
     * @param remoteAddress     直连对端地址
     * @param trustedProxyCount 可信代理跳数，小于等于 0 时直接回退到直连对端地址
     */
    public static String resolve(String forwardedFor, String remoteAddress, int trustedProxyCount) {
        if (forwardedFor != null && !forwardedFor.isBlank() && trustedProxyCount > 0) {
            String[] segments = forwardedFor.split(",");
            int index = segments.length - trustedProxyCount;
            if (index >= 0) {
                String candidate = segments[index].trim();
                if (!candidate.isEmpty()) {
                    return candidate;
                }
            }
        }
        return (remoteAddress == null || remoteAddress.isBlank()) ? UNKNOWN : remoteAddress;
    }
}
