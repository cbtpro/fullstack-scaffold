package com.scaffold.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 鉴权配置。前缀 {@code auth}。
 * <pre>
 * auth:
 *   type: JWT            # JWT | SA_TOKEN
 *   token-header: Authorization
 *   token-prefix: "Bearer "
 *   jwt:
 *     secret: "至少32字节的密钥"
 *     expire-minutes: 120
 *   device:
 *     fingerprint-header: X-Device-Fp   # 设备指纹请求头
 *     binding-check: true               # 是否校验 token 指纹与请求头一致
 *   sa-token:
 *     token-name: Authorization
 *     timeout: 7200      # 秒
 *   gateway:
 *     trust-header: X-Gateway-Secret   # 网关注入的共享密钥头
 *     trust-secret: ${AUTH_GATEWAY_SECRET:}  # 网关与下游共享密钥
 *     enforce-trust: false             # 下游 Servlet 服务置 true 强制校验
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    /**
     * 内置默认 JWT 密钥，仅允许开发/演示环境使用。
     * 生产环境必须通过 {@code AUTH_JWT_SECRET} 覆盖，否则启动时 fail-fast（见 {@code JwtSecretValidator}）。
     */
    public static final String DEFAULT_JWT_SECRET = "fullstack-scaffold-default-secret-please-change-it-32bytes";

    /** 鉴权方式，默认 JWT */
    private AuthType type = AuthType.JWT;
    /** token 请求头名 */
    private String tokenHeader = "Authorization";
    /** token 前缀，如 "Bearer " */
    private String tokenPrefix = "Bearer ";
    /** JWT 配置 */
    private Jwt jwt = new Jwt();
    /** Sa-Token 配置 */
    private SaToken saToken = new SaToken();
    /** 设备绑定配置 */
    private Device device = new Device();
    /** 网关信任配置 */
    private Gateway gateway = new Gateway();

    public enum AuthType {
        JWT, SA_TOKEN
    }

    @Data
    public static class Device {
        /** 设备指纹请求头名，前端每请求携带，与 token 内指纹比对 */
        private String fingerprintHeader = "X-Device-Fp";
        /** 是否校验 token 指纹与请求头一致（false 时 token 绑定仅记录不拦截） */
        private boolean bindingCheck = true;
    }

    @Data
    public static class Jwt {
        /** HMAC 密钥，至少 32 字节（256 bit） */
        private String secret = DEFAULT_JWT_SECRET;
        /**
         * 是否显式放行默认/弱密钥。仅用于内部演示与测试，生产环境禁止开启。
         * 默认关闭：非开发 profile 下检测到默认或长度不足 32 字节的密钥将拒绝启动。
         */
        private boolean allowDefaultSecret = false;
        /** 有效期，分钟 */
        private long expireMinutes = 120L;
    }

    @Data
    public static class SaToken {
        /** token 名（请求头/参数名） */
        private String tokenName = "Authorization";
        /** 有效期，秒 */
        private long timeout = 7200L;
    }

    /**
     * 网关信任配置。网关转发时注入共享密钥头，下游 Servlet 服务据此识别
     * 「请求来自可信网关」，防止外部绕过网关直连业务服务并伪造 X-User-Id 等内部头。
     */
    @Data
    public static class Gateway {
        /** 网关与下游共享的密钥请求头名 */
        private String trustHeader = "X-Gateway-Secret";
        /** 共享密钥：网关注入、下游校验；为空表示网关注入侧不启用 */
        private String trustSecret;
        /** 下游是否强制校验网关密钥头（仅 Servlet 服务开启，网关自身不校验） */
        private boolean enforceTrust = false;
    }
}
