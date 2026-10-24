package com.scaffold.user.log;

import com.scaffold.user.entity.OperationLogRecord;
import com.scaffold.user.mapper.OperationLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 操作日志落库。异步执行，写入失败只告警不抛出：
 * 审计失败不应反过来让已经成功的业务操作表现为失败。
 */
@Slf4j
@Component
public class OperationLogRecorder {

    private final OperationLogMapper operationLogMapper;

    public OperationLogRecorder(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Async("auditLogExecutor")
    public void record(OperationLogRecord record) {
        try {
            operationLogMapper.insert(record);
        } catch (RuntimeException e) {
            log.error("操作日志写入失败：module={}, action={}, uri={}, cause={}",
                    record.getModule(), record.getAction(), record.getUri(), e.getMessage());
        }
    }
}
