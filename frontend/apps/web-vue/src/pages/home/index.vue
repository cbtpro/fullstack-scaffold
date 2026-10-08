<template>
  <a-card>
    <a-typography-title :level="3">{{ t('menu.home') }}</a-typography-title>
    <a-typography-paragraph>
      {{ greeting }}
    </a-typography-paragraph>
  </a-card>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { useAuthStore } from '@/store/auth';

const { t } = useI18n();
const auth = useAuthStore();
const greeting = computed(() =>
  auth.user
    ? `${t('login.success')}，${auth.user.nickname ?? auth.user.username}`
    : t('login.success'),
);
</script>
