/**
 * 路由驱动的多标签栏：激活项由 URL 派生，标签历史单独保存。
 * 标签键 = pathname + 排序后的 query；标题来自路由 handle.title，可按路由参数动态生成。
 */
import { useEffect, useMemo, useState } from 'react';
import { Tabs } from 'antd';
import { useLocation, useMatches, useNavigate, type Params } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

/** 翻译函数形态（i18next TFunction 可赋值给该子集） */
type Translator = (key: string) => string;

/** 路由 handle：title 为 i18n key 或按路由参数生成文案的函数；permission 声明进入该路由所需权限码 */
export interface RouteHandle {
  title?: string | ((t: Translator, params: Params<string>) => string);
  /** 进入该路由所需权限码（数组时任一满足即可）；布局级 403 守卫使用 */
  permission?: string | string[];
}

interface RouteTab {
  key: string;
  label: string;
  closable: boolean;
}

/** 标签键 = pathname + 排序后的 search，避免参数顺序不同生成两个标签 */
function getTabKey(pathname: string, search: string) {
  const params = new URLSearchParams(search);
  params.sort();
  const query = params.toString();
  return query ? `${pathname}?${query}` : pathname;
}

const HOME_TAB_KEY = '/home';

export default function TabBar() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const matches = useMatches();

  // 标签标题取叶子路由 handle，描述当前页面而非层级
  const leaf = matches[matches.length - 1];
  const handle = leaf?.handle as RouteHandle | undefined;
  const currentLabel = useMemo(() => {
    const title = handle?.title;
    if (!title) return '';
    return typeof title === 'function' ? title(t, leaf?.params ?? {}) : t(title);
  }, [handle, leaf, t]);

  const currentKey = getTabKey(location.pathname, location.search);

  // 首页为固定首项，closable: false 隐藏关闭按钮
  const [tabs, setTabs] = useState<RouteTab[]>([{ key: HOME_TAB_KEY, label: '', closable: false }]);

  // 激活项由 URL 派生，标签历史在此追加：新地址补录，已有地址仅同步最新标题
  useEffect(() => {
    if (currentKey === HOME_TAB_KEY || !currentLabel) return;
    setTabs((previous) => {
      const existing = previous.find((tab) => tab.key === currentKey);
      if (!existing) return [...previous, { key: currentKey, label: currentLabel, closable: true }];
      if (existing.label === currentLabel) return previous;
      return previous.map((tab) => (tab.key === currentKey ? { ...tab, label: currentLabel } : tab));
    });
  }, [currentKey, currentLabel]);

  const removeTab = (key: string) => {
    if (key === HOME_TAB_KEY) return;
    const index = tabs.findIndex((tab) => tab.key === key);
    if (index < 0) return;
    const next = tabs.filter((tab) => tab.key !== key);
    setTabs(next);
    // 关闭当前项导航到左侧相邻项；关闭非当前项只修改历史，不改当前 URL
    if (key === currentKey) {
      navigate(next[Math.max(0, index - 1)]?.key ?? HOME_TAB_KEY, { replace: true });
    }
  };

  return (
    <Tabs
      type="editable-card"
      size="small"
      hideAdd
      activeKey={currentKey}
      // 首页标签文案实时取 t，语言切换同步更新
      items={tabs.map((tab) => (tab.key === HOME_TAB_KEY ? { ...tab, label: t('menu.home') } : tab))}
      onChange={(key) => navigate(key)}
      onEdit={(key, action) => {
        if (action === 'remove' && typeof key === 'string') removeTab(key);
      }}
      style={{ marginBottom: 12 }}
    />
  );
}
