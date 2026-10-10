/**
 * 防抖搜索调度组合式函数。
 *
 * 统一管理所有查询入口（输入防抖 / 查询按钮 / 回车 / 重置），
 * 通过 timer + cancelSearch 共用一个计时器：
 * - scheduleSearch() — 防抖延迟（默认 300ms）后执行查询
 * - scheduleSearch({ force: true }) — 立即查询并取消待执行计时器
 * - cancelSearch() — 取消待执行计时器（compositionstart 时调用）
 *
 * 执行时通过 getValues() 现场取值，不保存快照。
 * Vue 的响应式系统天然避免闭包过期，getValues 读 ref/reactive 始终拿最新值。
 */
import { onUnmounted } from 'vue';

export interface UseDebounceSearchOptions<T> {
  /** 获取当前查询条件（执行时取值，不保存快照） */
  getValues: () => T;
  /** 查询回调 */
  onSearch: (values: T) => void;
  /** 防抖延迟毫秒，默认 300 */
  delay?: number;
}

export function useDebounceSearch<T extends Record<string, unknown>>({
  getValues,
  onSearch,
  delay = 300,
}: UseDebounceSearchOptions<T>) {
  let timer: ReturnType<typeof setTimeout> | null = null;

  const cancelSearch = () => {
    if (timer) {
      clearTimeout(timer);
      timer = null;
    }
  };

  const scheduleSearch = ({ force = false } = {}) => {
    cancelSearch();
    if (force) {
      onSearch(getValues());
      return;
    }
    timer = setTimeout(() => {
      onSearch(getValues());
    }, delay);
  };

  onUnmounted(cancelSearch);

  return { scheduleSearch, cancelSearch };
}
