package com.scaffold.common.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 客户端地址取法测试。这里的判定直接决定限流键与审计记录里的来源地址，
 * 取错等于把「按 IP 限流」变成「按攻击者自填的字符串限流」，因此逐条固化行为。
 */
class ClientIpResolverTest {

    @Test
    @DisplayName("跳数为 1：只取最后一跳追加的地址，客户端伪造的前段不采信")
    void trustedProxyCountOneIgnoresSpoofedSegments() {
        // 客户端伪造了 1.2.3.4，真实来源是 203.0.113.7（由最后一跳代理追加）
        assertThat(ClientIpResolver.resolve("1.2.3.4, 203.0.113.7", "10.0.0.1", 1))
                .isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("跳数为 2：链路前面还有一层代理时取到更靠前的地址")
    void trustedProxyCountTwoReachesEarlierHop() {
        assertThat(ClientIpResolver.resolve("1.2.3.4, 203.0.113.7, 10.0.0.5", "10.0.0.1", 2))
                .isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("段数少于可信跳数时回退到直连对端地址，不取下标越界的段")
    void fallsBackWhenFewerSegmentsThanTrustedHops() {
        // 只声明 1 跳可信却给出 2 跳，说明链路与配置不符，宁可回退也不要猜
        assertThat(ClientIpResolver.resolve("203.0.113.7", "10.0.0.1", 2)).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("可信跳数为 0 表示不信任任何转发头")
    void zeroTrustedHopsIgnoresForwardedFor() {
        assertThat(ClientIpResolver.resolve("1.2.3.4, 203.0.113.7", "10.0.0.1", 0))
                .isEqualTo("10.0.0.1");
        assertThat(ClientIpResolver.resolve("1.2.3.4", "10.0.0.1", -1)).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("空白段被跳过，落到对端地址")
    void blankSegmentsFallBackToRemoteAddress() {
        assertThat(ClientIpResolver.resolve("203.0.113.7,   ", "10.0.0.1", 1)).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("转发头缺失或空白时取对端地址")
    void missingForwardedForUsesRemoteAddress() {
        assertThat(ClientIpResolver.resolve(null, "10.0.0.1", 1)).isEqualTo("10.0.0.1");
        assertThat(ClientIpResolver.resolve("   ", "10.0.0.1", 1)).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("两处都取不到时返回占位值，避免限流键落到 null")
    void unknownWhenNothingResolvable() {
        assertThat(ClientIpResolver.resolve(null, null, 1)).isEqualTo(ClientIpResolver.UNKNOWN);
        assertThat(ClientIpResolver.resolve(null, "", 1)).isEqualTo(ClientIpResolver.UNKNOWN);
    }
}
