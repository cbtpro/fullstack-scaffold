package com.scaffold.user.log;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 审计日志异步写入的线程池（操作日志、登录日志共用）。
 *
 * <p>日志落库不参与业务事务：异步写避免审计开销拖慢接口响应，也不因业务回滚而丢日志
 * ——审计要记录的正是「尝试过什么」，包括失败的那次。
 *
 * <p>拒绝策略取 {@code CallerRunsPolicy}：队列打满时由调用线程直接写，
 * 宁可让请求慢一点，也不静默丢弃审计记录。
 */
@Configuration
@EnableAsync
public class AuditAsyncConfig {

    @Bean("auditLogExecutor")
    public Executor auditLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("audit-log-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
