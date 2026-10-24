<template>
  <a-layout class="layout-root">
    <a-layout-sider v-model:collapsed="collapsed" breakpoint="lg" collapsible>
      <div class="logo" :class="{ 'logo--collapsed': collapsed }">
        {{ collapsed ? 'FS' : 'fullstack-scaffold' }}
      </div>
      <a-menu
        theme="dark"
        mode="inline"
        :selected-keys="selectedKeys"
        :items="menuItems"
        @click="onMenuClick"
      />
    </a-layout-sider>
    <a-layout>
      <a-layout-header class="header">
        <a-dropdown>
          <span class="account">
            <template v-if="user">
              {{ user.nickname ?? user.username }}
            </template>
            <a-skeleton-input v-else active size="small" />
          </span>
          <template #overlay>
            <a-menu @click="onAccountClick">
              <a-menu-item key="profile">{{ t('menu.profile') }}</a-menu-item>
              <a-menu-item key="change-password">{{ t('login.changePassword') }}</a-menu-item>
              <a-menu-item key="logout">{{ t('login.logout') }}</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
        <ChangePasswordModal :open="passwordOpen" @close="passwordOpen = false" />
      </a-layout-header>
      <a-layout-content class="content">
        <TabBar />
        <!-- 布局级 403 守卫：路由 meta 声明 permission，无对应权限码时不渲染页面；
             user 未就绪（权限集合为空）先渲染骨架等待，避免刷新直达被误判 -->
        <a-skeleton v-if="!user" active />
        <ForbiddenPage v-else-if="forbidden" />
        <router-view v-else />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { computed, h, onMounted, ref, watch, type VNode } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { clearToken } from '@fs/auth';
import type { Menu as FsMenu, User } from '@fs/types';
import { fs } from '@/request';
import { useAuthStore } from '@/store/auth';
import { usePermission } from '@/composables/usePermission';
import { MenuIcon } from '@/components/MenuIcon';
import TabBar from '@/components/TabBar.vue';
import ChangePasswordModal from '@/components/ChangePasswordModal.vue';
import ForbiddenPage from '@/pages/error/Forbidden.vue';

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const user = computed(() => auth.user);
const menus = ref<FsMenu[]>([]);
const collapsed = ref(false);
const passwordOpen = ref(false);

// 菜单高亮按路径前缀匹配，三态页等子路由也能点亮所属菜单
const selectedKeys = computed(() => {
  const keys = ['/home', ...menus.value.map((m) => m.path)];
  const match = keys.find((k) => route.path === k || route.path.startsWith(`${k}/`));
  return [match ?? route.path];
});

// 布局级 403 守卫判定：路由 meta 声明 permission，当前用户无对应权限码时拦截
const { check } = usePermission();
const forbidden = computed(() => {
  const required = route.meta.permission;
  if (!user.value || !required) return false;
  return !check({ codes: Array.isArray(required) ? required : [required] });
});

interface MenuItem {
  key: string;
  label: string;
  icon?: VNode;
  children?: MenuItem[];
}

/** 后端菜单树 → antd-vue Menu items；保留 path 作 key，meta.title 作 label */
function toItems(list: FsMenu[]): MenuItem[] {
  return list.map((m): MenuItem => {
    const label = m.meta?.title ?? m.name;
    const icon = m.meta?.icon ? h(MenuIcon, { icon: m.meta.icon }) : undefined;
    if (m.children && m.children.length > 0) {
      return {
        key: m.path,
        label,
        icon,
        children: toItems(m.children),
      };
    }
    return { key: m.path, label, icon };
  });
}

/** 本地 home 菜单 + 后端动态菜单合并 */
const menuItems = computed(() => [
  { key: '/home', label: t('menu.home'), icon: h(MenuIcon, { icon: 'home' }) },
  ...toItems(menus.value),
]);

async function fetchMenus() {
  if (!auth.user) {
    menus.value = [];
    return;
  }
  try {
    const list = await fs.get<FsMenu[]>('/user/menus');
    menus.value = list ?? [];
  } catch {
    menus.value = [];
  }
}

onMounted(() => {
  if (auth.user) {
    fetchMenus();
    return;
  }
  fs.get<User>('/user/info')
    .then((u) => {
      auth.set({ user: u });
    })
    .catch(() => {
      /* 401 已由 request 拦截器处理 */
    });
});

watch(
  () => auth.user,
  (u) => {
    if (u) fetchMenus();
  },
  { immediate: false },
);

function onMenuClick({ key }: { key: string }) {
  router.push(key);
}

function onAccountClick({ key }: { key: string }) {
  if (key === 'profile') {
    router.push('/profile');
    return;
  }
  if (key === 'change-password') {
    passwordOpen.value = true;
    return;
  }
  if (key !== 'logout') return;
  clearToken();
  auth.clear();
  router.push('/login');
}
</script>

<style scoped>
.layout-root {
  min-height: 100vh;
}
.logo {
  height: 48px;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  text-align: center;
  line-height: 48px;
  overflow: hidden;
  white-space: nowrap;
}
.logo--collapsed {
  font-size: 20px;
}
.header {
  background: #fff;
  padding: 0 24px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
}
.account {
  cursor: pointer;
}
.content {
  margin: 16px;
}
</style>
