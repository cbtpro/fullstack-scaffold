/**
 * v-permission 指令。无对应权限时移除元素。
 *
 * 用法：
 * - `v-permission="'user:create'"` 单个权限
 * - `v-permission="['user:create', 'user:import']"` 多个权限（默认 AND）
 * - `v-permission.and="[...]"` / `v-permission.or="..."` 切换逻辑
 * - `v-permission.role="'ADMIN'"` 按角色判定
 *
 * 实现采用函数式权限判定，结合 @fs/auth 的 hasPermission/hasRole；
 * 无权限时通过注释占位移除元素，避免遗留 DOM。
 */
import type { Directive, DirectiveBinding } from 'vue';
import { hasAnyPermission, hasPermission, hasRole } from '@fs/auth';
import { useAuthStore } from '@/store/auth';

interface PermissionModifiers {
  and?: boolean;
  or?: boolean;
  role?: boolean;
}

function evaluate(binding: DirectiveBinding, perms: string[], roles: string[]): boolean {
  const value = binding.value;
  const mods = binding.modifiers as PermissionModifiers;
  if (mods.role && typeof value === 'string') {
    return hasRole(roles, value);
  }
  if (typeof value === 'string') {
    return hasPermission(perms, value);
  }
  if (Array.isArray(value)) {
    return hasAnyPermission(perms, value, mods.or ? 'OR' : 'AND');
  }
  return true;
}

export const permission: Directive<HTMLElement & { __permPlaceholder?: Comment | null }> = {
  mounted(el, binding) {
    if (!evaluatePermission(binding)) {
      const placeholder = document.createComment('v-permission');
      el.__permPlaceholder = placeholder;
      el.replaceWith(placeholder);
    }
  },
  updated(el, binding) {
    // 简化处理：权限变化场景较少，这里只保证挂载时一次性判定
    // 如需响应权限变化重新渲染，请在组件内用 v-if + usePermission() 替代
  },
  beforeUnmount(el) {
    if (el.__permPlaceholder) {
      el.__permPlaceholder = null;
    }
  },
};

function evaluatePermission(binding: DirectiveBinding): boolean {
  const auth = useAuthStore();
  const perms = auth.user?.permissions ?? [];
  const roles = auth.user?.roles ?? [];
  return evaluate(binding, perms, roles);
}

declare module 'vue' {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  interface ComponentCustomProperties {
    vPermission: any;
  }
}
