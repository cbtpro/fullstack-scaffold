import { Navigate, createBrowserRouter } from 'react-router-dom';
import BasicLayout from '@/layouts/BasicLayout';
import LoginPage from '@/pages/login';
import HomePage from '@/pages/home';
import UserListPage from '@/pages/user-list';
import UserFormPage from '@/pages/user-form';
import DeviceListPage from '@/pages/device-list';
import RoleListPage from '@/pages/role-list';
import PermissionListPage from '@/pages/permission-list';
import PermissionGroupListPage from '@/pages/permission-group-list';
import type { RouteHandle } from '@/components/TabBar';
import NotFoundPage from '@/pages/error/NotFound';

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: <BasicLayout />,
    children: [
      { index: true, element: <Navigate to="/home" replace /> },
      { path: 'home', element: <HomePage />, handle: { title: 'menu.home' } satisfies RouteHandle },
      // 列表页声明菜单权限：菜单不可见的用户直达 URL 也会被布局级守卫拦截为 403
      {
        path: 'user',
        element: <UserListPage />,
        handle: { title: 'menu.user', permission: 'menu:user' } satisfies RouteHandle,
      },
      {
        path: 'device',
        element: <DeviceListPage />,
        handle: { title: 'menu.device', permission: 'menu:device' } satisfies RouteHandle,
      },
      {
        path: 'role',
        element: <RoleListPage />,
        handle: { title: 'menu.role', permission: 'menu:role' } satisfies RouteHandle,
      },
      {
        path: 'permission',
        element: <PermissionListPage />,
        handle: { title: 'menu.permission', permission: 'menu:permission' } satisfies RouteHandle,
      },
      {
        path: 'permission-group',
        element: <PermissionGroupListPage />,
        handle: { title: 'menu.permissionGroup', permission: 'menu:permission-group' } satisfies RouteHandle,
      },
      // 用户新建/详情/编辑共用三态页，模式由路径派生；
      // 详情/编辑标题按记录 id 生成，同一路由打开多条记录时各自成标签
      {
        path: 'user/create',
        element: <UserFormPage />,
        handle: { title: 'user.createTitle' } satisfies RouteHandle,
      },
      {
        path: 'user/detail/:id',
        element: <UserFormPage />,
        handle: { title: (t, params) => `${t('user.detailTitle')} #${params.id}` } satisfies RouteHandle,
      },
      {
        path: 'user/edit/:id',
        element: <UserFormPage />,
        handle: { title: (t, params) => `${t('user.editTitle')} #${params.id}` } satisfies RouteHandle,
      },
      // 兜底 404：未匹配的路径渲染带布局的 NotFound 页
      { path: '*', element: <NotFoundPage />, handle: { title: 'error.notFoundTitle' } satisfies RouteHandle },
    ],
  },
]);
