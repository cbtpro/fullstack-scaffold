import { useEffect, type ReactNode } from 'react';
import { Layout, Menu, Dropdown, Skeleton } from 'antd';
import { Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { createAuthGuard, clearToken } from '@fs/auth';
import type { User } from '@fs/types';
import { fs } from '@/request';
import { authStore, useAuth } from '@/store/auth';

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

export default function BasicLayout() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const user = useAuth((s) => s.user);
  const setAuth = useAuth((s) => s.set);

  useEffect(() => {
    if (user) return;
    let active = true;
    fs.get<User>('/user/info')
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

  const onLogout = ({ key }: { key: string }) => {
    if (key !== 'logout') return;
    clearToken();
    useAuth.getState().clear();
    navigate('/login', { replace: true });
  };

  const menuItems = [
    { key: '/home', label: t('menu.home') },
    { key: '/user', label: t('menu.user') },
  ];

  const accountItems = [{ key: 'logout', label: t('login.logout') }];

  return (
    <RequireAuth>
      <Layout style={{ minHeight: '100vh' }}>
        <Sider breakpoint="lg" collapsible style={{ background: '#001529' }}>
          <div
            style={{
              height: 48,
              color: '#fff',
              fontSize: 16,
              fontWeight: 600,
              textAlign: 'center',
              lineHeight: '48px',
              overflow: 'hidden',
              whiteSpace: 'nowrap',
            }}
          >
            fullstack-scaffold
          </div>
          <Menu
            theme="dark"
            mode="inline"
            selectedKeys={[location.pathname]}
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
