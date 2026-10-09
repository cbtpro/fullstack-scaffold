import { Navigate, createBrowserRouter } from 'react-router-dom';
import BasicLayout from '@/layouts/BasicLayout';
import LoginPage from '@/pages/login';
import HomePage from '@/pages/home';
import UserListPage from '@/pages/user-list';
import UserFormPage from '@/pages/user-form';
import type { RouteHandle } from '@/components/TabBar';

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: <BasicLayout />,
    children: [
      { index: true, element: <Navigate to="/home" replace /> },
      { path: 'home', element: <HomePage />, handle: { title: 'menu.home' } satisfies RouteHandle },
      { path: 'user', element: <UserListPage />, handle: { title: 'menu.user' } satisfies RouteHandle },
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
    ],
  },
]);
