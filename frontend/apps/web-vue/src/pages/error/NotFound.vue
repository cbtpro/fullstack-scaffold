<template>
  <a-result status="404" title="404" :sub-title="t('error.notFoundDesc')">
    <template #extra>
      <a-button type="primary" @click="router.push('/home')">{{ t('error.backHome') }}</a-button>
    </template>
  </a-result>
</template>

<script setup lang="ts">
// 404 页：未匹配任何路由时的兜底展示
import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';

const router = useRouter();
const { t } = useI18n();
</script>
