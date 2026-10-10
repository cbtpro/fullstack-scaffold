/**
 * 菜单图标：基于 lucide-react，零自定义 SVG。
 * 后端 sys_permission.icon 字段为字符串（如 'team'、'home'），
 * 此组件按名称映射到 lucide 图标，供 antd Menu 的 icon 属性使用。
 */
import type { ComponentType, ReactNode } from 'react';
import { House, Settings, Smartphone, User, Users } from 'lucide-react';

type LucideIcon = ComponentType<{ size?: number | string; className?: string }>;

/** 后端图标名 → lucide 图标组件（新增图标在此登记即可） */
const ICON_MAP: Record<string, LucideIcon> = {
  home: House,
  team: Users,
  user: User,
  setting: Settings,
  smartphone: Smartphone,
};

/**
 * 菜单图标组件。icon 为后端 sys_permission.icon 字符串名称。
 * 折叠侧边栏时 antd Menu 只显示 icon，不传 icon 时折叠后为空白。
 */
export function MenuIcon({ icon }: { icon?: string }): ReactNode {
  const Lucide = icon ? ICON_MAP[icon] : undefined;
  if (!Lucide) return null;
  // 包一层 span.anticon：antd Menu 的图标间距/对齐样式作用在 .anticon 上
  return (
    <span role="img" className="anticon" aria-hidden="true">
      <Lucide size="1em" />
    </span>
  );
}
