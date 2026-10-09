/**
 * 菜单图标：基于 lucide-vue-next，与 web-react 端共用同一套图标语义。
 * 后端 sys_permission.icon 字段为字符串（如 'team'、'home'），
 * 此组件按名称映射到 lucide 图标，供 antd-vue Menu 的 icon 属性使用。
 */
import { defineComponent, h, type Component } from 'vue';
import { House, Settings, User, Users } from 'lucide-vue-next';

/** 后端图标名 → lucide 图标组件（新增图标在此登记即可） */
const ICON_MAP: Record<string, Component> = {
  home: House,
  team: Users,
  user: User,
  setting: Settings,
};

/**
 * 菜单图标组件。icon 为后端 sys_permission.icon 字符串名称。
 * 折叠侧边栏时 antd Menu 只显示 icon，不传 icon 时折叠后为空白。
 */
export const MenuIcon = defineComponent({
  name: 'MenuIcon',
  props: {
    icon: { type: String, default: '' },
  },
  setup(props) {
    return () => {
      const Lucide = ICON_MAP[props.icon];
      if (!Lucide) return null;
      // 包一层 span.anticon：antd Menu 的图标间距/对齐样式作用在 .anticon 上
      return h(
        'span',
        { role: 'img', class: 'anticon', 'aria-hidden': 'true' },
        [h(Lucide, { size: '1em' })],
      );
    };
  },
});
