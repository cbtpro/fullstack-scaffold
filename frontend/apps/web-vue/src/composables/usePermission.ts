/**
 * 权限判定 composable。读取当前 auth store 的用户权限/角色，
 * 绑定到 @fs/auth 的 checkPermission 组合判定上。
 *
 * - 无参调用：拿到 check / can 做按需判定
 * - 传入规则：hasAuth 为该规则的判定结果（响应式，权限未加载时保持追踪）
 */
import { computed } from 'vue';
import { checkPermission, type PermissionRule } from '@fs/auth';
import { useAuthStore } from '@/store/auth';

export function usePermission<T = unknown>(rule?: PermissionRule<T>) {
  const auth = useAuthStore();

  const check = (r: PermissionRule<T> = {}) =>
    checkPermission<T>({ permissions: auth.user?.permissions, userRoles: auth.user?.roles, ...r });

  /** 单个权限码判定的便捷封装 */
  const can = (code: string) => check({ codes: [code] });

  /** 调用时传入的规则的判定结果；未传规则时恒为 true */
  const hasAuth = computed(() => check(rule));

  return { hasAuth, check, can };
}
