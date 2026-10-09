package com.scaffold.auth.jwt;

import com.scaffold.auth.AuthManager;
import com.scaffold.auth.AuthProperties;
import com.scaffold.auth.AuthUserLoader;
import com.scaffold.auth.model.LoginRequest;
import com.scaffold.auth.model.LoginResult;
import com.scaffold.auth.model.LoginUser;
import com.scaffold.auth.model.UserInfo;
import com.scaffold.common.api.ResultCode;
import com.scaffold.common.exception.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import javax.crypto.SecretKey;

/**
 * JWT 鉴权实现（Spring Security + jjwt 0.12.x）。
 *
 * <p>设计取舍：
 * <ul>
 *   <li>token claims 含 id/username/nickname/avatar/email/phone/status/roles/permissions，
 *       {@link #getLoginUser} 从 token 重建 {@link UserInfo}，无需回查库</li>
 *   <li>logout 为 no-op：JWT 无状态，客户端 token 至过期前仍有效</li>
 *   <li>{@link AuthUserLoader} 用 {@link ObjectProvider} 延迟获取，让 gateway（不调 login）
 *       即使不提供 loader 也能装配 {@code AuthManager}</li>
 * </ul>
 */
@Slf4j
public class JwtAuthManager implements AuthManager {

    private final AuthProperties props;
    private final ObjectProvider<AuthUserLoader> loaderProvider;
    private final PasswordEncoder encoder;
    private final SecretKey key;

    public JwtAuthManager(AuthProperties props, ObjectProvider<AuthUserLoader> loaderProvider, PasswordEncoder encoder) {
        this.props = props;
        this.loaderProvider = loaderProvider;
        this.encoder = encoder;
        byte[] keyBytes = props.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public LoginResult login(LoginRequest req) {
        AuthUserLoader loader = loaderProvider.getIfAvailable();
        if (loader == null) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "AuthUserLoader 未实现，无法登录");
        }
        LoginUser user = loader.loadUserByUsername(req.getUsername());
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "用户不存在");
        }
        if (user.getPassword() == null || !encoder.matches(req.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "密码错误");
        }
        return LoginResult.of(sign(user));
    }

    private LoginUser sign(LoginUser user) {
        Date now = new Date();
        long expireMs = props.getJwt().getExpireMinutes() * 60_000L;
        Date expire = new Date(now.getTime() + expireMs);

        String token = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("id", user.getId())
                .claim("username", user.getUsername())
                .claim("nickname", user.getNickname())
                .claim("avatar", user.getAvatar())
                .claim("email", user.getEmail())
                .claim("phone", user.getPhone())
                .claim("status", user.getStatus())
                .claim("roles", user.getRoles())
                .claim("permissions", user.getPermissions())
                .issuedAt(now)
                .expiration(expire)
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        user.setToken(token);
        user.setExpireAt(expire.getTime());
        user.setPassword(null);
        return user;
    }

    @Override
    public UserInfo getLoginUser(String token) {
        Claims claims = parse(token);
        LoginUser user = new LoginUser();
        user.setId(claims.get("id", Long.class));
        user.setUsername(claims.get("username", String.class));
        user.setNickname(claims.get("nickname", String.class));
        user.setAvatar(claims.get("avatar", String.class));
        user.setEmail(claims.get("email", String.class));
        user.setPhone(claims.get("phone", String.class));
        user.setStatus(claims.get("status", Integer.class));
        user.setRoles(claims.get("roles", java.util.List.class));
        user.setPermissions(claims.get("permissions", java.util.List.class));
        user.setToken(token);
        user.setExpireAt(claims.getExpiration() != null ? claims.getExpiration().getTime() : null);
        if (claims.getIssuedAt() != null) {
            user.setCreateTime(LocalDateTime.ofInstant(claims.getIssuedAt().toInstant(), ZoneId.systemDefault()));
        }
        return UserInfo.from(user);
    }

    private Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            throw new BizException(ResultCode.UNAUTHORIZED, "token 无效或已过期");
        }
    }

    @Override
    public boolean check(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void logout(String token) {
        log.warn("JWT 无状态，logout 为 no-op，客户端 token 在过期前仍有效");
    }

    @Override
    public String getTokenHeaderName() {
        return props.getTokenHeader();
    }

    @Override
    public String parseToken(String authHeader) {
        if (authHeader == null || authHeader.isEmpty()) {
            return null;
        }
        String prefix = props.getTokenPrefix();
        if (prefix != null && !prefix.isEmpty() && authHeader.startsWith(prefix)) {
            return authHeader.substring(prefix.length()).trim();
        }
        return authHeader.trim();
    }
}
