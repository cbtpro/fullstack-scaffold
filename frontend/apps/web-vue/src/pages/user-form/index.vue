<template>
  <a-card :title="title">
    <template #extra>
      <a-space>
        <template v-if="mode === 'detail'">
          <a-button @click="backToList">{{ t('user.backToList') }}</a-button>
          <a-button v-if="canEdit" type="primary" @click="beginEdit">{{ t('common.edit') }}</a-button>
        </template>
        <template v-else>
          <a-button :disabled="saving" @click="cancelEdit">
            {{ mode === 'edit' ? t('user.cancelEdit') : t('common.cancel') }}
          </a-button>
          <a-button type="primary" :loading="saving" @click="handleSave">{{ t('common.save') }}</a-button>
        </template>
      </a-space>
    </template>

    <a-skeleton v-if="loading" active />
    <template v-else-if="loadError">
      <a-typography-text type="danger">{{ t('user.loadFailed') }}</a-typography-text>
      <div class="error-actions">
        <a-space>
          <a-button @click="backToList">{{ t('user.backToList') }}</a-button>
          <a-button v-if="canCreate" type="primary" @click="goCreate">{{ t('common.create') }}</a-button>
        </a-space>
      </div>
    </template>

    <!-- 详情：纯文本只读展示，枚举显示名、时间格式化 -->
    <a-form v-else-if="mode === 'detail'" :label-col="{ span: 5 }" :wrapper-col="{ span: 17 }">
      <a-form-item :label="t('login.username')"><span>{{ record?.username ?? '-' }}</span></a-form-item>
      <a-form-item :label="t('user.nickname')"><span>{{ record?.nickname || '-' }}</span></a-form-item>
      <a-form-item :label="t('user.email')"><span>{{ record?.email || '-' }}</span></a-form-item>
      <a-form-item :label="t('user.phone')"><span>{{ record?.phone || '-' }}</span></a-form-item>
      <a-form-item :label="t('user.roles')">
        <span>{{ record?.roles?.length ? record.roles.join('、') : '-' }}</span>
      </a-form-item>
      <a-form-item :label="t('user.status')">
        <a-tag :color="record?.status === 1 ? 'green' : 'red'">
          {{ record?.status === 1 ? t('user.enabled') : t('user.disabled') }}
        </a-tag>
      </a-form-item>
      <a-form-item :label="t('user.createTime')"><span>{{ formatTime(record?.createTime) }}</span></a-form-item>
      <a-form-item :label="t('user.updateTime')"><span>{{ formatTime(record?.updateTime) }}</span></a-form-item>
    </a-form>

    <!-- 新建 / 编辑：共享同一份字段与校验 -->
    <a-form
      v-else
      ref="formRef"
      :model="form"
      :label-col="{ span: 5 }"
      :wrapper-col="{ span: 17 }"
      :disabled="saving"
    >
      <a-form-item
        :label="t('login.username')"
        name="username"
        :rules="[{ required: true, message: t('login.usernameRequired') }]"
      >
        <a-input v-model:value="form.username" autocomplete="off" />
      </a-form-item>
      <a-form-item :label="t('user.nickname')" name="nickname">
        <a-input v-model:value="form.nickname" autocomplete="off" />
      </a-form-item>
      <a-form-item
        :label="t('login.password')"
        name="password"
        :extra="mode === 'edit' ? t('user.passwordEditHint') : t('user.passwordHint')"
      >
        <a-input-password v-model:value="form.password" autocomplete="new-password" />
      </a-form-item>
      <a-form-item
        :label="t('user.email')"
        name="email"
        :rules="[{ type: 'email', message: t('user.emailInvalid') }]"
      >
        <a-input v-model:value="form.email" autocomplete="off" />
      </a-form-item>
      <a-form-item :label="t('user.phone')" name="phone">
        <a-input v-model:value="form.phone" autocomplete="off" />
      </a-form-item>
      <a-form-item :label="t('user.status')" name="status">
        <a-select v-model:value="form.status">
          <a-select-option :value="1">{{ t('user.enabled') }}</a-select-option>
          <a-select-option :value="0">{{ t('user.disabled') }}</a-select-option>
        </a-select>
      </a-form-item>
    </a-form>
  </a-card>
</template>

<script setup lang="ts">
/**
 * 用户三态页：新建 / 详情 / 编辑共享一份字段、校验与布局。
 * 模式由命名路由派生（user-create / user-detail / user-edit）。
 * record 为已保存快照，表单仅是其草稿：进入编辑=快照复制，取消编辑=回详情恢复快照，
 * 保存成功=以接口返回值更新快照后跳转详情。
 */
import { computed, reactive, ref, watch, watchEffect } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import type { User } from '@fs/types';
import { hasPermission } from '@fs/auth';
import { fs } from '@/request';
import { useAuthStore } from '@/store/auth';

type Mode = 'create' | 'detail' | 'edit';

const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const auth = useAuthStore();

const mode = computed<Mode>(() =>
  route.name === 'user-edit' ? 'edit' : route.name === 'user-detail' ? 'detail' : 'create',
);
const recordId = computed(() => (mode.value === 'create' ? undefined : String(route.params.id)));

const record = ref<User | null>(null);
const loading = ref(false);
const loadError = ref(false);
const saving = ref(false);
const formRef = ref();

const emptyForm = () => ({
  username: '',
  nickname: '',
  password: '',
  email: '',
  phone: '',
  status: 1,
});
const form = reactive(emptyForm());

// 拉取已保存记录；已持有同 id 快照（如保存后跳详情）不重复请求
watch(
  recordId,
  (id, _prev, onCleanup) => {
    if (id && record.value && String(record.value.id) === id) return;
    if (!id) {
      record.value = null;
      loadError.value = false;
      loading.value = false;
      return;
    }
    let active = true;
    onCleanup(() => {
      active = false;
    });
    record.value = null;
    loadError.value = false;
    loading.value = true;
    fs.get<User>(`/user/${id}`)
      .then((res) => {
        if (active) record.value = res;
      })
      .catch(() => {
        if (active) loadError.value = true;
      })
      .finally(() => {
        if (active) loading.value = false;
      });
  },
  { immediate: true },
);

// 进入编辑=快照复制到表单；进入新建=重置为初始草稿
watch([mode, record], ([m, rec]) => {
  if (m === 'create') {
    Object.assign(form, emptyForm());
  } else if (m === 'edit' && rec) {
    Object.assign(form, {
      username: rec.username,
      nickname: rec.nickname ?? '',
      password: '',
      email: rec.email ?? '',
      phone: rec.phone ?? '',
      status: rec.status ?? 1,
    });
  }
});

const canCreate = computed(() => hasPermission(auth.user?.permissions ?? [], 'user:create'));
const canEdit = computed(() => hasPermission(auth.user?.permissions ?? [], 'user:update'));

// 无权限访问对应模式时直接重定向，避免页面先渲染再被接口拒绝
watchEffect(() => {
  if (mode.value === 'create' && !canCreate.value) {
    router.replace({ name: 'user' });
  } else if (mode.value === 'edit' && !canEdit.value) {
    router.replace({ name: 'user-detail', params: { id: recordId.value ?? '' } });
  }
});

const title = computed(() =>
  mode.value === 'create'
    ? t('user.createTitle')
    : mode.value === 'edit'
      ? t('user.editTitle')
      : t('user.detailTitle'),
);

/** 后端时间形如 2026-10-09T12:34:56，转为可读文本 */
function formatTime(v?: string) {
  return v ? v.replace('T', ' ').slice(0, 19) : '-';
}

function backToList() {
  router.push({ name: 'user' });
}

function goCreate() {
  router.push({ name: 'user-create' });
}

function beginEdit() {
  router.push({ name: 'user-edit', params: { id: recordId.value ?? '' } });
}

function cancelEdit() {
  if (mode.value === 'edit') {
    router.push({ name: 'user-detail', params: { id: recordId.value ?? '' } });
  } else {
    backToList();
  }
}

async function handleSave() {
  try {
    await formRef.value?.validate();
  } catch {
    return; // 校验失败：表单已就地提示
  }
  saving.value = true;
  try {
    const saved =
      mode.value === 'edit'
        ? await fs.put<User>('/user', { ...form, id: record.value?.id })
        : await fs.post<User>('/user', { ...form });
    record.value = saved;
    message.success(t('user.saveSuccess'));
    router.push({ name: 'user-detail', params: { id: String(saved.id) } });
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped>
.error-actions {
  margin-top: 16px;
}
</style>
