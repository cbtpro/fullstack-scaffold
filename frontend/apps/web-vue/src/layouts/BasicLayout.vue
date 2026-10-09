<template>
  <a-layout class="layout-root">
    <a-layout-sider breakpoint="lg" collapsible>
      <div class="logo">fullstack-scaffold</div>
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
              <a-menu-item key="logout">{{ t('login.logout') }}</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </a-layout-header>
      <a-layout-content class="content">
        <router-view />
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
import { MenuIcon } from '@/components/MenuIcon';

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const user = computed(() => auth.user);
const menus = ref<FsMenu[]>([]);

const selectedKeys = computed(() => [route.path]);

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

watch(() => auth.user, (u) => {
  if (u) fetchMenus();
}, { immediate: false });

function onMenuClick({ key }: { key: string }) {
  router.push(key);
}

function onAccountClick({ key }: { key: string }) {
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
