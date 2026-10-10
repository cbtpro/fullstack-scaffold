<template>
  <a-card>
    <a-space class="header">
      <a-typography-title :level="3">{{ t('role.title') }}</a-typography-title>
      <Permission codes="role:create">
        <a-button type="primary" @click="onCreate">{{ t('common.create') }}</a-button>
      </Permission>
    </a-space>
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
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="record.status === 1 ? 'green' : 'default'">
            {{ record.status === 1 ? t('user.enabled') : t('user.disabled') }}
          </a-tag>
        </template>
        <template v-else-if="column.key === '__op'">
          <a-space>
            <Permission codes="role:update" fallback="disable">
              <a-button size="small" @click="onEdit(record)">{{ t('common.edit') }}</a-button>
            </Permission>
            <Permission codes="role:assign" fallback="disable">
              <a-button size="small" @click="onAssign(record)">{{ t('role.assignPermission') }}</a-button>
            </Permission>
            <Permission codes="role:assign-group" fallback="disable">
              <a-button size="small" @click="onAssignGroup(record)">{{ t('role.assignGroup') }}</a-button>
            </Permission>
            <Permission codes="role:delete">
              <a-popconfirm
                :title="t('role.deleteConfirm')"
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

    <!-- 新增/编辑弹窗 -->
    <a-modal
      :title="editing ? t('role.editTitle') : t('role.createTitle')"
      v-model:open="formOpen"
      @ok="onSave"
      destroy-on-close
    >
      <a-form ref="formRef" :model="form" layout="vertical">
        <a-form-item :label="t('role.code')" name="code" :rules="[{ required: true, message: t('role.codeRequired') }]">
          <a-input v-model:value="form.code" />
        </a-form-item>
        <a-form-item :label="t('role.name')" name="name" :rules="[{ required: true, message: t('role.nameRequired') }]">
          <a-input v-model:value="form.name" />
        </a-form-item>
        <a-form-item :label="t('role.sort')" name="sort">
          <a-input-number v-model:value="form.sort" :min="0" style="width: 100%" />
        </a-form-item>
        <a-form-item :label="t('role.status')" name="status">
          <a-select v-model:value="form.status">
            <a-select-option :value="1">{{ t('user.enabled') }}</a-select-option>
            <a-select-option :value="0">{{ t('user.disabled') }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item :label="t('role.description')" name="description">
          <a-textarea v-model:value="form.description" :rows="3" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 分配权限弹窗 -->
    <a-modal
      :title="permRole ? t('role.assignPermissionTitle', { name: permRole.name }) : ''"
      v-model:open="permOpen"
      @ok="onSavePerm"
      width="520px"
      destroy-on-close
    >
      <a-tree
        checkable
        :tree-data="treeData"
        v-model:checkedKeys="checkedKeys"
        default-expand-all
      />
    </a-modal>

    <!-- 分配权限组弹窗 -->
    <a-modal
      :title="groupRole ? t('role.assignGroupTitle', { name: groupRole.name }) : ''"
      v-model:open="groupOpen"
      @ok="onSaveGroup"
      width="520px"
      destroy-on-close
    >
      <a-select
        mode="multiple"
        v-model:value="groupCheckedIds"
        :options="groupOptions"
        :placeholder="t('role.assignGroup')"
        allow-clear
        style="width: 100%"
      />
    </a-modal>
  </a-card>
</template>

<script setup lang="ts">
/**
 * 角色管理页：列表 + 新增/编辑弹窗 + 分配权限弹窗 + 分配权限组弹窗。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { message } from 'ant-design-vue';
import type { PageResult, Permission as PermissionItem, PermissionGroup, Role } from '@fs/types';
import { fs } from '@/request';
import Permission from '@/components/permission/Permission.vue';

const { t } = useI18n();
const loading = ref(false);
const data = reactive<PageResult<Role>>({ list: [], total: 0, pageNum: 1, pageSize: 10 });

const formOpen = ref(false);
const editing = ref<Role | null>(null);
const formRef = ref();
const form = reactive({ code: '', name: '', sort: 0, status: 1, description: '' });

const permOpen = ref(false);
const permRole = ref<Role | null>(null);
const allPerms = ref<PermissionItem[]>([]);
const checkedKeys = ref<(string | number)[]>([]);

// 分配权限组相关状态
const groupOpen = ref(false);
const groupRole = ref<Role | null>(null);
const allGroups = ref<PermissionGroup[]>([]);
const groupCheckedIds = ref<number[]>([]);

const treeData = computed(() => buildTree(allPerms.value));

const groupOptions = computed(() =>
  allGroups.value.map((g) => ({ label: `${g.name} (${g.code})`, value: g.id })),
);

const columns = computed(() => [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: t('role.code'), dataIndex: 'code', width: 160 },
  { title: t('role.name'), dataIndex: 'name' },
  { title: t('role.sort'), dataIndex: 'sort', width: 80 },
  { title: t('role.description'), dataIndex: 'description', customRender: ({ record }: any) => record.description ?? '-' },
  { title: t('role.status'), dataIndex: 'status', width: 90 },
  { title: t('role.createTime'), dataIndex: 'createTime', width: 180 },
  { title: t('common.operation'), key: '__op', width: 320 },
]);

const pagination = computed(() => ({
  total: data.total,
  current: data.pageNum,
  pageSize: data.pageSize,
  showTotal: (total: number) => t('user.total', { count: total }),
}));

function buildTree(list: PermissionItem[]): any[] {
  const map = new Map<number, any>();
  const roots: any[] = [];
  list.forEach((p) => map.set(p.id, { key: p.id, title: `${p.name} (${p.code})`, children: [] }));
  list.forEach((p) => {
    const node = map.get(p.id)!;
    if (p.parentId && map.has(p.parentId)) {
      map.get(p.parentId)!.children.push(node);
    } else {
      roots.push(node);
    }
  });
  return roots;
}

function reload(page = 1, size = 10) {
  loading.value = true;
  fs.get<PageResult<Role>>('/role/list', { params: { page, size } })
    .then((res) => {
      data.list = res.list;
      data.total = res.total;
      data.pageNum = res.pageNum;
      data.pageSize = res.pageSize;
    })
    .finally(() => (loading.value = false));
}

function onPageChange(p: { current?: number; pageSize?: number }) {
  reload(p.current ?? 1, p.pageSize ?? 10);
}

function onCreate() {
  editing.value = null;
  Object.assign(form, { code: '', name: '', sort: 0, status: 1, description: '' });
  formOpen.value = true;
}

function onEdit(record: Role) {
  editing.value = record;
  Object.assign(form, {
    code: record.code,
    name: record.name,
    sort: record.sort ?? 0,
    status: record.status ?? 1,
    description: record.description ?? '',
  });
  formOpen.value = true;
}

async function onSave() {
  await formRef.value?.validate();
  if (editing.value) {
    await fs.put<Role>('/role', { ...form, id: editing.value.id });
  } else {
    await fs.post<Role>('/role', { ...form });
  }
  message.success(t('role.saveSuccess'));
  formOpen.value = false;
  reload(data.pageNum, data.pageSize);
}

function onDelete(record: Role) {
  fs.delete<void>(`/role/${record.id}`).then(() => {
    message.success(t('role.deleteSuccess'));
    reload(data.pageNum, data.pageSize);
  });
}

function onAssign(record: Role) {
  permRole.value = record;
  checkedKeys.value = [];
  fs.get<PermissionItem[]>('/permission/list').then((perms) => {
    allPerms.value = perms;
    fs.get<number[]>(`/role/${record.id}/permissions`).then((ids) => {
      checkedKeys.value = ids;
      permOpen.value = true;
    });
  });
}

function onSavePerm() {
  if (!permRole.value) return;
  fs.put<void>(`/role/${permRole.value.id}/permissions`, checkedKeys.value).then(() => {
    message.success(t('role.assignSuccess'));
    permOpen.value = false;
  });
}

/** 打开分配权限组弹窗：拉取全部权限组与当前角色已绑定的权限组 */
function onAssignGroup(record: Role) {
  groupRole.value = record;
  groupCheckedIds.value = [];
  fs.get<PermissionGroup[]>('/permission-group/all').then((groups) => {
    allGroups.value = groups;
    fs.get<number[]>(`/role/${record.id}/groups`).then((ids) => {
      groupCheckedIds.value = ids;
      groupOpen.value = true;
    });
  });
}

/** 保存角色与权限组的绑定关系 */
function onSaveGroup() {
  if (!groupRole.value) return;
  fs.put<void>(`/role/${groupRole.value.id}/groups`, groupCheckedIds.value).then(() => {
    message.success(t('role.assignGroupSuccess'));
    groupOpen.value = false;
  });
}

onMounted(() => reload(1, 10));
</script>

<style scoped>
.header {
  margin-bottom: 16px;
  width: 100%;
  justify-content: space-between;
}
.header :deep(.ant-typography) {
  margin: 0;
}
</style>
