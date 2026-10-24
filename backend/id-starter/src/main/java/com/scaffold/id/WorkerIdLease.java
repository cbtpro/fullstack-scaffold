package com.scaffold.id;

/**
 * 分布式 Worker ID 租约。扩展点，由部署环境提供实现。
 *
 * <p>本项目已提供基于数据库的实现（见 {@code JdbcWorkerIdLease}），
 * 因为数据库是本系统已有的基础设施，不额外引入注册中心。
 * 若部署环境已有 Redis / Nacos / Zookeeper，实现本接口替换即可。
 */
public interface WorkerIdLease {

    /**
     * 获取或续持一个 workerId。
     *
     * <p>同一 {@code nodeKey} 重复调用应返回同一个 workerId（节点重启复用安全）。
     * 若该 workerId 已被其他 nodeKey 持有且租约未过期，返回其他可用 workerId；
     * 全部占满则抛 {@link SnowflakeIdErrorCode#WORKER_ID_UNAVAILABLE}。
     *
     * @param nodeKey     节点标识
     * @param maxWorkerId workerId 上界（含）
     */
    int acquire(String nodeKey, int maxWorkerId);

    /**
     * 续约。
     *
     * @return true 续约成功；false 表示租约已被其他节点抢占或已过期
     */
    boolean renew(int workerId, String nodeKey);

    /** 主动释放租约，进程正常退出时调用。 */
    void release(int workerId, String nodeKey);
}
