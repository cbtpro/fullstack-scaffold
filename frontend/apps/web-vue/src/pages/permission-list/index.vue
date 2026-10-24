<template>
  <a-card>
    <a-space class="header">
      <a-typography-title :level="3">{{ t('permission.title') }}</a-typography-title>
      <Permission codes="permission:create">
        <a-button type="primary" @click="onCreate()">{{ t('common.create') }}</a-button>
      </Permission>
    </a-space>
    <a-space style="margin-bottom: 16px">
      <ImeInput
        :value="keyword"
        @update:value="onKeywordChange"
        :placeholder="t('common.searchPlaceholder')"
        allow-clear
        style="width: 240px"
        @compositionstart="cancelSearch"
        @press-enter="scheduleSearch({ force: true })"
      />
      <a-button type="primary" @click="scheduleSearch({ force: true })">{{ t('common.search') }}</a-button>
      <a-button @click="onReset">{{ t('common.reset') }}</a-button>
    </a-space>
    <a-table
      row-key="id"
      size="middle"
      :loading="loading"
      :data-source="treeData"
      :columns="columns"
      :pagination="false"
      :expandable="{ defaultExpandAllRows: true }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'type'">
          <a-tag :color="typeColors[record.type]">
            {{ t(`permission.type${record.type === 1 ? 'Menu' : record.type === 2 ? 'Button' : 'Api'}`) }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'path'">
          {{ record.path ?? '-' }}
        </template>
        <template v-else-if="column.key === '__op'">
          <a-space>
            <Permission codes="permission:update" fallback="disable">
              <a-button size="small" @click="onEdit(record)">{{ t('common.edit') }}</a-button>
            </Permission>
            <Permission codes="permission:delete">
              <a-popconfirm
                :title="t('permission.deleteConfirm')"
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

    <a-modal
      :title="editing ? t('permission.editTitle') : t('permission.createTitle')"
      v-model:open="formOpen"
      @ok="onSave"
      width="560px"
      destroy-on-close
    >
      <a-form ref="formRef" :model="form" layout="vertical">
        <a-form-item :label="t('permission.parentId')" name="parentId">
          <a-select v-model:value="form.parentId" :options="parentOptions" />
        </a-form-item>
        <a-form-item
          :label="t('permission.type')"
          name="type"
          :rules="[{ required: true, message: t('permission.typeRequired') }]"
        >
          <a-select v-model:value="form.type">
            <a-select-option :value="1">{{ t('permission.typeMenu') }}</a-select-option>
            <a-select-option :value="2">{{ t('permission.typeButton') }}</a-select-option>
            <a-select-option :value="3">{{ t('permission.typeApi') }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item
          :label="t('permission.code')"
          name="code"
          :rules="[{ required: true, message: t('permission.codeRequired') }]"
        >
          <a-input v-model:value="form.code" />
        </a-form-item>
        <a-form-item
          :label="t('permission.name')"
          name="name"
          :rules="[{ required: true, message: t('permission.nameRequired') }]"
        >
          <a-input v-model:value="form.name" />
        </a-form-item>
        <a-form-item :label="t('permission.path')" name="path">
          <a-input v-model:value="form.path" />
        </a-form-item>
        <a-form-item :label="t('permission.component')" name="component">
          <a-input v-model:value="form.component" />
        </a-form-item>
        <a-form-item :label="t('permission.icon')" name="icon">
          <a-input v-model:value="form.icon" />
        </a-form-item>
        <a-form-item :label="t('permission.sort')" name="sort">
          <a-input-number v-model:value="form.sort" :min="0" style="width: 100%" />
        </a-form-item>
        <a-form-item :label="t('permission.status')" name="status">
          <a-select v-model:value="form.status">
            <a-select-option :value="1">{{ t('user.enabled') }}</a-select-option>
            <a-select-option :value="0">{{ t('user.disabled') }}</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
  </a-card>
</template>

<script setup lang="ts">
/**
 * 权限管理页：树形表格 + 新增/编辑/删除弹窗。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { message } from 'ant-design-vue';
import type { ID, Permission as PermissionItem } from '@fs/types';
import { fs } from '@/request';
import Permission from '@/components/permission/Permission.vue';
import ImeInput from '@/components/ImeInput.vue';
import { useDebounceSearch } from '@/composables/useDebounceSearch';

const { t } = useI18n();
const loading = ref(false);
const list = ref<PermissionItem[]>([]);

// 搜索关键词与防抖调度（前端过滤，不请求后端）
const keyword = ref('');
const displayKeyword = ref('');

const { scheduleSearch, cancelSearch } = useDebounceSearch({
  getValues: () => ({ keyword: keyword.value }),
  onSearch: ({ keyword: kw }) => {
    displayKeyword.value = kw;
  },
  delay: 300,
});

function onKeywordChange(v: string) {
  keyword.value = v;
  scheduleSearch();
}

function onReset() {
  keyword.value = '';
  scheduleSearch({ force: true });
}

const formOpen = ref(false);
const editing = ref<PermissionItem | null>(null);
const formRef = ref();
const form = reactive({
  parentId: 0,
  type: 1,
  code: '',
  name: '',
  path: '',
  component: '',
  icon: '',
  sort: 0,
  status: 1,
});

const treeData = computed(() => {
  const kw = displayKeyword.value.trim().toLowerCase();
  const filtered = kw
    ? list.value.filter((p) => p.name.toLowerCase().includes(kw) || p.code.toLowerCase().includes(kw))
    : list.value;
  return buildTree(filtered);
});
const parentOptions = computed(() => [
  { value: 0, label: t('permission.root') },
  ...list.value.map((p) => ({ value: p.id, label: `${p.name} (${p.code})` })),
]);

const typeColors: Record<number, string> = { 1: 'blue', 2: 'green', 3: 'orange' };

const columns = computed(() => [
  { title: t('permission.name'), dataIndex: 'name' },
  { title: t('permission.code'), dataIndex: 'code', width: 200 },
  { title: t('permission.type'), dataIndex: 'type', width: 100 },
  { title: t('permission.path'), dataIndex: 'path', width: 140 },
  { title: t('permission.sort'), dataIndex: 'sort', width: 70 },
  { title: t('common.operation'), key: '__op', width: 200 },
]);

function buildTree(items: PermissionItem[]): PermissionItem[] {
  const map = new Map<ID, PermissionItem>();
  const roots: PermissionItem[] = [];
  items.forEach((p) => map.set(p.id, { ...p, children: [] }));
  items.forEach((p) => {
    const node = map.get(p.id)!;
    if (p.parentId && map.has(p.parentId)) {
      map.get(p.parentId)!.children!.push(node);
    } else {
      roots.push(node);
    }
  });
  return roots;
}

function reload() {
  loading.value = true;
  fs.get<PermissionItem[]>('/permission/list')
    .then((res) => (list.value = res))
    .finally(() => (loading.value = false));
}

function onCreate(parentId?: number) {
  editing.value = null;
  Object.assign(form, {
    parentId: parentId ?? 0,
    type: 1,
    code: '',
    name: '',
    path: '',
    component: '',
    icon: '',
    sort: 0,
    status: 1,
  });
  formOpen.value = true;
}

function onEdit(record: PermissionItem) {
  editing.value = record;
  Object.assign(form, {
    parentId: record.parentId,
    type: record.type,
    code: record.code,
    name: record.name,
    path: record.path ?? '',
    component: record.component ?? '',
    icon: record.icon ?? '',
    sort: record.sort ?? 0,
    status: record.status ?? 1,
  });
  formOpen.value = true;
}

async function onSave() {
  await formRef.value?.validate();
  if (editing.value) {
    await fs.put<PermissionItem>('/permission', { ...form, id: editing.value.id });
  } else {
    await fs.post<PermissionItem>('/permission', { ...form });
  }
  message.success(t('permission.saveSuccess'));
  formOpen.value = false;
  reload();
}

function onDelete(record: PermissionItem) {
  fs.delete<void>(`/permission/${record.id}`).then(() => {
    message.success(t('permission.deleteSuccess'));
    reload();
  });
}

onMounted(() => reload());
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
