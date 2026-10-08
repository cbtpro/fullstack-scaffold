<template>
  <a-config-provider :theme="theme">
    <RouterView />
  </a-config-provider>
</template>

<script setup lang="ts">
import { RouterView } from 'vue-router';
import { theme as antTheme } from 'ant-design-vue';

const theme = {
  token: {
    borderRadius: 0,
    borderRadiusLG: 0,
    borderRadiusSM: 0,
    borderRadiusXS: 0,
  },
  algorithm: antTheme.defaultAlgorithm,
};
</script>
