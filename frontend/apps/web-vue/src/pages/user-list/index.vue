<template>
  <a-card>
    <a-typography-title :level="3">{{ t('user.list') }}</a-typography-title>
    <a-table
      row-key="id"
      size="middle"
      :loading="loading"
      :data-source="data.list"
      :columns="columns"
      :pagination="pagination"
    />
  </a-card>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import type { PageResult, User } from '@fs/types';
import { fs } from '@/request';

const { t } = useI18n();
const loading = ref(false);
const data = reactive<PageResult<User>>({
  list: [],
  total: 0,
  pageNum: 1,
  pageSize: 10,
});

const columns = computed(() => [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: t('login.username'), dataIndex: 'username' },
  { title: 'Nickname', dataIndex: 'nickname' },
  { title: 'Email', dataIndex: 'email' },
  {
    title: 'Roles',
    dataIndex: 'roles',
    customRender: ({ text }: { text?: string[] }) => text?.join(', ') ?? '-',
  },
  {
    title: t('common.success'),
    dataIndex: 'status',
    customRender: ({ text }: { text?: number }) =>
      text === 1 ? t('common.success') : t('common.failed'),
  },
  { title: 'createTime', dataIndex: 'createTime' },
]);

const pagination = computed(() => ({
  total: data.total,
  current: data.pageNum,
  pageSize: data.pageSize,
  showTotal: (total: number) => t('user.total', { count: total }),
}));

onMounted(() => {
  loading.value = true;
  fs.get<PageResult<User>>('/user/list', { params: { pageNum: 1, pageSize: 10 } })
    .then((res) => {
      data.list = res.list;
      data.total = res.total;
      data.pageNum = res.pageNum;
      data.pageSize = res.pageSize;
    })
    .finally(() => {
      loading.value = false;
    });
});
</script>
