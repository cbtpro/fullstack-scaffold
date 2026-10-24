<template>
  <a-card>
    <a-typography-title :level="3" class="title">{{ t('dict.title') }}</a-typography-title>
    <a-row :gutter="16">
      <!-- 左栏：字典类型 -->
      <a-col :span="10">
        <a-card size="small" :title="t('dict.types')">
          <template #extra>
            <Permission codes="dict-type:create">
              <a-button type="primary" size="small" @click="onCreateType">{{ t('common.create') }}</a-button>
            </Permission>
          </template>
          <a-table
            row-key="id"
            size="small"
            :loading="typesLoading"
            :data-source="types.list"
            :columns="typeColumns"
            :pagination="typesPagination"
            :row-class-name="typeRowClassName"
            :custom-row="typeRow"
            @change="onTypePageChange"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === '__op'">
                <a-space>
                  <Permission codes="dict-type:update" fallback="disable">
                    <a-button size="small" @click="onEditType(record)">{{ t('common.edit') }}</a-button>
                  </Permission>
                  <Permission codes="dict-type:delete">
                    <a-popconfirm
                      :title="t('dict.deleteTypeConfirm')"
                      :ok-text="t('common.confirm')"
                      :cancel-text="t('common.cancel')"
                      @confirm="onDeleteType(record)"
                    >
                      <a-button danger size="small">{{ t('common.delete') }}</a-button>
                    </a-popconfirm>
                  </Permission>
                </a-space>
              </template>
            </template>
          </a-table>
        </a-card>
      </a-col>
      <!-- 右栏：所选类型下的字典项 -->
      <a-col :span="14">
        <a-card size="small" :title="itemsTitle">
          <template #extra>
            <Permission codes="dict-item:create">
              <a-button type="primary" size="small" :disabled="!selectedType" @click="onCreateItem">
                {{ t('common.create') }}
              </a-button>
            </Permission>
          </template>
          <a-table
            row-key="id"
            size="small"
            :loading="itemsLoading"
            :data-source="items.list"
            :columns="itemColumns"
            :pagination="itemsPagination"
            :locale="itemsLocale"
            @change="onItemPageChange"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'status'">
                <a-tag v-if="record.status === 1" color="green">{{ t('dict.enabled') }}</a-tag>
                <a-tag v-else>{{ t('dict.disabled') }}</a-tag>
              </template>
              <template v-else-if="column.key === '__op'">
                <a-space>
                  <Permission codes="dict-item:update" fallback="disable">
                    <a-button size="small" @click="onEditItem(record)">{{ t('common.edit') }}</a-button>
                  </Permission>
                  <Permission codes="dict-item:delete">
                    <a-popconfirm
                      :title="t('dict.deleteItemConfirm')"
                      :ok-text="t('common.confirm')"
                      :cancel-text="t('common.cancel')"
                      @confirm="onDeleteItem(record)"
                    >
                      <a-button danger size="small">{{ t('common.delete') }}</a-button>
                    </a-popconfirm>
                  </Permission>
                </a-space>
              </template>
            </template>
          </a-table>
        </a-card>
      </a-col>
    </a-row>

    <!-- 字典类型表单 -->
    <a-modal
      :title="editingType ? t('dict.editTypeTitle') : t('dict.createTypeTitle')"
      :open="typeOpen"
      destroy-on-close
      @ok="onSaveType"
      @cancel="typeOpen = false"
    >
      <a-form ref="typeFormRef" :model="typeForm" :rules="typeRules" layout="vertical">
        <a-form-item :label="t('dict.code')" name="code">
          <a-input v-model:value="typeForm.code" :disabled="!!editingType" />
        </a-form-item>
        <a-form-item :label="t('dict.name')" name="name">
          <a-input v-model:value="typeForm.name" />
        </a-form-item>
        <a-form-item :label="t('dict.sort')" name="sort">
          <a-input-number v-model:value="typeForm.sort" :min="0" style="width: 100%" />
        </a-form-item>
        <a-form-item :label="t('dict.status')" name="status">
          <a-select v-model:value="typeForm.status" :options="statusOptions" />
        </a-form-item>
        <a-form-item :label="t('dict.description')" name="description">
          <a-textarea v-model:value="typeForm.description" :rows="3" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 字典项表单 -->
    <a-modal
      :title="editingItem ? t('dict.editItemTitle') : t('dict.createItemTitle')"
      :open="itemOpen"
      destroy-on-close
      @ok="onSaveItem"
      @cancel="itemOpen = false"
    >
      <a-form ref="itemFormRef" :model="itemForm" :rules="itemRules" layout="vertical">
        <a-form-item :label="t('dict.label')" name="label">
          <a-input v-model:value="itemForm.label" />
        </a-form-item>
        <a-form-item :label="t('dict.value')" name="value">
          <a-input v-model:value="itemForm.value" />
        </a-form-item>
        <a-form-item :label="t('dict.sort')" name="sort">
          <a-input-number v-model:value="itemForm.sort" :min="0" style="width: 100%" />
        </a-form-item>
        <a-form-item :label="t('dict.status')" name="status">
          <a-select v-model:value="itemForm.status" :options="statusOptions" />
        </a-form-item>
        <a-form-item :label="t('dict.remark')" name="remark">
          <a-textarea v-model:value="itemForm.remark" :rows="3" />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-card>
</template>

<script setup lang="ts">
/**
 * 字典管理页：左侧字典类型、右侧该类型下的字典项。
 * 两类数据各自独立增删改，但以「类型 → 项」的从属关系呈现，
 * 选中左侧类型后右侧才可操作，避免字典项脱离类型被孤立维护。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { message } from 'ant-design-vue';
import type { DictItem, DictType, PageResult } from '@fs/types';
import { fs } from '@/request';
import Permission from '@/components/permission/Permission.vue';

const { t } = useI18n();

const types = reactive<PageResult<DictType>>({ list: [], total: 0, pageNum: 1, pageSize: 10 });
const typesLoading = ref(false);
const selectedType = ref<DictType | null>(null);

const items = reactive<PageResult<DictItem>>({ list: [], total: 0, pageNum: 1, pageSize: 10 });
const itemsLoading = ref(false);

const typeOpen = ref(false);
const editingType = ref<DictType | null>(null);
const typeFormRef = ref();
const typeForm = reactive<Record<string, unknown>>({
  code: '',
  name: '',
  description: '',
  status: 1,
  sort: 0,
});

const itemOpen = ref(false);
const editingItem = ref<DictItem | null>(null);
const itemFormRef = ref();
const itemForm = reactive<Record<string, unknown>>({
  label: '',
  value: '',
  remark: '',
  status: 1,
  sort: 0,
});

const statusOptions = computed(() => [
  { value: 1, label: t('dict.enabled') },
  { value: 0, label: t('dict.disabled') },
]);

const typeRules = computed(() => ({
  code: [{ required: true, message: t('dict.codeRequired') }],
  name: [{ required: true, message: t('dict.nameRequired') }],
}));

const itemRules = computed(() => ({
  label: [{ required: true, message: t('dict.labelRequired') }],
  value: [{ required: true, message: t('dict.valueRequired') }],
}));

const typeColumns = computed(() => [
  { title: t('dict.code'), dataIndex: 'code', width: 150 },
  { title: t('dict.name'), dataIndex: 'name', ellipsis: true },
  { title: t('dict.sort'), dataIndex: 'sort', width: 70 },
  { title: t('common.operation'), key: '__op', width: 150 },
]);

const itemColumns = computed(() => [
  { title: t('dict.label'), dataIndex: 'label', ellipsis: true },
  { title: t('dict.value'), dataIndex: 'value', ellipsis: true },
  { title: t('dict.sort'), dataIndex: 'sort', width: 70 },
  { title: t('dict.status'), dataIndex: 'status', width: 90 },
  { title: t('common.operation'), key: '__op', width: 150 },
]);

const typesPagination = computed(() => ({
  total: types.total,
  current: types.pageNum,
  pageSize: types.pageSize,
  showTotal: (total: number) => t('user.total', { count: total }),
}));

const itemsPagination = computed(() => ({
  total: items.total,
  current: items.pageNum,
  pageSize: items.pageSize,
  showTotal: (total: number) => t('user.total', { count: total }),
}));

const itemsTitle = computed(() =>
  selectedType.value ? `${t('dict.items')} - ${selectedType.value.name}` : t('dict.items'),
);

// 未选类型时不展示空数据文案，提示先选类型
const itemsLocale = computed(() => ({
  emptyText: selectedType.value ? undefined : t('dict.selectTypeFirst'),
}));

function loadTypes(page = 1, size = 10) {
  typesLoading.value = true;
  fs.get<PageResult<DictType>>('/dict-type/list', { params: { page, size } })
    .then((res) => {
      types.list = res.list;
      types.total = res.total;
      types.pageNum = res.pageNum;
      types.pageSize = res.pageSize;
    })
    .finally(() => {
      typesLoading.value = false;
    });
}

function loadItems(typeId: string | number, page = 1, size = 10) {
  itemsLoading.value = true;
  fs.get<PageResult<DictItem>>('/dict-item/list', { params: { page, size, typeId } })
    .then((res) => {
      items.list = res.list;
      items.total = res.total;
      items.pageNum = res.pageNum;
      items.pageSize = res.pageSize;
    })
    .finally(() => {
      itemsLoading.value = false;
    });
}

// 点击左侧类型行：右栏切换到该类型的字典项
function onSelectType(record: DictType) {
  selectedType.value = record;
  loadItems(record.id, 1, 10);
}

function typeRowClassName(record: DictType) {
  return selectedType.value?.id === record.id ? 'dict-type-selected' : '';
}

function typeRow(record: DictType) {
  return { onClick: () => onSelectType(record) };
}

function onTypePageChange(p: { current?: number; pageSize?: number }) {
  loadTypes(p.current ?? 1, p.pageSize ?? 10);
}

function onItemPageChange(p: { current?: number; pageSize?: number }) {
  if (selectedType.value) loadItems(selectedType.value.id, p.current ?? 1, p.pageSize ?? 10);
}

// ------------------------------------------------------------------ 字典类型
function resetTypeForm() {
  typeForm.code = '';
  typeForm.name = '';
  typeForm.description = '';
  typeForm.status = 1;
  typeForm.sort = 0;
  typeFormRef.value?.clearValidate?.();
}

function onCreateType() {
  editingType.value = null;
  resetTypeForm();
  typeOpen.value = true;
}

function onEditType(record: DictType) {
  editingType.value = record;
  typeForm.code = record.code;
  typeForm.name = record.name;
  typeForm.description = record.description ?? '';
  typeForm.status = record.status ?? 1;
  typeForm.sort = record.sort ?? 0;
  typeFormRef.value?.clearValidate?.();
  typeOpen.value = true;
}

function onSaveType() {
  typeFormRef.value
    .validate()
    .then(() => {
      const payload = { ...typeForm };
      return editingType.value
        ? fs.put<DictType>('/dict-type', { ...payload, id: editingType.value.id })
        : fs.post<DictType>('/dict-type', payload);
    })
    .then(() => {
      message.success(t('dict.saveSuccess'));
      typeOpen.value = false;
      loadTypes(types.pageNum, types.pageSize);
    })
    .catch(() => {
      /* 校验失败或接口错误：拦截器已提示，停留弹窗 */
    });
}

function onDeleteType(record: DictType) {
  fs.delete<void>(`/dict-type/${record.id}`).then(() => {
    message.success(t('dict.deleteSuccess'));
    // 被删类型正是当前选中项时，清空右栏，避免展示已不存在的字典项
    if (selectedType.value?.id === record.id) {
      selectedType.value = null;
      items.list = [];
      items.total = 0;
      items.pageNum = 1;
      items.pageSize = 10;
    }
    loadTypes(types.pageNum, types.pageSize);
  });
}

// ------------------------------------------------------------------ 字典项
function resetItemForm() {
  itemForm.label = '';
  itemForm.value = '';
  itemForm.remark = '';
  itemForm.status = 1;
  itemForm.sort = 0;
  itemFormRef.value?.clearValidate?.();
}

function onCreateItem() {
  editingItem.value = null;
  resetItemForm();
  itemOpen.value = true;
}

function onEditItem(record: DictItem) {
  editingItem.value = record;
  itemForm.label = record.label;
  itemForm.value = record.value;
  itemForm.remark = record.remark ?? '';
  itemForm.status = record.status ?? 1;
  itemForm.sort = record.sort ?? 0;
  itemFormRef.value?.clearValidate?.();
  itemOpen.value = true;
}

function onSaveItem() {
  if (!selectedType.value) return;
  const typeId = selectedType.value.id;
  itemFormRef.value
    .validate()
    .then(() => {
      const payload = { ...itemForm, typeId };
      return editingItem.value
        ? fs.put<DictItem>('/dict-item', { ...payload, id: editingItem.value.id })
        : fs.post<DictItem>('/dict-item', payload);
    })
    .then(() => {
      message.success(t('dict.saveSuccess'));
      itemOpen.value = false;
      loadItems(typeId, items.pageNum, items.pageSize);
    })
    .catch(() => {
      /* 校验失败或接口错误：拦截器已提示，停留弹窗 */
    });
}

function onDeleteItem(record: DictItem) {
  fs.delete<void>(`/dict-item/${record.id}`).then(() => {
    message.success(t('dict.deleteSuccess'));
    if (selectedType.value) loadItems(selectedType.value.id, items.pageNum, items.pageSize);
  });
}

onMounted(() => loadTypes(1, 10));
</script>

<style scoped>
.title {
  margin: 0 0 16px;
}
</style>
