/**
 * withPermission 权限包装 HOC：给任意组件套上权限判定，替代专用的
 * 权限按钮外壳——业务组件自己决定渲染什么，包装层只负责「放不放行」。
 *
 * - config 为静态规则；渲染时可用同名 props 覆盖 codes/roles/customFn/extra，
 *   便于按行数据等运行时上下文组合
 * - fallback="hide"（默认）不渲染；"disable" 灰置并给出提示
 * - HOC 必须在模块级定义，避免每次渲染创建新的组件类型导致子树反复卸载
 */
import type { ComponentType, ReactNode } from 'react';
import type { PermissionRule } from '@fs/auth';
import { usePermission } from '@/hooks/usePermission';
import { PermissionFallback } from './PermissionFallback';

export interface WithPermissionConfig<T = unknown> extends PermissionRule<T> {
  /** 无权限时的表现：hide 不渲染，disable 灰置并解释原因 */
  fallback?: 'hide' | 'disable';
  /** disable 模式的提示文案，缺省为「暂无操作权限」 */
  tooltip?: ReactNode;
}

/** 运行时覆盖 props：与静态 config 同名时以 props 为准。 */
export type WithPermissionProps<T = unknown> = PermissionRule<T>;

export function withPermission<T = unknown>(config: WithPermissionConfig<T> = {}) {
  const { fallback = 'hide', tooltip, ...rule } = config;
  return function wrap<P extends { children?: ReactNode }>(Wrapped: ComponentType<P>) {
    function WithPermission(props: P & WithPermissionProps<T> & { tooltip?: ReactNode }) {
      const { check } = usePermission<T>();
      const { codes, roles, customFn, extra, tooltip: tooltipProp, ...rest } = props;
      const allowed = check({
        codes: codes ?? rule?.codes,
        roles: roles ?? rule?.roles,
        customFn: customFn ?? rule?.customFn,
        extra: (extra ?? rule?.extra) as T | undefined,
      });
      if (!allowed) {
        if (fallback === 'disable') {
          return (
            <PermissionFallback tooltip={tooltipProp ?? tooltip}>
              {props.children as ReactNode}
            </PermissionFallback>
          );
        }
        return null;
      }
      return <Wrapped {...(rest as P)} />;
    }
    WithPermission.displayName = `WithPermission(${Wrapped.displayName ?? Wrapped.name ?? 'Component'})`;
    return WithPermission;
  };
}
