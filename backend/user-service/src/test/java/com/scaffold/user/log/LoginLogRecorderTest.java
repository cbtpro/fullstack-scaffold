package com.scaffold.user.log;

import com.scaffold.user.entity.LoginLogRecord;
import com.scaffold.user.mapper.LoginLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录日志的采集必须落在请求线程上。
 *
 * <p>servlet 容器在响应结束后回收 request，异步线程再去读会抛
 * "request object has been recycled"，异常打断整段逻辑导致记录写不进去。
 * 这里用可控的 Executor 把落库时机推迟到 request 被回收之后，固化该约束。
 */
class LoginLogRecorderTest {

    /** 容器回收请求后的异常信息（Undertow 原文） */
    private static final String RECYCLED = "The request object has been recycled and is no longer associated with this facade";

    @Test
    @DisplayName("请求先被回收、异步线程再落库：字段已在采集阶段取好，记录仍写入")
    void keepsRecordWhenRequestRecycledBeforePersist() {
        LoginLogMapper mapper = mock(LoginLogMapper.class);
        AtomicReference<Runnable> pending = new AtomicReference<>();
        // 手动控制异步任务何时执行，以复现「容器先回收请求、异步线程后落库」的时序
        Executor executor = pending::set;

        LoginLogRecorder recorder = new LoginLogRecorder(mapper, executor, 1);

        AtomicBoolean recycled = new AtomicBoolean(false);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(anyString())).thenAnswer(invocation -> {
            if (recycled.get()) {
                throw new IllegalStateException(RECYCLED);
            }
            return "X-Forwarded-For".equals(invocation.getArgument(0))
                    ? "203.0.113.9, 10.0.0.1" : "curl/8.0.1";
        });
        when(request.getRemoteAddr()).thenAnswer(invocation -> {
            if (recycled.get()) {
                throw new IllegalStateException(RECYCLED);
            }
            return "127.0.0.1";
        });

        recorder.record("tester", LoginLogRecorder.EVENT_LOGIN, true, "登录成功", request);

        recycled.set(true);
        pending.get().run();

        ArgumentCaptor<LoginLogRecord> captor = ArgumentCaptor.forClass(LoginLogRecord.class);
        verify(mapper).insert(captor.capture());
        LoginLogRecord saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("tester");
        assertThat(saved.getEventType()).isEqualTo(LoginLogRecorder.EVENT_LOGIN);
        assertThat(saved.getSuccess()).isTrue();
        // 可信代理跳数为 1：取最后一段，客户端伪造的前段不采信
        assertThat(saved.getClientIp()).isEqualTo("10.0.0.1");
        assertThat(saved.getUserAgent()).isEqualTo("curl/8.0.1");
    }

    @Test
    @DisplayName("请求为空时地址与 User-Agent 留空，仍完成落库")
    void recordsWithNullRequest() {
        LoginLogMapper mapper = mock(LoginLogMapper.class);
        LoginLogRecorder recorder = new LoginLogRecorder(mapper, Runnable::run, 1);

        recorder.record("tester", LoginLogRecorder.EVENT_LOGOUT, true, "退出登录", null);

        ArgumentCaptor<LoginLogRecord> captor = ArgumentCaptor.forClass(LoginLogRecord.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getClientIp()).isNull();
        assertThat(captor.getValue().getUserAgent()).isNull();
    }

    @Test
    @DisplayName("落库失败只告警：不把异常抛回调用方，登录结果不受影响")
    void swallowsPersistFailure() {
        LoginLogMapper mapper = mock(LoginLogMapper.class);
        when(mapper.insert(any(LoginLogRecord.class))).thenThrow(new IllegalStateException("库不可用"));
        LoginLogRecorder recorder = new LoginLogRecorder(mapper, Runnable::run, 1);

        recorder.record("tester", LoginLogRecorder.EVENT_LOGIN, false, "密码错误", null);

        verify(mapper).insert(any(LoginLogRecord.class));
    }
}
