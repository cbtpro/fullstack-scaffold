package com.scaffold.user.log;

import com.scaffold.common.web.ClientIpResolver;
import com.scaffold.user.entity.LoginLogRecord;
import com.scaffold.user.mapper.LoginLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/**
 * 登录日志落库。异步执行，写入失败只告警不抛出：
 * 审计失败不应反过来改变登录本身的结果。
 *
 * <p>客户端地址与 User-Agent 在此采集，调用方只需给出账号、事件类型与结果，
 * 避免每个登录入口各写一遍字段拼装。
 *
 * <p>采集与落库分处两个线程：servlet 容器在响应结束后会回收 request，异步线程再去读
 * 会抛 "request object has been recycled"，异常打断整段逻辑，记录连插入都到不了。
 * 因此请求相关字段必须在调用线程上取好，再把成形的记录交给 {@code auditLogExecutor}。
 */
@Slf4j
@Component
public class LoginLogRecorder {

    /** 事件类型：登录 */
    public static final String EVENT_LOGIN = "LOGIN";
    /** 事件类型：登出 */
    public static final String EVENT_LOGOUT = "LOGOUT";

    /** User-Agent 长度上限，避免超长头撑大日志表 */
    private static final int MAX_USER_AGENT_LENGTH = 500;
    /** 提示信息长度上限 */
    private static final int MAX_MESSAGE_LENGTH = 255;

    private final LoginLogMapper loginLogMapper;
    private final Executor auditLogExecutor;
    private final int trustedProxyCount;

    public LoginLogRecorder(LoginLogMapper loginLogMapper,
                            @Qualifier("auditLogExecutor") Executor auditLogExecutor,
                            @Value("${scaffold.client-ip.trusted-proxy-count:1}") int trustedProxyCount) {
        this.loginLogMapper = loginLogMapper;
        this.auditLogExecutor = auditLogExecutor;
        this.trustedProxyCount = trustedProxyCount;
    }

    /**
     * 记录一次登录/登出事件。
     *
     * @param eventType LOGIN 或 LOGOUT
     * @param request   当前请求，用于采集地址与 User-Agent；为空时这两个字段留空
     */
    public void record(String username, String eventType, boolean success, String message,
                       HttpServletRequest request) {
        LoginLogRecord record = new LoginLogRecord();
        record.setUsername(username);
        record.setEventType(eventType);
        record.setSuccess(success);
        record.setMessage(truncate(message, MAX_MESSAGE_LENGTH));
        if (request != null) {
            record.setClientIp(ClientIpResolver.resolve(request.getHeader("X-Forwarded-For"),
                    request.getRemoteAddr(), trustedProxyCount));
            record.setUserAgent(truncate(request.getHeader("User-Agent"), MAX_USER_AGENT_LENGTH));
        }
        // 直接投递到审计线程池而非用 @Async：本方法承担采集职责，需在调用线程完成，
        // 异步注解会连采集一起挪到异步线程，反而踩到请求回收。
        auditLogExecutor.execute(() -> persist(record));
    }

    private void persist(LoginLogRecord record) {
        try {
            loginLogMapper.insert(record);
        } catch (RuntimeException e) {
            log.error("登录日志写入失败：username={}, eventType={}, cause={}",
                    record.getUsername(), record.getEventType(), e.getMessage());
        }
    }

    private String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }
}
