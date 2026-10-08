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
