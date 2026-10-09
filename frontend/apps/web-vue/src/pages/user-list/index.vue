<template>
  <a-card>
    <div class="toolbar">
      <a-typography-title :level="3" class="title">{{ t('user.list') }}</a-typography-title>
      <a-button v-permission="'user:create'" type="primary" @click="openCreate">
        {{ t('common.create') }}
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
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === '__op'">
          <a-space>
            <a-button v-permission="'user:view'" size="small" @click="openDetail(record)">
              {{ t('common.view') }}
            </a-button>
            <a-button v-permission="'user:update'" size="small" @click="openEdit(record)">
              {{ t('common.edit') }}
            </a-button>
            <!-- v-permission 无权限时移除按钮节点；外层 popconfirm 若保留会因无触发子节点而失效 -->
            <a-popconfirm
              v-if="canDelete"
              :title="t('user.deleteConfirm')"
              :ok-text="t('common.confirm')"
              :cancel-text="t('common.cancel')"
              @confirm="onDelete(record)"
            >
              <a-button danger size="small">{{ t('common.delete') }}</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>
  </a-card>
</template>

<script setup lang="ts">
/**
 * 用户列表页：分页查询 + 删除；查看/编辑/新增跳转三态表单页（user-detail/user-edit/user-create）。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import type { PageResult, User } from '@fs/types';
import { hasPermission } from '@fs/auth';
import { fs } from '@/request';
import { useAuthStore } from '@/store/auth';

const { t } = useI18n();
const router = useRouter();
const auth = useAuthStore();
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
  { title: t('user.nickname'), dataIndex: 'nickname', customRender: ({ text }: { text?: string }) => text ?? '-' },
  { title: t('user.email'), dataIndex: 'email', customRender: ({ text }: { text?: string }) => text ?? '-' },
  {
    title: t('user.roles'),
    dataIndex: 'roles',
    customRender: ({ text }: { text?: string[] }) => text?.join(', ') ?? '-',
  },
  {
    title: t('user.status'),
    dataIndex: 'status',
    width: 90,
    customRender: ({ text }: { text?: number }) =>
      text === 1 ? t('user.enabled') : t('user.disabled'),
  },
  { title: t('user.createTime'), dataIndex: 'createTime', width: 180 },
  { title: t('common.operation'), key: '__op', width: 200 },
]);

const pagination = computed(() => ({
  total: data.total,
  current: data.pageNum,
  pageSize: data.pageSize,
  showTotal: (total: number) => t('user.total', { count: total }),
}));

// 后端 PageQuery 参数为 page/size（pageNum/pageSize 会被忽略导致翻页失效）
function reload(page = 1, size = 10) {
  loading.value = true;
  fs.get<PageResult<User>>('/user/list', { params: { page, size } })
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

function openCreate() {
  router.push({ name: 'user-create' });
}

function openEdit(record: User) {
  router.push({ name: 'user-edit', params: { id: String(record.id) } });
}

function openDetail(record: User) {
  router.push({ name: 'user-detail', params: { id: String(record.id) } });
}

// 指令只管 DOM 移除，popconfirm 触发器需要 v-if 同步判定
const canDelete = computed(() => hasPermission(auth.user?.permissions ?? [], 'user:delete'));

function onDelete(record: User) {
  fs.delete<void>(`/user/${record.id}`).then(() => {
    message.success(t('user.deleteSuccess'));
    reload(data.pageNum, data.pageSize);
  });
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
