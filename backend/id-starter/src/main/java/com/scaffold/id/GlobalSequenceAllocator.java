package com.scaffold.id;

import java.util.List;

/**
 * 全局严格序号分配器，为需要 {@code sort_seq} 的业务表分配唯一且单调递增的序号。
 *
 * <p>与 Snowflake 的区别：Snowflake 只承载生成时刻，不同节点之间存在时钟偏差，
 * 不能作为跨节点严格顺序；本分配器通过中心化的单调计数保证全局递增。
 *
 * <p>语义边界：序号代表「序号分配顺序」，不等于「事务提交顺序」。
 * 两个并发事务可能先拿到序号后提交，也可能先提交后拿到序号。
 * 如果业务要求严格按提交顺序排列，需要额外的事务协调机制，本接口不提供。
 */
public interface GlobalSequenceAllocator {

    /**
     * 分配下一个序号。
     *
     * @param key 业务键，通常对应一张表
     * @return 全局唯一且单调递增的序号
     */
    long next(String key);

    /**
     * 批量分配 count 个连续序号，返回列表升序。
     *
     * @throws IllegalArgumentException count 非正
     */
    List<Long> next(String key, int count);
}
