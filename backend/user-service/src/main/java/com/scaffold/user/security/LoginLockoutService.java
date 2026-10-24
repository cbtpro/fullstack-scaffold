package com.scaffold.user.security;

import com.scaffold.common.api.ResultCode;
import com.scaffold.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 登录失败锁定（Redis 计数）。
 *
 * <p>计数与锁定判定放在一次 Lua 脚本里完成，避免「读计数 → 比较 → 写锁」之间的竞态
 * 让并发爆破刚好卡在上限之下。
 *
 * <p>Redis 不可用时放行（fail-open）并告警：此时图形验证码同样取不到，登录本已不可用，
 * 锁定服务再抛异常只会让错误更难排查。
 */
@Slf4j
@Service
@EnableConfigurationProperties(LoginLockoutProperties.class)
public class LoginLockoutService {

    private static final String FAIL_KEY_PREFIX = "login:fail:";
    private static final String LOCK_KEY_PREFIX = "login:locked:";

    /** 累加失败次数并在到达上限时写入锁：KEYS[1] 计数键，KEYS[2] 锁键；返回 1 表示本次触发了锁定 */
    private static final RedisScript<Long> RECORD_FAILURE_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            if count >= tonumber(ARGV[2]) then
              redis.call('SET', KEYS[2], '1', 'EX', ARGV[3])
              redis.call('DEL', KEYS[1])
              return 1
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final LoginLockoutProperties properties;

    public LoginLockoutService(StringRedisTemplate redisTemplate, LoginLockoutProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * 校验账号是否处于锁定期，锁定中直接拒绝登录。
     */
    public void assertNotLocked(String username) {
        if (!properties.isEnabled() || username == null || username.isBlank()) {
            return;
        }
        try {
            Long remainingSeconds = redisTemplate.getExpire(LOCK_KEY_PREFIX + username);
            if (remainingSeconds != null && remainingSeconds > 0) {
                long remainingMinutes = Math.max(1, (remainingSeconds + 59) / 60);
                throw new BizException(ResultCode.FORBIDDEN,
                        "账号已被锁定，请 " + remainingMinutes + " 分钟后重试");
            }
        } catch (BizException e) {
            throw e;
        } catch (DataAccessException e) {
            log.warn("登录锁定状态查询失败，放行本次登录：username={}, cause={}", username, e.getMessage());
        }
    }

    /**
     * 记录一次登录失败，达到上限时锁定账号。
     *
     * @param clientIp 客户端地址，仅用于审计日志
     */
    public void recordFailure(String username, String clientIp) {
        if (!properties.isEnabled() || username == null || username.isBlank()) {
            return;
        }
        try {
            Long locked = redisTemplate.execute(RECORD_FAILURE_SCRIPT,
                    List.of(FAIL_KEY_PREFIX + username, LOCK_KEY_PREFIX + username),
                    String.valueOf(Duration.ofMinutes(properties.getWindowMinutes()).toSeconds()),
                    String.valueOf(properties.getMaxFailures()),
                    String.valueOf(Duration.ofMinutes(properties.getLockMinutes()).toSeconds()));
            if (locked != null && locked == 1L) {
                log.warn("账号连续登录失败达 {} 次，锁定 {} 分钟：username={}, clientIp={}",
                        properties.getMaxFailures(), properties.getLockMinutes(), username, clientIp);
            }
        } catch (DataAccessException e) {
            log.warn("登录失败计数写入失败，放行本次登录：username={}, cause={}", username, e.getMessage());
        }
    }

    /**
     * 登录成功：清零失败计数。
     */
    public void reset(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        try {
            redisTemplate.delete(FAIL_KEY_PREFIX + username);
        } catch (DataAccessException e) {
            log.warn("登录失败计数清理失败：username={}, cause={}", username, e.getMessage());
        }
    }
}
