/**
 * 权限按钮。无对应权限时直接返回 null，不渲染子节点；
 * 满足权限则透传 props 渲染 antd {@link Button}。
 *
 * 用法：
 * ```tsx
 * <AuthButton perm="user:create" type="primary" onClick={...}>新增用户</AuthButton>
 * ```
 *
 * 多权限（默认 AND）：
 * ```tsx
 * <AuthButton perms={['user:create', 'user:update']}>新增并编辑</AuthButton>
 * <AuthButton perms={['user:create', 'user:import']} logical="OR">任一即可</AuthButton>
 * ```
 *
 * 仅角色判定：
 * ```tsx
 * <AuthButton role="ADMIN">管理员按钮</AuthButton>
 * ```
 */
import { type ReactElement } from 'react';
import { Button, type ButtonProps } from 'antd';
import { usePermission } from '@/hooks/usePermission';

interface AuthButtonProps extends Omit<ButtonProps, 'role'> {
  /** 单个权限编码 */
  perm?: string;
  /** 多个权限编码，配合 logical */
  perms?: string[];
  /** 多权限逻辑：AND 全部满足 / OR 任一满足 */
  logical?: 'AND' | 'OR';
  /** 角色编码 */
  role?: string;
  /** 无权限时的回退节点，默认 null */
  fallback?: ReactElement | null;
}

export default function AuthButton({
  perm,
  perms,
  logical = 'AND',
  role,
  fallback = null,
  children,
  ...buttonProps
}: AuthButtonProps) {
  const { can, canAny, is } = usePermission();

  let allowed = true;
  if (perm) {
    allowed = can(perm) && allowed;
  }
  if (perms && perms.length > 0) {
    allowed = canAny(perms, logical) && allowed;
  }
  if (role) {
    allowed = is(role) && allowed;
  }

  if (!allowed) {
    return fallback;
  }
  return <Button {...buttonProps}>{children}</Button>;
}
