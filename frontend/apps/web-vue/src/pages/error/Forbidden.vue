<template>
  <a-result status="403" title="403" :sub-title="t('error.forbiddenDesc')">
    <template #extra>
      <a-button type="primary" @click="router.push('/home')">{{ t('error.backHome') }}</a-button>
    </template>
  </a-result>
</template>

<script setup lang="ts">
// 403 页：已登录但权限不足访问被守卫路由时的兜底展示
import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';

const router = useRouter();
const { t } = useI18n();
</script>
