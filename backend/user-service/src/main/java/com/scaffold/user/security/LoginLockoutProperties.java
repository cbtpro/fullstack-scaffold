package com.scaffold.user.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 登录失败锁定配置，前缀 {@code login-lockout}。
 *
 * <p>策略为「同一账号在统计窗口内连续失败达到上限后锁定一段时间」，
 * 成功登录会清零计数。仅按账号维度锁定：按 IP 锁定在 NAT / 反向代理下
 * 会因共享出口地址而误伤整片用户，IP 维度改为只记录用于审计。
 */
@ConfigurationProperties(prefix = "login-lockout")
public class LoginLockoutProperties {

    /** 是否启用登录失败锁定 */
    private boolean enabled = true;

    /** 连续失败次数上限，达到即锁定账号 */
    private int maxFailures = 5;

    /** 失败计数统计窗口（分钟），窗口内未继续失败则计数自动过期 */
    private int windowMinutes = 15;

    /** 锁定时长（分钟） */
    private int lockMinutes = 15;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxFailures() {
        return maxFailures;
    }

    public void setMaxFailures(int maxFailures) {
        this.maxFailures = maxFailures;
    }

    public int getWindowMinutes() {
        return windowMinutes;
    }

    public void setWindowMinutes(int windowMinutes) {
        this.windowMinutes = windowMinutes;
    }

    public int getLockMinutes() {
        return lockMinutes;
    }

    public void setLockMinutes(int lockMinutes) {
        this.lockMinutes = lockMinutes;
    }
}
