import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { createAuthGuard } from '@fs/auth';
import { authStore } from '@/store/auth';

const guard = createAuthGuard(authStore, { loginPath: '/login' });

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/pages/login/index.vue'),
  },
  {
    path: '/',
    component: () => import('@/layouts/BasicLayout.vue'),
    children: [
      { path: '', redirect: { name: 'home' } },
      { path: 'home', name: 'home', component: () => import('@/pages/home/index.vue') },
      { path: 'user', name: 'user', component: () => import('@/pages/user-list/index.vue') },
      // 用户新建/详情/编辑共用三态页，模式由路由名派生
      { path: 'user/create', name: 'user-create', component: () => import('@/pages/user-form/index.vue') },
      { path: 'user/detail/:id', name: 'user-detail', component: () => import('@/pages/user-form/index.vue') },
      { path: 'user/edit/:id', name: 'user-edit', component: () => import('@/pages/user-form/index.vue') },
    ],
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach((to) => {
  const decision = guard(to.path);
  if (typeof decision === 'string') {
    return { path: decision, query: { redirect: to.fullPath } };
  }
  return true;
});

export default router;
