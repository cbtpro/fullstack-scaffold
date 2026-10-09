/**
 * 权限守卫组件：适合只出现一次的 JSX 片段。
 * 规则通过则渲染 children，否则按 fallback 隐藏或灰置。
 * 判定走与 withPermission 相同的 checkPermission 纯函数，口径一致。
 */
import type { ReactNode } from 'react';
import { usePermission } from '@/hooks/usePermission';
import { PermissionFallback } from './PermissionFallback';
import type { WithPermissionConfig } from './withPermission';

export interface PermissionGuardProps<T = unknown> extends WithPermissionConfig<T> {
  children: ReactNode;
}

export function PermissionGuard<T>({
  children,
  fallback = 'hide',
  tooltip,
  ...rule
}: PermissionGuardProps<T>) {
  const { check } = usePermission<T>();
  if (!check(rule)) {
    if (fallback === 'disable') {
      return <PermissionFallback tooltip={tooltip}>{children}</PermissionFallback>;
    }
    return null;
  }
  return <>{children}</>;
}
