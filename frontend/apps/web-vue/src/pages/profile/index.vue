<template>
  <a-card>
    <a-typography-title :level="3" class="title">{{ t('profile.title') }}</a-typography-title>
    <a-skeleton v-if="loading" active />
    <template v-else>
      <a-descriptions :column="1" bordered size="small" style="margin-bottom: 24px">
        <a-descriptions-item :label="t('profile.username')">
          {{ profile?.username ?? '-' }}
        </a-descriptions-item>
      </a-descriptions>
      <a-typography-title :level="5">{{ t('profile.basicInfo') }}</a-typography-title>
      <a-form ref="formRef" :model="form" :rules="rules" layout="vertical" style="max-width: 420px">
        <a-form-item :label="t('profile.nickname')" name="nickname">
          <a-input v-model:value="form.nickname" :maxlength="32" />
        </a-form-item>
        <a-form-item :label="t('profile.email')" name="email">
          <a-input v-model:value="form.email" :maxlength="64" />
        </a-form-item>
        <a-form-item :label="t('profile.phone')" name="phone">
          <a-input v-model:value="form.phone" :maxlength="20" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" :loading="saving" @click="onSave">{{ t('common.save') }}</a-button>
          <a-button style="margin-left: 8px" @click="onReset">{{ t('profile.reset') }}</a-button>
        </a-form-item>
      </a-form>
    </template>
  </a-card>
</template>

<script setup lang="ts">
/**
 * 个人中心页：查看与维护本人资料（昵称、邮箱、手机）。
 * 用户名只读展示；可改字段与后端 ProfileUpdateRequest 一一对应，
 * userId 由后端从 token 解析，前端不传。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { message } from 'ant-design-vue';
import type { User } from '@fs/types';
import { fs } from '@/request';
import { useAuthStore } from '@/store/auth';

const { t } = useI18n();
const auth = useAuthStore();

const profile = ref<User | null>(null);
const loading = ref(true);
const saving = ref(false);
const formRef = ref();
const form = reactive({ nickname: '', email: '', phone: '' });

const rules = computed(() => ({
  email: [{ type: 'email', message: t('profile.emailInvalid') }],
}));

function applyForm(data: User) {
  form.nickname = data.nickname ?? '';
  form.email = data.email ?? '';
  form.phone = data.phone ?? '';
}

onMounted(() => {
  fs.get<User>('/user/profile')
    .then((data) => {
      profile.value = data;
      applyForm(data);
    })
    .finally(() => {
      loading.value = false;
    });
});

function onSave() {
  formRef.value
    .validate()
    .then(() => {
      saving.value = true;
      return fs.put<User>('/user/profile', { ...form });
    })
    .then((updated: User) => {
      profile.value = updated;
      applyForm(updated);
      // 同步顶栏展示的昵称，避免与已保存资料不一致
      auth.set({ user: updated });
      message.success(t('profile.saveSuccess'));
    })
    .catch(() => {
      /* 校验失败或接口错误：拦截器已提示，停留表单 */
    })
    .finally(() => {
      saving.value = false;
    });
}

function onReset() {
  if (profile.value) applyForm(profile.value);
}
</script>

<style scoped>
.title {
  margin: 0 0 16px;
}
</style>
