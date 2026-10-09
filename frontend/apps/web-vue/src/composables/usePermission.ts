/**
 * 权限判定 composable。读取当前 auth store 的用户 permissions/roles，
 * 暴露 hasPermission / hasRole 工具函数，与 @fs/auth 的纯函数解耦。
 */
import { computed } from 'vue';
import { hasAnyPermission, hasPermission, hasRole } from '@fs/auth';
import { useAuthStore } from '@/store/auth';

export function usePermission() {
  const auth = useAuthStore();
  const permissions = computed<string[]>(() => auth.user?.permissions ?? []);
  const roles = computed<string[]>(() => auth.user?.roles ?? []);

  const can = (perm: string) => hasPermission(permissions.value, perm);
  const is = (role: string) => hasRole(roles.value, role);
  const canAny = (perms: string[], logical: 'AND' | 'OR' = 'AND') =>
    hasAnyPermission(permissions.value, perms, logical);

  return { permissions, roles, can, is, canAny };
}
