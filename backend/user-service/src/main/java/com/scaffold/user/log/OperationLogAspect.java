package com.scaffold.user.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaffold.auth.AuthManager;
import com.scaffold.auth.AuthProperties;
import com.scaffold.auth.model.UserInfo;
import com.scaffold.common.exception.BizException;
import com.scaffold.common.log.OperationLog;
import com.scaffold.common.web.ClientIpResolver;
import com.scaffold.user.entity.OperationLogRecord;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 操作日志切面：采集 {@link OperationLog} 标注方法的执行结果并异步落库。
 *
 * <p>优先级高于鉴权切面（{@code PermissionAspect} 未显式指定顺序，即最低优先级），
 * 使其包在鉴权外层：无权限被拒绝的调用同样留下审计记录，这正是审计最该关注的场景之一。
 *
 * <p>记录动作不改变业务结果：参数序列化、身份解析、落库全部吞掉自身异常，
 * 任何一环失败都只告警，不影响接口返回。
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class OperationLogAspect {

    /** 网关鉴权成功后注入的可信用户标识头 */
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USERNAME_HEADER = "X-Username";

    /** 参数快照长度上限，避免大请求体把日志表撑大 */
    private static final int MAX_PARAMS_LENGTH = 1000;
    /** 失败原因长度上限 */
    private static final int MAX_ERROR_LENGTH = 500;

    /**
     * 敏感字段值替换：键名含 password/passwd/token/secret/credential 时，值一律写 {"***"}。
     * 参数只以 JSON 字符串形态存在，逐字段脱敏比按类型判断更不容易漏。
     */
    private static final Pattern SENSITIVE_VALUE = Pattern.compile(
            "(\"[A-Za-z0-9_]*(?:password|passwd|token|secret|credential)[A-Za-z0-9_]*\"\\s*:\\s*)"
                    + "(\"(?:\\\\.|[^\"\\\\])*\"|\\d+|true|false|null)",
            Pattern.CASE_INSENSITIVE);

    private final OperationLogRecorder recorder;
    private final ObjectMapper objectMapper;
    private final AuthManager authManager;
    private final AuthProperties authProperties;
    private final int trustedProxyCount;

    public OperationLogAspect(OperationLogRecorder recorder,
                              ObjectMapper objectMapper,
                              AuthManager authManager,
                              AuthProperties authProperties,
                              @Value("${scaffold.client-ip.trusted-proxy-count:1}") int trustedProxyCount) {
        this.recorder = recorder;
        this.objectMapper = objectMapper;
        this.authManager = authManager;
        this.authProperties = authProperties;
        this.trustedProxyCount = trustedProxyCount;
    }

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint pjp, OperationLog operationLog) throws Throwable {
        long start = System.currentTimeMillis();
        Throwable failure = null;
        try {
            return pjp.proceed();
        } catch (Throwable t) {
            failure = t;
            throw t;
        } finally {
            try {
                OperationLogRecord record = buildRecord(pjp, operationLog, failure, System.currentTimeMillis() - start);
                recorder.record(record);
            } catch (RuntimeException e) {
                log.error("操作日志采集失败：action={}, cause={}", operationLog.action(), e.getMessage());
            }
        }
    }

    private OperationLogRecord buildRecord(ProceedingJoinPoint pjp, OperationLog operationLog,
                                           Throwable failure, long durationMs) {
        HttpServletRequest request = currentRequest();

        OperationLogRecord record = new OperationLogRecord();
        record.setModule(operationLog.module());
        record.setAction(operationLog.action());
        record.setDescription(operationLog.description().isEmpty()
                ? pjp.getSignature().toShortString()
                : operationLog.description());
        record.setSuccess(failure == null);
        record.setDurationMs(durationMs);

        if (failure != null) {
            String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            record.setErrorMessage(truncate(message, MAX_ERROR_LENGTH));
        }

        if (request != null) {
            record.setHttpMethod(request.getMethod());
            record.setUri(request.getRequestURI());
            record.setParams(serializeParams(pjp.getArgs(), operationLog.recordParams()));
            record.setClientIp(ClientIpResolver.resolve(request.getHeader("X-Forwarded-For"),
                    request.getRemoteAddr(), trustedProxyCount));
        }

        Operator operator = resolveOperator(request);
        if (operator != null) {
            record.setOperatorId(operator.id());
            record.setOperatorName(operator.name());
        }
        return record;
    }

    /**
     * 当前操作人。优先取网关注入的可信头；直连（未经网关）时回退到按 token 解析。
     */
    private Operator resolveOperator(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String userId = request.getHeader(USER_ID_HEADER);
        if (userId != null && !userId.isBlank()) {
            return new Operator(parseLong(userId), request.getHeader(USERNAME_HEADER));
        }
        try {
            String token = authManager.parseToken(request.getHeader(authProperties.getTokenHeader()));
            if (token == null) {
                return null;
            }
            UserInfo user = authManager.getLoginUser(token);
            return user == null ? null : new Operator(user.getId(), user.getUsername());
        } catch (BizException e) {
            return null;
        }
    }

    /**
     * 参数快照：剔除容器对象与文件上传，序列化后脱敏并截断。
     */
    private String serializeParams(Object[] args, boolean recordParams) {
        if (!recordParams || args == null || args.length == 0) {
            return null;
        }
        List<Object> payload = new ArrayList<>(args.length);
        for (Object arg : args) {
            if (arg instanceof ServletRequest || arg instanceof ServletResponse || arg instanceof MultipartFile) {
                continue;
            }
            payload.add(arg);
        }
        if (payload.isEmpty()) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            return truncate(SENSITIVE_VALUE.matcher(json).replaceAll("$1\"***\""), MAX_PARAMS_LENGTH);
        } catch (Exception e) {
            // 参数含无法序列化的类型时放弃记录参数，不影响其余字段
            log.debug("操作日志参数序列化失败，跳过参数记录：{}", e.getMessage());
            return null;
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "...(截断)";
    }

    /** 操作人标识 */
    private record Operator(Long id, String name) {
    }
}
