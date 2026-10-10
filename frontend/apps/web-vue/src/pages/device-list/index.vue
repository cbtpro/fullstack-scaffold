<template>
  <a-card>
    <a-typography-title :level="3" class="title">{{ t('device.title') }}</a-typography-title>
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
      :data-source="data.list"
      :columns="columns"
      :pagination="pagination"
      @change="onPageChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'fingerprint'">
          {{ maskFingerprint(record.fingerprint) }}
        </template>
        <template v-else-if="column.dataIndex === 'ip'">
          {{ record.ip ?? '-' }}
        </template>
        <template v-else-if="column.dataIndex === 'riskLevel'">
          <a-tag :color="RISK_COLORS[record.riskLevel as DeviceRiskLevel]">
            {{ t(`device.${record.riskLevel.toLowerCase()}`) }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'lastLoginTime'">
          {{ record.lastLoginTime ?? '-' }}
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ record.createTime ?? '-' }}
        </template>
        <template v-else-if="column.key === '__op'">
          <a-space>
            <Permission codes="device:trust" fallback="hide" :custom-fn="canTrust" :extra="record">
              <a-popconfirm
                :title="t('device.trustConfirm')"
                :ok-text="t('common.confirm')"
                :cancel-text="t('common.cancel')"
                @confirm="onTrust(record)"
              >
                <a-button size="small">{{ t('device.trust') }}</a-button>
              </a-popconfirm>
            </Permission>
            <Permission codes="device:revoke" fallback="hide" :custom-fn="canRevoke" :extra="record">
              <a-popconfirm
                :title="t('device.revokeConfirm')"
                :ok-text="t('common.confirm')"
                :cancel-text="t('common.cancel')"
                @confirm="onRevoke(record)"
              >
                <a-button danger size="small">{{ t('device.revoke') }}</a-button>
              </a-popconfirm>
            </Permission>
            <Permission codes="device:remove" :extra="record">
              <a-popconfirm
                :title="t('device.removeConfirm')"
                :ok-text="t('common.confirm')"
                :cancel-text="t('common.cancel')"
                @confirm="onRemove(record)"
              >
                <a-button danger size="small">{{ t('device.remove') }}</a-button>
              </a-popconfirm>
            </Permission>
          </a-space>
        </template>
      </template>
    </a-table>
  </a-card>
</template>

<script setup lang="ts">
/**
 * 登录设备列表页：查看当前用户的常用登录设备，支持设为可信/吊销/移除。
 * 设备由登录自动登记，无新增入口。操作权限走 Permission 组件：
 * 已可信的设备不显示"设为可信"，已吊销的不显示"吊销"，避免无效操作。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { message } from 'ant-design-vue';
import type { DeviceRiskLevel, PageResult, UserDevice } from '@fs/types';
import { fs } from '@/request';
import Permission from '@/components/permission/Permission.vue';
import ImeInput from '@/components/ImeInput.vue';
import { useDebounceSearch } from '@/composables/useDebounceSearch';

const { t } = useI18n();
const loading = ref(false);
const data = reactive<PageResult<UserDevice>>({
  list: [],
  total: 0,
  pageNum: 1,
  pageSize: 10,
});

const RISK_COLORS: Record<DeviceRiskLevel, string> = {
  TRUSTED: 'green',
  NORMAL: 'blue',
  RISKY: 'orange',
  REVOKED: 'red',
};

const columns = computed(() => [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: t('device.fingerprint'), dataIndex: 'fingerprint' },
  { title: t('device.ip'), dataIndex: 'ip', width: 150 },
  { title: t('device.riskLevel'), dataIndex: 'riskLevel', width: 100 },
  { title: t('device.lastLoginTime'), dataIndex: 'lastLoginTime', width: 180 },
  { title: t('device.createTime'), dataIndex: 'createTime', width: 180 },
  { title: t('common.operation'), key: '__op', width: 220 },
]);

const pagination = computed(() => ({
  total: data.total,
  current: data.pageNum,
  pageSize: data.pageSize,
  showTotal: (total: number) => t('user.total', { count: total }),
}));

function maskFingerprint(fp: string): string {
  if (!fp || fp.length <= 16) return fp;
  return `${fp.slice(0, 8)}…${fp.slice(-8)}`;
}

function canTrust(_roles: string[], extra?: unknown) {
  return (extra as UserDevice | undefined)?.riskLevel !== 'TRUSTED';
}

function canRevoke(_roles: string[], extra?: unknown) {
  return (extra as UserDevice | undefined)?.riskLevel !== 'REVOKED';
}

// 搜索关键词与防抖调度
const keyword = ref('');

const { scheduleSearch, cancelSearch } = useDebounceSearch({
  getValues: () => ({ keyword: keyword.value }),
  onSearch: ({ keyword: kw }) => reload(1, data.pageSize, kw),
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

function reload(page = 1, size = 10, kw = keyword.value) {
  loading.value = true;
  fs.get<PageResult<UserDevice>>('/device/list', { params: { page, size, keyword: kw } })
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

function onTrust(record: UserDevice) {
  fs.put<void>(`/device/${record.id}/trust`).then(() => {
    message.success(t('device.trustSuccess'));
    reload(data.pageNum, data.pageSize);
  });
}

function onRevoke(record: UserDevice) {
  fs.put<void>(`/device/${record.id}/revoke`).then(() => {
    message.success(t('device.revokeSuccess'));
    reload(data.pageNum, data.pageSize);
  });
}

function onRemove(record: UserDevice) {
  fs.delete<void>(`/device/${record.id}`).then(() => {
    message.success(t('device.removeSuccess'));
    reload(data.pageNum, data.pageSize);
  });
}

onMounted(() => reload(1, 10));
</script>

<style scoped>
.title {
  margin: 0 0 16px;
}
</style>
