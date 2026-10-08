<template>
  <a-layout class="layout-root">
    <a-layout-sider breakpoint="lg" collapsible>
      <div class="logo">fullstack-scaffold</div>
      <a-menu
        theme="dark"
        mode="inline"
        :selected-keys="selectedKeys"
        @click="onMenuClick"
      >
        <a-menu-item key="/home">{{ t('menu.home') }}</a-menu-item>
        <a-menu-item key="/user">{{ t('menu.user') }}</a-menu-item>
      </a-menu>
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
import { computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { clearToken } from '@fs/auth';
import type { User } from '@fs/types';
import { fs } from '@/request';
import { useAuthStore } from '@/store/auth';

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const user = computed(() => auth.user);
const selectedKeys = computed(() => [route.path]);

onMounted(() => {
  if (auth.user) return;
  fs.get<User>('/user/info')
    .then((u) => auth.set({ user: u }))
    .catch(() => {
      /* 401 已由 request 拦截器处理 */
    });
});

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
