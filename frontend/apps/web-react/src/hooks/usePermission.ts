/**
 * 权限判定 hook。订阅 zustand auth store，把当前用户的 permissions/roles
 * 绑定到 {@link hasPermission} / {@link hasRole}，组件内按需调用即可触发渲染。
 *
 * 与 @fs/auth 的纯函数解耦：组件只关心「是否放行」，不关心 user 数据结构。
 */
import { useMemo } from 'react';
import { hasAnyPermission, hasPermission, hasRole } from '@fs/auth';
import { useAuth } from '@/store/auth';

export interface PermissionApi {
  /** 当前用户权限编码集合 */
  permissions: string[];
  /** 当前用户角色编码集合 */
  roles: string[];
  /** 是否拥有指定权限 */
  can: (perm: string) => boolean;
  /** 是否拥有指定角色 */
  is: (role: string) => boolean;
  /** 多权限批量判定，默认 AND */
  canAny: (perms: string[], logical?: 'AND' | 'OR') => boolean;
}

export function usePermission(): PermissionApi {
  const user = useAuth((s) => s.user);
  return useMemo<PermissionApi>(() => {
    const permissions = user?.permissions ?? [];
    const roles = user?.roles ?? [];
    return {
      permissions,
      roles,
      can: (perm: string) => hasPermission(permissions, perm),
      is: (role: string) => hasRole(roles, role),
      canAny: (perms: string[], logical: 'AND' | 'OR' = 'AND') =>
        hasAnyPermission(permissions, perms, logical),
    };
  }, [user]);
}
