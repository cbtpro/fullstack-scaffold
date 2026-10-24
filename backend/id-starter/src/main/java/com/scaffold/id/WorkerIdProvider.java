package com.scaffold.id;

/**
 * Worker ID 提供者。发号器每次发号都向它取 workerId 并校验有效性，
 * 因此租约失效后可以立即停止发号，而不是等到进程重启。
 */
public interface WorkerIdProvider {

    /**
     * 当前节点持有的 workerId。
     *
     * @throws SnowflakeIdException 租约失效或尚未获取到 workerId
     */
    int getWorkerId();

    /**
     * 当前 workerId 是否仍然有效。租约模式下续约失败会置为 false。
     */
    default boolean isValid() {
        return true;
    }
}
