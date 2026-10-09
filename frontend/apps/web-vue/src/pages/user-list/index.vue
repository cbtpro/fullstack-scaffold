<template>
  <a-card>
    <div class="toolbar">
      <a-typography-title :level="3" class="title">{{ t('user.list') }}</a-typography-title>
      <a-button
        v-permission="'user:create'"
        type="primary"
        @click="reload()"
      >
        {{ t('common.create', '新增') }}
      </a-button>
    </div>
    <a-table
      row-key="id"
      size="middle"
      :loading="loading"
      :data-source="data.list"
      :columns="columns"
      :pagination="pagination"
      @change="onPageChange"
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
  {
    title: t('common.operation'),
    key: '__op',
    width: 200,
    customRender: () => '—',
  },
]);

const pagination = computed(() => ({
  total: data.total,
  current: data.pageNum,
  pageSize: data.pageSize,
  showTotal: (total: number) => t('user.total', { count: total }),
}));

function reload(pageNum = 1, pageSize = 10) {
  loading.value = true;
  fs.get<PageResult<User>>('/user/list', { params: { pageNum, pageSize } })
    .then((res) => {
      data.list = res.list;
      data.total = res.total;
      data.pageNum = res.pageNum;
      data.pageSize = res.pageSize;
    })
    .finally(() => {
      loading.value = false;
    });
}

function onPageChange(p: { current?: number; pageSize?: number }) {
  reload(p.current ?? 1, p.pageSize ?? 10);
}

onMounted(() => reload(1, 10));
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.title {
  margin: 0;
}
</style>
