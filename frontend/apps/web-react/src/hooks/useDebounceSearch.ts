/**
 * 防抖搜索调度 Hook。
 *
 * 统一管理所有查询入口（输入防抖 / 查询按钮 / 回车 / 重置），
 * 通过 timerRef + cancelSearch 共用一个计时器：
 * - scheduleSearch() — 防抖延迟（默认 300ms）后执行查询
 * - scheduleSearch({ force: true }) — 立即查询并取消待执行计时器
 * - cancelSearch() — 取消待执行计时器（compositionstart 时调用）
 *
 * 执行时通过 getValues() 现场取值，不保存快照，避免闭包过期。
 * onSearch 回调收到的始终是最新的查询条件。
 */
import { useCallback, useEffect, useRef } from 'react';

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
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // ref 持有最新函数引用，避免闭包过期
  const getValuesRef = useRef(getValues);
  const onSearchRef = useRef(onSearch);
  getValuesRef.current = getValues;
  onSearchRef.current = onSearch;

  const cancelSearch = useCallback(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  }, []);

  const scheduleSearch = useCallback(
    ({ force = false } = {}) => {
      cancelSearch();
      if (force) {
        onSearchRef.current(getValuesRef.current());
        return;
      }
      timerRef.current = setTimeout(() => {
        onSearchRef.current(getValuesRef.current());
      }, delay);
    },
    [cancelSearch, delay],
  );

  // 卸载时清理计时器
  useEffect(() => cancelSearch, [cancelSearch]);

  return { scheduleSearch, cancelSearch };
}
