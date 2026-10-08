package com.scaffold.auth.jwt;

import com.scaffold.auth.AuthManager;
import com.scaffold.auth.AuthProperties;
import com.scaffold.auth.AuthUserLoader;
import com.scaffold.auth.model.LoginRequest;
import com.scaffold.auth.model.LoginUser;
import com.scaffold.common.api.ResultCode;
import com.scaffold.common.exception.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;

/**
 * JWT 鉴权实现（Spring Security + jjwt 0.12.x）。
 *
 * <p>设计取舍：
 * <ul>
 *   <li>token claims 直接含 userId/username/nickname，{@link #getLoginUser} 从 token 重建，无需回查库</li>
 *   <li>logout 为 no-op：JWT 无状态，客户端 token 至过期前仍有效</li>
 *   <li>密码用 {@link PasswordEncoder}（BCrypt）校验，由 {@link AuthUserLoader} 返回密文</li>
 * </ul>
 */
@Slf4j
public class JwtAuthManager implements AuthManager {

    private final AuthProperties props;
    private final AuthUserLoader loader;
    private final PasswordEncoder encoder;
    private final SecretKey key;

    public JwtAuthManager(AuthProperties props, AuthUserLoader loader, PasswordEncoder encoder) {
        this.props = props;
        this.loader = loader;
        this.encoder = encoder;
        byte[] keyBytes = props.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public LoginUser login(LoginRequest req) {
        LoginUser user = loader.loadUserByUsername(req.getUsername());
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "用户不存在");
        }
        if (user.getPassword() == null || !encoder.matches(req.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "密码错误");
        }
        return sign(user);
    }

    private LoginUser sign(LoginUser user) {
        Date now = new Date();
        long expireMs = props.getJwt().getExpireMinutes() * 60_000L;
        Date expire = new Date(now.getTime() + expireMs);

        String token = Jwts.builder()
                .subject(String.valueOf(user.getUserId()))
                .claim("username", user.getUsername())
                .claim("nickname", user.getNickname())
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
    public LoginUser getLoginUser(String token) {
        Claims claims = parse(token);
        LoginUser user = new LoginUser();
        user.setUserId(Long.valueOf(claims.getSubject()));
        user.setUsername(claims.get("username", String.class));
        user.setNickname(claims.get("nickname", String.class));
        user.setToken(token);
        user.setExpireAt(claims.getExpiration().getTime());
        return user;
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
    public String parseToken(HttpServletRequest req) {
        String header = req.getHeader(props.getTokenHeader());
        if (header == null || header.isEmpty()) {
            return null;
        }
        String prefix = props.getTokenPrefix();
        if (prefix != null && !prefix.isEmpty() && header.startsWith(prefix)) {
            return header.substring(prefix.length()).trim();
        }
        return header.trim();
    }
}
