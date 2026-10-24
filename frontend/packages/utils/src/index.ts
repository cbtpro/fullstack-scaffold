/**
 * 业务无关工具函数。纯函数、零依赖，React 与 Vue 端共享。
 */

/** 深拷贝。优先用运行时原生 structuredClone，老环境退化为手写递归。 */
export function deepClone<T>(value: T): T {
  if (value === null || typeof value !== 'object') return value;
  if (typeof structuredClone === 'function') {
    try {
      return structuredClone(value);
    } catch {
      // structuredClone 不支持的类型（如函数、Symbol）退化为递归
    }
  }
  if (value instanceof Date) return new Date(value.getTime()) as unknown as T;
  if (value instanceof RegExp) return new RegExp(value.source, value.flags) as unknown as T;
  if (value instanceof Map) {
    const map = new Map();
    value.forEach((v, k) => map.set(k, deepClone(v)));
    return map as unknown as T;
  }
  if (value instanceof Set) {
    const set = new Set();
    value.forEach((v) => set.add(deepClone(v)));
    return set as unknown as T;
  }
  if (Array.isArray(value)) return value.map((item) => deepClone(item)) as unknown as T;
  const result: Record<string, unknown> = {};
  for (const key in value) {
    if (Object.prototype.hasOwnProperty.call(value, key)) {
      result[key] = deepClone((value as Record<string, unknown>)[key]);
    }
  }
  return result as T;
}

/**
 * 防抖。连续触发只执行最后一次。返回的函数带 cancel / flush。
 */
export interface DebouncedFunction<A extends unknown[]> {
  (...args: A): void;
  cancel(): void;
  flush(): void;
}

export function debounce<A extends unknown[]>(fn: (...args: A) => void, wait = 300): DebouncedFunction<A> {
  let timer: ReturnType<typeof setTimeout> | null = null;
  let lastArgs: A | null = null;

  const debounced = (...args: A) => {
    lastArgs = args;
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => {
      timer = null;
      if (lastArgs) fn(...lastArgs);
    }, wait);
  };
  debounced.cancel = () => {
    if (timer) clearTimeout(timer);
    timer = null;
    lastArgs = null;
  };
  debounced.flush = () => {
    if (timer) clearTimeout(timer);
    timer = null;
    if (lastArgs) fn(...lastArgs);
    lastArgs = null;
  };
  return debounced;
}

/**
 * 节流。固定间隔最多执行一次。带 cancel。
 */
export interface ThrottledFunction<A extends unknown[]> {
  (...args: A): void;
  cancel(): void;
}

export function throttle<A extends unknown[]>(fn: (...args: A) => void, wait = 300): ThrottledFunction<A> {
  let lastTime = 0;
  let timer: ReturnType<typeof setTimeout> | null = null;

  const throttled = (...args: A) => {
    const now = Date.now();
    const remaining = wait - (now - lastTime);
    if (remaining <= 0) {
      if (timer) {
        clearTimeout(timer);
        timer = null;
      }
      lastTime = now;
      fn(...args);
    } else if (!timer) {
      timer = setTimeout(() => {
        lastTime = Date.now();
        timer = null;
        fn(...args);
      }, remaining);
    }
  };
  throttled.cancel = () => {
    lastTime = 0;
    if (timer) {
      clearTimeout(timer);
      timer = null;
    }
  };
  return throttled;
}

/** 判断值是否为空（null/undefined/空字符串/空数组/空对象）。 */
export function isEmpty(value: unknown): boolean {
  if (value === null || value === undefined || value === '') return true;
  if (Array.isArray(value)) return value.length === 0;
  if (typeof value === 'object') return Object.keys(value as object).length === 0;
  return false;
}

/** 生成唯一 id。优先用 crypto.randomUUID，退化用时间戳+随机数。 */
export function uuid(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

const DATE_TIME_FORMAT = 'YYYY-MM-DD HH:mm:ss';
const DATE_FORMAT = 'YYYY-MM-DD';

function pad(n: number): string {
  return n < 10 ? `0${n}` : String(n);
}

/**
 * 格式化日期。支持时间戳、字符串、Date。token 用 YYYY MM DD HH mm ss 占位。
 */
export function formatDateTime(input: Date | string | number, pattern: string = DATE_TIME_FORMAT): string {
  const date = input instanceof Date ? input : new Date(normalizeDateTime(input));
  if (Number.isNaN(date.getTime())) return '';
  const map: Record<string, string> = {
    YYYY: String(date.getFullYear()),
    MM: pad(date.getMonth() + 1),
    DD: pad(date.getDate()),
    HH: pad(date.getHours()),
    mm: pad(date.getMinutes()),
    ss: pad(date.getSeconds()),
  };
  return pattern.replace(/YYYY|MM|DD|HH|mm|ss/g, (token) => map[token]);
}

export function formatDate(input: Date | string | number): string {
  return formatDateTime(input, DATE_FORMAT);
}

/**
 * 兼容后端 yyyy-MM-dd HH:mm:ss 的日期时间串。
 * Safari 不解析以空格分隔的日期与时间，统一改为 ISO 的 T 分隔，
 * 两端与所有浏览器都能正确取值。
 */
function normalizeDateTime(input: Date | string | number): Date | string | number {
  if (typeof input !== 'string') return input;
  return /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/.test(input) ? input.replace(' ', 'T') : input;
}

/** 文件大小格式化。 */
export function formatFileSize(bytes: number, digits = 2): string {
  if (!Number.isFinite(bytes) || bytes < 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB'];
  let index = 0;
  let size = bytes;
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024;
    index++;
  }
  return `${size.toFixed(digits)} ${units[index]}`;
}

/** 千分位金额格式化。 */
export function formatMoney(amount: number | string, digits = 2): string {
  const num = typeof amount === 'string' ? Number(amount) : amount;
  if (!Number.isFinite(num)) return '0.00';
  return num.toFixed(digits).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}
