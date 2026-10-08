/**
 * 鉴权抽象层。仅依赖 {@link @fs/types}，绝不反向依赖 @fs/request。
 *
 * 职责：
 * - token 存取（存储可替换，默认 localStorage，SSR/小程序环境退化内存）
 * - 登录态 store 契约（由 React zustand / Vue pinia 实现）
 * - 框架无关路由守卫示例
 *
 * token 注入请求头由 @fs/request 调用本包 getToken 完成，避免循环依赖。
 */
import type { User } from '@fs/types';

/** 默认 token 存储键。 */
export const DEFAULT_TOKEN_KEY = 'fs_token';

/** token 存储抽象。业务可自定义实现并 setTokenStorage 注入。 */
export interface TokenStorage {
  get(key: string): string | null;
  set(key: string, token: string): void;
  remove(key: string): void;
}

class MemoryTokenStorage implements TokenStorage {
  private store = new Map<string, string>();
  get(key: string): string | null {
    return this.store.get(key) ?? null;
  }
  set(key: string, token: string): void {
    this.store.set(key, token);
  }
  remove(key: string): void {
    this.store.delete(key);
  }
}

class BrowserTokenStorage implements TokenStorage {
  get(key: string): string | null {
    return localStorage.getItem(key);
  }
  set(key: string, token: string): void {
    localStorage.setItem(key, token);
  }
  remove(key: string): void {
    localStorage.removeItem(key);
  }
}

function resolveDefaultStorage(): TokenStorage {
  try {
    if (typeof localStorage !== 'undefined') return new BrowserTokenStorage();
  } catch {
    // localStorage 访问异常（隐私模式）退化为内存
  }
  return new MemoryTokenStorage();
}

let storage: TokenStorage = resolveDefaultStorage();
let tokenKey = DEFAULT_TOKEN_KEY;

/** 替换 token 存储实现（如 Cookie、uni.storage）。 */
export function setTokenStorage(next: TokenStorage): void {
  storage = next;
}

/** 自定义 token 存储键名。 */
export function setTokenKey(key: string): void {
  tokenKey = key;
}

export function getToken(): string | null {
  return storage.get(tokenKey);
}

export function setToken(token: string): void {
  storage.set(tokenKey, token);
}

export function clearToken(): void {
  storage.remove(tokenKey);
}

/** 登录态快照。 */
export interface AuthState {
  token: string | null;
  user: User | null;
}

export const initialAuthState: AuthState = {
  token: null,
  user: null,
};

/**
 * 登录态 store 契约。React 端用 zustand 实现，Vue 端用 pinia 实现，
 * 均实现此接口即可被守卫与请求层复用。
 */
export interface AuthStateStore {
  getState(): AuthState;
  setState(patch: Partial<AuthState>): void;
  reset(): void;
  /** 订阅状态变化，返回取消订阅函数。 */
  subscribe(listener: (state: AuthState) => void): () => void;
}

/** 便捷判定是否已登录。 */
export function isAuthenticated(store: AuthStateStore): boolean {
  return !!store.getState().token;
}

/**
 * 框架无关路由守卫工厂。返回守卫函数：输入目标路径，返回 true 放行，
 * 返回字符串则重定向到该路径。React/Vue 路由各自适配。
 */
export interface GuardOptions {
  loginPath: string;
  /** 白名单路径，无需登录即可访问。 */
  whitelist?: string[];
  /** 自定义判定，默认 isAuthenticated。 */
  isAllowed?: (store: AuthStateStore) => boolean;
}

export function createAuthGuard(store: AuthStateStore, options: GuardOptions) {
  const whitelist = options.whitelist ?? [options.loginPath];
  return (toPath: string): boolean | string => {
    if (whitelist.includes(toPath)) return true;
    const allowed = options.isAllowed ? options.isAllowed(store) : isAuthenticated(store);
    return allowed ? true : options.loginPath;
  };
}
