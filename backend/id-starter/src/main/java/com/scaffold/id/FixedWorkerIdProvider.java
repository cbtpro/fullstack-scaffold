package com.scaffold.id;

/**
 * 固定 Worker ID 提供者。
 *
 * <p>从 {@code scaffold.id.worker-id} 读取，不做任何集群协调：
 * 部署方必须自行保证各节点 workerId 不冲突。
 * 本模式不具备自动冲突检测与自动协调能力。
 */
public final class FixedWorkerIdProvider implements WorkerIdProvider {

    private final int workerId;

    /**
     * @param workerId 节点标识，取值 [0, 2^workerIdBits-1]
     * @throws SnowflakeIdException workerId 为负
     */
    public FixedWorkerIdProvider(int workerId) {
        if (workerId < 0) {
            throw new SnowflakeIdException(SnowflakeIdErrorCode.INVALID_WORKER_ID,
                    "workerId 不能为负数: " + workerId);
        }
        this.workerId = workerId;
    }

    @Override
    public int getWorkerId() {
        return workerId;
    }
}
