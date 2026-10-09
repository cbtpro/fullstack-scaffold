import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { Layout, Menu, Dropdown, Skeleton, type MenuProps } from 'antd';
import { Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { createAuthGuard, clearToken } from '@fs/auth';
import type { Menu as FsMenu, User } from '@fs/types';
import { fs } from '@/request';
import { authStore, useAuth } from '@/store/auth';
import { MenuIcon } from '@/components/MenuIcon';

const { Header, Sider, Content } = Layout;

const guard = createAuthGuard(authStore, { loginPath: '/login' });

function RequireAuth({ children }: { children: ReactNode }) {
  // 订阅 token 以触发守卫重算
  useAuth((s) => s.token);
  const location = useLocation();
  const decision = guard(location.pathname);
  if (typeof decision === 'string') {
    return <Navigate to={decision} replace state={{ from: location.pathname }} />;
  }
  return <>{children}</>;
}

type AntdMenuItem = Required<MenuProps>['items'][number];

/** 后端菜单树 → antd Menu items；保留 path 作 key，meta.title 作 label。 */
function toAntdItems(menus: FsMenu[]): AntdMenuItem[] {
  return menus.map((m) => {
    const label = m.meta?.title ?? m.name;
    const icon = <MenuIcon icon={m.meta?.icon} />;
    if (m.children && m.children.length > 0) {
      return {
        key: m.path,
        label,
        icon,
        children: toAntdItems(m.children),
      } as AntdMenuItem;
    }
    return { key: m.path, label, icon } as AntdMenuItem;
  });
}

export default function BasicLayout() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const user = useAuth((s) => s.user);
  const setAuth = useAuth((s) => s.set);
  const [menus, setMenus] = useState<FsMenu[]>([]);

  // 拉取当前用户信息（login 响应里的 user 已含 permissions，这里补拉一次以同步最新角色/权限）
  useEffect(() => {
    if (user) return;
    let active = true;
    fs
      .get<User>('/user/info')
      .then((u) => {
        if (active) setAuth({ user: u });
      })
      .catch(() => {
        /* 401 已由 request 拦截器处理 */
      });
    return () => {
      active = false;
    };
  }, [user, setAuth]);

  // 拉取当前用户可见菜单
  useEffect(() => {
    if (!user) {
      setMenus([]);
      return;
    }
    let active = true;
    fs
      .get<FsMenu[]>('/user/menus')
      .then((list) => {
        if (active) setMenus(list ?? []);
      })
      .catch(() => {
        // 菜单拉取失败不阻断主流程，留空菜单避免白屏
        if (active) setMenus([]);
      });
    return () => {
      active = false;
    };
  }, [user]);

  // 后端菜单树 → antd Menu items；再叠加本地 home 菜单（无权限限制）
  const menuItems = useMemo<AntdMenuItem[]>(() => {
    const items: AntdMenuItem[] = [{ key: '/home', label: t('menu.home'), icon: <MenuIcon icon="home" /> }];
    const dynamic = toAntdItems(menus);
    return items.concat(dynamic);
  }, [menus, t]);

  const onLogout = ({ key }: { key: string }) => {
    if (key !== 'logout') return;
    clearToken();
    useAuth.getState().clear();
    navigate('/login', { replace: true });
  };

  const accountItems = [{ key: 'logout', label: t('login.logout') }];

  // 当前路径在 menuItems 中匹配的 key（取前缀，处理 /user/123 等子路径）
  const selectedKeys = useMemo(() => {
    const match = menuItems
      .map((i) => i?.key)
      .filter((k): k is string => typeof k === 'string')
      .filter((k) => location.pathname === k || location.pathname.startsWith(`${k}/`));
    return match.length ? match : [location.pathname];
  }, [menuItems, location.pathname]);

  const [collapsed, setCollapsed] = useState(false);

  return (
    <RequireAuth>
      <Layout style={{ minHeight: '100vh' }}>
        <Sider
          breakpoint="lg"
          collapsible
          collapsed={collapsed}
          onCollapse={setCollapsed}
          style={{ background: '#001529' }}
        >
          <div
            style={{
              height: 48,
              color: '#fff',
              fontSize: collapsed ? 20 : 16,
              fontWeight: 600,
              textAlign: 'center',
              lineHeight: '48px',
              overflow: 'hidden',
              whiteSpace: 'nowrap',
            }}
          >
            {collapsed ? 'FS' : 'fullstack-scaffold'}
          </div>
          <Menu
            theme="dark"
            mode="inline"
            selectedKeys={selectedKeys}
            items={menuItems}
            onClick={({ key }) => navigate(key)}
          />
        </Sider>
        <Layout>
          <Header
            style={{
              background: '#fff',
              padding: '0 24px',
              display: 'flex',
              justifyContent: 'flex-end',
              alignItems: 'center',
            }}
          >
            <Dropdown menu={{ items: accountItems, onClick: onLogout }}>
              <span style={{ cursor: 'pointer' }}>
                {user ? user.nickname ?? user.username : <Skeleton.Input active size="small" />}
              </span>
            </Dropdown>
          </Header>
          <Content style={{ margin: 16 }}>
            <Outlet />
          </Content>
        </Layout>
      </Layout>
    </RequireAuth>
  );
}
