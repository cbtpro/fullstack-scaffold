import { Navigate, createBrowserRouter } from 'react-router-dom';
import BasicLayout from '@/layouts/BasicLayout';
import LoginPage from '@/pages/login';
import HomePage from '@/pages/home';
import UserListPage from '@/pages/user-list';
import UserFormPage from '@/pages/user-form';

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: <BasicLayout />,
    children: [
      { index: true, element: <Navigate to="/home" replace /> },
      { path: 'home', element: <HomePage /> },
      { path: 'user', element: <UserListPage /> },
      // 用户新建/详情/编辑共用三态页，模式由路径派生
      { path: 'user/create', element: <UserFormPage /> },
      { path: 'user/detail/:id', element: <UserFormPage /> },
      { path: 'user/edit/:id', element: <UserFormPage /> },
    ],
  },
]);
