/**
 * 权限包装器集合。判定规则统一收敛在 @fs/auth 的 checkPermission：
 * - usePermission：hook，hasAuth / check / can 三种用法
 * - withPermission：HOC，适合列表行内复用的操作组件（模块级定义）
 * - PermissionGuard：单次使用的 JSX 片段
 * - PermissionFallback：disable 降级容器（Tooltip + inert）
 */
export { usePermission } from '@/hooks/usePermission';
export { withPermission } from './withPermission';
export type { WithPermissionConfig, WithPermissionProps } from './withPermission';
export { PermissionGuard } from './PermissionGuard';
export { PermissionFallback } from './PermissionFallback';
