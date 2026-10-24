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

export { getDeviceFingerprint } from './device';

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

/** 超级权限通配符，持有者视为拥有全部权限。 */
export const SUPER_PERMISSION = '*:*:*';

/**
 * 判定当前用户是否拥有指定权限编码。
 *
 * - 通配符 {@link SUPER_PERMISSION} 视为超级权限，匹配任意 value
 * - permissions 为空或不含目标编码返回 false
 *
 * 不依赖 store，便于在任意位置（组件、守卫、指令）按当前用户快照调用。
 */
export function hasPermission(permissions: string[] | undefined | null, value: string): boolean {
  if (!permissions || permissions.length === 0) return false;
  if (permissions.includes(SUPER_PERMISSION)) return true;
  return permissions.includes(value);
}

/**
 * 判定当前用户是否拥有指定角色编码。
 */
export function hasRole(roles: string[] | undefined | null, value: string): boolean {
  if (!roles || roles.length === 0) return false;
  return roles.includes(value);
}

/**
 * 批量权限判定：默认 AND（全部满足），传 {@code 'OR'} 改为任一满足。
 */
export function hasAnyPermission(
  permissions: string[] | undefined | null,
  values: string[],
  logical: 'AND' | 'OR' = 'AND',
): boolean {
  if (!values || values.length === 0) return true;
  if (!permissions || permissions.length === 0) return false;
  if (permissions.includes(SUPER_PERMISSION)) return true;
  return logical === 'AND'
    ? values.every((v) => permissions.includes(v))
    : values.some((v) => permissions.includes(v));
}

/** 权限规则：codes / roles / customFn 均可选，空数组与未指定同义（不加限制）。 */
export interface PermissionRule<T = unknown> {
  /** 需要的权限码，任一满足即通过 */
  codes?: string[];
  /** 需要的角色编码，仅当未指定非空 codes 时检查 */
  roles?: string[];
  /** 业务条件（如记录状态），通过权限/角色检查后执行，最终裁决 */
  customFn?: (userRoles: string[], extra?: T) => boolean;
  /** 传给 customFn 的业务数据 */
  extra?: T;
}

/** 组合判定的入参：用户侧权限/角色 + 权限规则。 */
export interface PermissionCheckParams<T = unknown> extends PermissionRule<T> {
  /** 用户权限码集合（后端下发，含超管通配符） */
  permissions?: string[] | null;
  /** 用户角色编码集合 */
  userRoles?: string[] | null;
}

/**
 * 组合权限判定（唯一规则源，Hook / 包装组件 / 过滤列表共用）：
 *
 * 1. 指定非空 codes 时按权限码判定（超管通配符直接通过），否则跳过
 * 2. 未指定非空 codes 且指定非空 roles 时按角色判定
 * 3. 两者都未指定表示没有权限限制
 * 4. customFn 为业务条件（如记录状态），权限/角色通过后仍需满足；
 *    超管可以绕过权限码检查，但不能绕过业务条件
 *
 * 界面显示策略与接口授权各管各的，后端必须继续独立鉴权。
 */
export function checkPermission<T = unknown>(params: PermissionCheckParams<T>): boolean {
  const { permissions, userRoles, codes, roles, customFn, extra } = params;

  let allowed = true;
  if (codes && codes.length > 0) {
    allowed = codes.some((code) => hasPermission(permissions, code));
  } else if (roles && roles.length > 0) {
    allowed = roles.some((role) => hasRole(userRoles, role));
  }
  if (!allowed) return false;

  return !customFn || customFn(userRoles ?? [], extra);
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
