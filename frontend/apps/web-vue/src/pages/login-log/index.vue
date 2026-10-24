<template>
  <a-card>
    <a-typography-title :level="3" class="title">{{ t('loginLog.title') }}</a-typography-title>
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
      <a-select :value="eventFilter" style="width: 140px" :options="eventOptions" @change="onEventChange" />
      <a-select
        :value="resultFilter"
        style="width: 140px"
        :options="resultOptions"
        @change="onResultChange"
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
        <template v-if="column.dataIndex === 'eventType'">
          {{ record.eventType === 'LOGOUT' ? t('loginLog.eventLogout') : t('loginLog.eventLogin') }}
        </template>
        <template v-else-if="column.dataIndex === 'success'">
          <a-tag :color="record.success ? 'green' : 'red'">
            {{ record.success ? t('loginLog.success') : t('loginLog.failed') }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'message'">
          {{ record.message ?? '-' }}
        </template>
        <template v-else-if="column.dataIndex === 'clientIp'">
          {{ record.clientIp ?? '-' }}
        </template>
        <template v-else-if="column.dataIndex === 'userAgent'">
          {{ record.userAgent ?? '-' }}
        </template>
      </template>
    </a-table>
  </a-card>
</template>

<script setup lang="ts">
/**
 * 登录日志页：查询登录/登出审计记录。日志由登录入口写入、只读，
 * 页面只提供检索与事件/结果筛选。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import type { LoginLog, PageResult } from '@fs/types';
import { fs } from '@/request';
import ImeInput from '@/components/ImeInput.vue';
import { useDebounceSearch } from '@/composables/useDebounceSearch';

/** 结果筛选：'ALL' 表示不加条件 */
type ResultFilter = 'ALL' | 'SUCCESS' | 'FAIL';
/** 事件筛选：'' 表示不加条件 */
type EventFilter = '' | 'LOGIN' | 'LOGOUT';

const { t } = useI18n();
const loading = ref(false);
const data = reactive<PageResult<LoginLog>>({
  list: [],
  total: 0,
  pageNum: 1,
  pageSize: 10,
});

const columns = computed(() => [
  { title: t('loginLog.username'), dataIndex: 'username', width: 140 },
  { title: t('loginLog.eventType'), dataIndex: 'eventType', width: 110 },
  { title: t('loginLog.result'), dataIndex: 'success', width: 90 },
  { title: t('loginLog.message'), dataIndex: 'message', ellipsis: true },
  { title: t('loginLog.clientIp'), dataIndex: 'clientIp', width: 140 },
  { title: t('loginLog.userAgent'), dataIndex: 'userAgent', ellipsis: true },
  { title: t('loginLog.createTime'), dataIndex: 'createTime', width: 180 },
]);

const pagination = computed(() => ({
  total: data.total,
  current: data.pageNum,
  pageSize: data.pageSize,
  showTotal: (total: number) => t('user.total', { count: total }),
}));

const eventOptions = computed(() => [
  { value: '', label: t('loginLog.allEvent') },
  { value: 'LOGIN', label: t('loginLog.eventLogin') },
  { value: 'LOGOUT', label: t('loginLog.eventLogout') },
]);

const resultOptions = computed(() => [
  { value: 'ALL', label: t('loginLog.allResult') },
  { value: 'SUCCESS', label: t('loginLog.success') },
  { value: 'FAIL', label: t('loginLog.failed') },
]);

// 搜索关键词与事件/结果筛选，值在执行时实时读取，避免依赖输入快照
const keyword = ref('');
const eventFilter = ref<EventFilter>('');
const resultFilter = ref<ResultFilter>('ALL');

const { scheduleSearch, cancelSearch } = useDebounceSearch({
  getValues: () => ({
    keyword: keyword.value,
    event: eventFilter.value,
    result: resultFilter.value,
  }),
  onSearch: ({ keyword: kw, event, result }) => reload(1, data.pageSize, kw, event, result),
  delay: 300,
});

function onKeywordChange(v: string) {
  keyword.value = v;
  scheduleSearch();
}

function onEventChange(v: EventFilter) {
  eventFilter.value = v;
  scheduleSearch({ force: true });
}

function onResultChange(v: ResultFilter) {
  resultFilter.value = v;
  scheduleSearch({ force: true });
}

function onReset() {
  keyword.value = '';
  eventFilter.value = '';
  resultFilter.value = 'ALL';
  scheduleSearch({ force: true });
}

function reload(
  page = 1,
  size = 10,
  kw = keyword.value,
  event = eventFilter.value,
  result = resultFilter.value,
) {
  loading.value = true;
  fs.get<PageResult<LoginLog>>('/login-log/list', {
    params: {
      page,
      size,
      keyword: kw,
      eventType: event || undefined,
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
</style>
