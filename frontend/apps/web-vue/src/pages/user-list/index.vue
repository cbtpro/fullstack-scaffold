<template>
  <a-card>
    <div class="toolbar">
      <a-typography-title :level="3" class="title">{{ t('user.list') }}</a-typography-title>
      <Permission codes="user:create">
        <a-button type="primary" @click="openCreate">{{ t('common.create') }}</a-button>
      </Permission>
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
            <Permission codes="user:view" fallback="disable">
              <a-button size="small" @click="openDetail(record)">{{ t('common.view') }}</a-button>
            </Permission>
            <Permission codes="user:update" fallback="disable" :custom-fn="canEditRecord" :extra="record">
              <a-button size="small" @click="openEdit(record)">{{ t('common.edit') }}</a-button>
            </Permission>
            <Permission codes="user:assign-role" fallback="disable">
              <a-button size="small" @click="onAssignRole(record)">{{ t('user.assignRole') }}</a-button>
            </Permission>
            <!-- Popconfirm 整体包进权限组件，无权限时触发器与确认层一起卸载 -->
            <Permission codes="user:delete">
              <a-popconfirm
                :title="t('user.deleteConfirm')"
                :ok-text="t('common.confirm')"
                :cancel-text="t('common.cancel')"
                @confirm="onDelete(record)"
              >
                <a-button danger size="small">{{ t('common.delete') }}</a-button>
              </a-popconfirm>
            </Permission>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 分配角色弹窗 -->
    <a-modal
      :title="roleUser ? t('user.assignRoleTitle', { name: roleUser.nickname || roleUser.username }) : ''"
      v-model:open="roleOpen"
      @ok="onSaveRole"
      destroy-on-close
    >
      <a-select
        mode="multiple"
        style="width: 100%"
        :placeholder="t('user.roles')"
        v-model:value="roleIds"
        :options="roleOptions.map((r) => ({ value: r.id, label: `${r.name} (${r.code})` }))"
      />
    </a-modal>
  </a-card>
</template>

<script setup lang="ts">
/**
 * 用户列表页：分页查询 + 删除；查看/编辑/新增跳转三态表单页（user-create/user-detail/user-edit）。
 * 操作权限走包装器模式：Permission 负责放行与否，view/edit 灰置提示、
 * Popconfirm 整体隐藏，edit 叠加记录状态业务条件（customFn），后端仍需独立鉴权。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import type { PageResult, Role, User } from '@fs/types';
import { fs } from '@/request';
import Permission from '@/components/permission/Permission.vue';

const { t } = useI18n();
const router = useRouter();
const loading = ref(false);
const data = reactive<PageResult<User>>({
  list: [],
  total: 0,
  pageNum: 1,
  pageSize: 10,
});

// 分配角色弹窗
const roleOpen = ref(false);
const roleUser = ref<User | null>(null);
const roleIds = ref<number[]>([]);
const roleOptions = ref<Role[]>([]);

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

/** 业务条件：仅启用状态的用户可编辑（控制界面入口，后端仍需独立鉴权） */
function canEditRecord(_userRoles: string[], extra?: unknown) {
  return (extra as User | undefined)?.status === 1;
}

function onDelete(record: User) {
  fs.delete<void>(`/user/${record.id}`).then(() => {
    message.success(t('user.deleteSuccess'));
    reload(data.pageNum, data.pageSize);
  });
}

function onAssignRole(record: User) {
  roleUser.value = record;
  Promise.all([fs.get<Role[]>('/role/all'), fs.get<number[]>(`/user/${record.id}/roles`)]).then(
    ([roles, ids]) => {
      roleOptions.value = roles;
      roleIds.value = ids;
      roleOpen.value = true;
    },
  );
}

function onSaveRole() {
  if (!roleUser.value) return;
  fs.put<void>(`/user/${roleUser.value.id}/roles`, roleIds.value).then(() => {
    message.success(t('user.saveSuccess'));
    roleOpen.value = false;
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
