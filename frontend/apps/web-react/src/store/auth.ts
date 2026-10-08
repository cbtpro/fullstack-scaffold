/**
 * 登录态 store。基于 zustand 实现 {@link AuthStateStore} 契约，
 * 供 @fs/auth 的守卫/请求层与 React 组件订阅共用同一份状态。
 *
 * - `useAuth` 给组件订阅（selector 触发渲染）
 * - `authStore` 给框架无关代码（createAuthGuard / 请求拦截器）调用
 */
import { create } from 'zustand';
import { getToken, type AuthState, type AuthStateStore } from '@fs/auth';

interface AuthStoreState extends AuthState {
  set(patch: Partial<AuthState>): void;
  clear(): void;
}

const useAuthStore = create<AuthStoreState>()((set) => ({
  token: getToken(),
  user: null,
  set: (patch) => set(patch),
  clear: () => set({ token: null, user: null }),
}));

/** 组件订阅入口。 */
export const useAuth = useAuthStore;

/** 框架无关适配器，对齐 @fs/auth 的 AuthStateStore 接口。 */
export const authStore: AuthStateStore = {
  getState: () => {
    const { token, user } = useAuthStore.getState();
    return { token, user };
  },
  setState: (patch) => useAuthStore.getState().set(patch),
  reset: () => useAuthStore.getState().clear(),
  subscribe: (listener) =>
    useAuthStore.subscribe((state, prev) => {
      if (state.token !== prev.token || state.user !== prev.user) {
        listener({ token: state.token, user: state.user });
      }
    }),
};
