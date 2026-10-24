package com.scaffold.id;

/**
 * 时钟回拨处理策略。
 *
 * <p>回拨指 {@code clock.millis()} 返回的时间早于本节点上一次发号所用时间。
 * 三种策略的区别只在「回拨幅度超过容忍阈值」之后的行为：
 *
 * <ul>
 *   <li>{@link #WAIT_THEN_THROW}（默认）：先在容忍窗口内自旋等待时钟追平，
 *       超过阈值抛 {@link SnowflakeIdErrorCode#CLOCK_MOVED_BACKWARDS}。
 *       这是最保守的选择——宁可中断发号，也不产生可能与历史 ID 重复的号码。</li>
 *   <li>{@link #WAIT_FOREVER}：不限时等待时钟追平。适用于可以接受发号线程被长时间
 *       阻塞、但要求服务不中断的场景（例如时钟由 chrony 平滑校正，回拨会自行恢复）。
 *       风险是若时钟永久停在回拨位置，发号线程会挂死。</li>
 *   <li>{@link #THROW}：不做任何等待，检测到回拨立即抛异常。
 *       适用于上游有重试/降级、希望快速失败而不是静默阻塞的场景。</li>
 * </ul>
 *
 * <p>注意：本策略只处理「运行期」回拨。跨进程重启的回拨由
 * {@link TimestampBookkeeper} 持久化的水位负责检测，见
 * {@link DefaultSnowflakeIdGenerator}。
 */
public enum ClockRollbackStrategy {

    /** 容忍窗口内等待追平，超时抛异常（默认） */
    WAIT_THEN_THROW,

    /** 不限时等待时钟追平，不抛异常 */
    WAIT_FOREVER,

    /** 检测到回拨立即抛异常，不等待 */
    THROW
}
