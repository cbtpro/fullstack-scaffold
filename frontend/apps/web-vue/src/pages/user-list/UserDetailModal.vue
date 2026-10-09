<template>
  <a-modal
    :open="open"
    :title="t('user.detailTitle')"
    :footer="null"
    @cancel="emit('update:open', false)"
  >
    <a-spin v-if="loading" class="spin" />
    <a-descriptions v-else-if="detail" :column="1" size="small">
      <a-descriptions-item :label="t('login.username')">{{ detail.username }}</a-descriptions-item>
      <a-descriptions-item :label="t('user.nickname')">{{ detail.nickname ?? '-' }}</a-descriptions-item>
      <a-descriptions-item :label="t('user.email')">{{ detail.email ?? '-' }}</a-descriptions-item>
      <a-descriptions-item :label="t('user.phone')">{{ detail.phone ?? '-' }}</a-descriptions-item>
      <a-descriptions-item :label="t('user.status')">
        <a-tag :color="detail.status === 1 ? 'green' : 'default'">
          {{ detail.status === 1 ? t('user.enabled') : t('user.disabled') }}
        </a-tag>
      </a-descriptions-item>
      <a-descriptions-item :label="t('user.createTime')">{{ detail.createTime ?? '-' }}</a-descriptions-item>
      <a-descriptions-item :label="t('user.updateTime')">{{ detail.updateTime ?? '-' }}</a-descriptions-item>
    </a-descriptions>
  </a-modal>
</template>

<script setup lang="ts">
/** 用户详情弹窗：按 id 拉取后端最新数据展示，避免列表数据过期。 */
import { ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import type { User } from '@fs/types';
import { fs } from '@/request';

const props = defineProps<{
  open: boolean;
  record?: User | null;
}>();

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void;
}>();

const { t } = useI18n();
const detail = ref<User | null>(null);
const loading = ref(false);

watch(
  () => [props.open, props.record?.id],
  ([open]) => {
    detail.value = null;
    if (!open || !props.record?.id) return;
    loading.value = true;
    fs.get<User>(`/user/${props.record.id}`)
      .then((u) => {
        detail.value = u;
      })
      .catch(() => {
        // 业务错误已由请求层统一提示
      })
      .finally(() => {
        loading.value = false;
      });
  },
);
</script>

<style scoped>
.spin {
  display: block;
  margin: 48px auto;
}
</style>
