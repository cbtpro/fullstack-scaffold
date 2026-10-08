import { Navigate, createBrowserRouter } from 'react-router-dom';
import BasicLayout from '@/layouts/BasicLayout';
import LoginPage from '@/pages/login';
import HomePage from '@/pages/home';
import UserListPage from '@/pages/user-list';

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: <BasicLayout />,
    children: [
      { index: true, element: <Navigate to="/home" replace /> },
      { path: 'home', element: <HomePage /> },
      { path: 'user', element: <UserListPage /> },
    ],
  },
]);
