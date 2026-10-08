/**
 * 登录态 store。基于 pinia 实现 {@link AuthStateStore} 契约，
 * 供 @fs/auth 的守卫/请求层与 Vue 组件订阅共用同一份状态。
 */
import { defineStore } from 'pinia';
import { getToken, type AuthState, type AuthStateStore } from '@fs/auth';

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: getToken(),
    user: null,
  }),
  actions: {
    set(patch: Partial<AuthState>) {
      if (patch.token !== undefined) this.token = patch.token;
      if (patch.user !== undefined) this.user = patch.user;
    },
    clear() {
      this.token = null;
      this.user = null;
    },
  },
});

/**
 * 框架无关适配器，对齐 @fs/auth 的 AuthStateStore。
 * 方法在 pinia install 后才会调用，懒加载 useAuthStore。
 */
export const authStore: AuthStateStore = {
  getState: () => {
    const s = useAuthStore();
    return { token: s.token, user: s.user };
  },
  setState: (patch) => useAuthStore().set(patch),
  reset: () => useAuthStore().clear(),
  subscribe: (listener) =>
    useAuthStore().$subscribe((_, state) => {
      listener({ token: state.token, user: state.user });
    }),
};
