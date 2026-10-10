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
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

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
        private String secret = "fullstack-scaffold-default-secret-please-change-it-32bytes";
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
}
