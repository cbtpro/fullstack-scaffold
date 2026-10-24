/**
 * 权限判定 hook。订阅 zustand auth store，把当前用户的权限/角色
 * 绑定到 @fs/auth 的 {@link checkPermission} 组合判定上。
 *
 * - 无参调用：拿到 check / can 做按需判定
 * - 传入规则：hasAuth 为该规则的判定结果，规则支持权限码、角色与业务条件
 *
 * 组件只关心「是否放行」，判定规则统一收敛在纯函数里，避免多处口径不一致。
 */
import { useMemo } from 'react';
import { checkPermission, type PermissionRule } from '@fs/auth';
import { useAuth } from '@/store/auth';

export interface PermissionApi<T = unknown> {
  /** 调用时传入的规则的判定结果；未传规则时恒为 true */
  hasAuth: boolean;
  /** 按传入规则判定，用于过滤操作列表等场景 */
  check: (rule?: PermissionRule<T>) => boolean;
  /** 单个权限码判定的便捷封装 */
  can: (code: string) => boolean;
}

export function usePermission<T = unknown>(rule?: PermissionRule<T>): PermissionApi<T> {
  const user = useAuth((s) => s.user);
  return useMemo<PermissionApi<T>>(() => {
    const permissions = user?.permissions ?? [];
    const userRoles = user?.roles ?? [];
    const check = (r: PermissionRule<T> = {}) => checkPermission<T>({ permissions, userRoles, ...r });
    return {
      hasAuth: check(rule),
      check,
      can: (code) => check({ codes: [code] }),
    };
  }, [user, rule]);
}
