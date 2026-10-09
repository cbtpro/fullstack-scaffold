import { createRouter, createWebHistory, type RouteParams, type RouteRecordRaw } from 'vue-router';
import { createAuthGuard } from '@fs/auth';
import { authStore } from '@/store/auth';

declare module 'vue-router' {
  interface RouteMeta {
    /** 标签标题：i18n key，或按路由参数生成文案的函数（多标签栏 TabBar 使用） */
    title?: string | ((t: (key: string) => string, params: RouteParams) => string);
  }
}

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
      { path: 'home', name: 'home', meta: { title: 'menu.home' }, component: () => import('@/pages/home/index.vue') },
      { path: 'user', name: 'user', meta: { title: 'menu.user' }, component: () => import('@/pages/user-list/index.vue') },
      // 用户新建/详情/编辑共用三态页，模式由路由名派生；
      // 详情/编辑标题按记录 id 生成，同一路由打开多条记录时各自成标签
      {
        path: 'user/create',
        name: 'user-create',
        meta: { title: 'user.createTitle' },
        component: () => import('@/pages/user-form/index.vue'),
      },
      {
        path: 'user/detail/:id',
        name: 'user-detail',
        meta: { title: (t, params) => `${t('user.detailTitle')} #${params.id}` },
        component: () => import('@/pages/user-form/index.vue'),
      },
      {
        path: 'user/edit/:id',
        name: 'user-edit',
        meta: { title: (t, params) => `${t('user.editTitle')} #${params.id}` },
        component: () => import('@/pages/user-form/index.vue'),
      },
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
