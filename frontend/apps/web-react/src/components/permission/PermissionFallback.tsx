/**
 * 无权限降级容器（fallback="disable"）。
 *
 * 禁用不能只靠 pointer-events：子元素仍可能通过 Tab 聚焦后用 Enter/空格触发。
 * 结构上分两层：
 * - 外层 span 承接 Tooltip 提示（hover + focus），拦截冒泡点击并标记 aria-disabled
 * - 内层 span 设置 inert，把后代从焦点顺序与无障碍树中移除
 */
import type { ReactNode } from 'react';
import { Tooltip } from 'antd';
import { useTranslation } from 'react-i18next';

export interface PermissionFallbackProps {
  /** 提示文案，缺省为「暂无操作权限」 */
  tooltip?: ReactNode;
  children: ReactNode;
}

export function PermissionFallback({ tooltip, children }: PermissionFallbackProps) {
  const { t } = useTranslation();
  return (
    <Tooltip title={tooltip ?? t('common.noPermission')} trigger={['hover', 'focus']}>
      <span
        aria-disabled="true"
        tabIndex={0}
        onClickCapture={(event) => {
          event.preventDefault();
          event.stopPropagation();
        }}
        style={{ display: 'inline-block', opacity: 0.5, cursor: 'not-allowed' }}
      >
        {/* React 18 的 JSX 类型尚未包含 inert，用空字符串透传为 inert="" 布尔属性 */}
        <span {...{ inert: '' }} style={{ display: 'inline-block', pointerEvents: 'none' }}>
          {children}
        </span>
      </span>
    </Tooltip>
  );
}
