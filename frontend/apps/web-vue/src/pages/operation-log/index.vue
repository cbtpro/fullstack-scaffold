<template>
  <a-card>
    <a-typography-title :level="3" class="title">{{ t('operationLog.title') }}</a-typography-title>
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
      <a-select
        :value="resultFilter"
        style="width: 160px"
        :options="resultOptions"
        @change="onFilterChange"
      />
      <a-button type="primary" @click="scheduleSearch({ force: true })">{{ t('common.search') }}</a-button>
      <a-button @click="onReset">{{ t('common.reset') }}</a-button>
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
        <template v-if="column.dataIndex === 'operatorName'">
          {{ record.operatorName ?? '-' }}
        </template>
        <template v-else-if="column.dataIndex === 'clientIp'">
          {{ record.clientIp ?? '-' }}
        </template>
        <template v-else-if="column.dataIndex === 'success'">
          <a-tag :color="record.success ? 'green' : 'red'">
            {{ record.success ? t('operationLog.success') : t('operationLog.failed') }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'durationMs'">
          {{
            record.durationMs == null ? '-' : t('operationLog.durationValue', { value: record.durationMs })
          }}
        </template>
      </template>
      <template #expandedRowRender="{ record }">
        <a-space direction="vertical" :size="4" style="width: 100%">
          <div>
            <a-typography-text type="secondary">{{ t('operationLog.uri') }}: </a-typography-text>
            <a-typography-text>{{ `${record.httpMethod ?? ''} ${record.uri ?? '-'}` }}</a-typography-text>
          </div>
          <div>
            <a-typography-text type="secondary">{{ t('operationLog.params') }}: </a-typography-text>
            <pre class="log-params">{{ record.params ?? '-' }}</pre>
          </div>
          <div v-if="!record.success">
            <a-typography-text type="secondary">{{ t('operationLog.errorMessage') }}: </a-typography-text>
            <a-typography-text type="danger">{{ record.errorMessage ?? '-' }}</a-typography-text>
          </div>
        </a-space>
      </template>
    </a-table>
  </a-card>
</template>

<script setup lang="ts">
/**
 * 操作日志页：查询审计记录。日志由后端切面写入、只读，
 * 页面只提供检索与结果筛选，参数快照通过展开行查看。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import type { OperationLog, PageResult } from '@fs/types';
import { fs } from '@/request';
import ImeInput from '@/components/ImeInput.vue';
import { useDebounceSearch } from '@/composables/useDebounceSearch';

/** 结果筛选：'ALL' 表示不加条件 */
type ResultFilter = 'ALL' | 'SUCCESS' | 'FAIL';

const { t } = useI18n();
const loading = ref(false);
const data = reactive<PageResult<OperationLog>>({
  list: [],
  total: 0,
  pageNum: 1,
  pageSize: 10,
});

const columns = computed(() => [
  { title: t('operationLog.module'), dataIndex: 'module', width: 120 },
  { title: t('operationLog.action'), dataIndex: 'action', width: 120 },
  { title: t('operationLog.description'), dataIndex: 'description', ellipsis: true },
  { title: t('operationLog.operator'), dataIndex: 'operatorName', width: 120 },
  { title: t('operationLog.clientIp'), dataIndex: 'clientIp', width: 140 },
  { title: t('operationLog.result'), dataIndex: 'success', width: 90 },
  { title: t('operationLog.duration'), dataIndex: 'durationMs', width: 100 },
  { title: t('operationLog.createTime'), dataIndex: 'createTime', width: 180 },
]);

const pagination = computed(() => ({
  total: data.total,
  current: data.pageNum,
  pageSize: data.pageSize,
  showTotal: (total: number) => t('user.total', { count: total }),
}));

const resultOptions = computed(() => [
  { value: 'ALL', label: t('operationLog.allResult') },
  { value: 'SUCCESS', label: t('operationLog.success') },
  { value: 'FAIL', label: t('operationLog.failed') },
]);

// 搜索关键词与结果筛选，值在执行时实时读取，避免依赖输入快照
const keyword = ref('');
const resultFilter = ref<ResultFilter>('ALL');

const { scheduleSearch, cancelSearch } = useDebounceSearch({
  getValues: () => ({ keyword: keyword.value, result: resultFilter.value }),
  onSearch: ({ keyword: kw, result }) => reload(1, data.pageSize, kw, result),
  delay: 300,
});

function onKeywordChange(v: string) {
  keyword.value = v;
  scheduleSearch();
}

function onFilterChange(v: ResultFilter) {
  resultFilter.value = v;
  scheduleSearch({ force: true });
}

function onReset() {
  keyword.value = '';
  resultFilter.value = 'ALL';
  scheduleSearch({ force: true });
}

function reload(page = 1, size = 10, kw = keyword.value, result = resultFilter.value) {
  loading.value = true;
  fs.get<PageResult<OperationLog>>('/operation-log/list', {
    params: {
      page,
      size,
      keyword: kw,
      // 后端 success 为可空布尔，'ALL' 时不传该参数
      success: result === 'ALL' ? undefined : result === 'SUCCESS',
    },
  })
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
.title {
  margin: 0 0 16px;
}

.log-params {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
