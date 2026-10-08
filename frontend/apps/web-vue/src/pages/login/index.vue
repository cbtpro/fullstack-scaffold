<template>
  <div class="login-wrap">
    <a-card class="login-card">
      <a-typography-title :level="3" class="title">{{ t('login.title') }}</a-typography-title>
      <a-form layout="vertical" :model="form" @finish="onFinish">
        <a-form-item
          :label="t('login.username')"
          name="username"
          :rules="[{ required: true, message: t('login.usernameRequired') }]"
        >
          <a-input
            v-model:value="form.username"
            :placeholder="t('login.usernamePlaceholder')"
            autocomplete="username"
          />
        </a-form-item>
        <a-form-item
          :label="t('login.password')"
          name="password"
          :rules="[{ required: true, message: t('login.passwordRequired') }]"
        >
          <a-input-password
            v-model:value="form.password"
            :placeholder="t('login.passwordPlaceholder')"
            autocomplete="current-password"
          />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" block :loading="submitting">
            {{ t('login.submit') }}
          </a-button>
        </a-form-item>
      </a-form>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { message } from 'ant-design-vue';
import type { LoginRequest, LoginResult, User } from '@fs/types';
import { setToken } from '@fs/auth';
import { fs } from '@/request';
import { useAuthStore } from '@/store/auth';

const { t } = useI18n();
const router = useRouter();
const auth = useAuthStore();
const submitting = ref(false);
const form = reactive<LoginRequest>({ username: 'admin', password: '123456' });

async function onFinish() {
  submitting.value = true;
  try {
    const { token, user } = await fs.post<LoginResult>('/auth/login', { ...form });
    setToken(token);
    auth.set({ token, user: (user as User) ?? null });
    void message.success(t('login.success'));
    router.push('/');
  } catch {
    /* onBusinessError 已展示 */
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
.login-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f1f2e 0%, #2c3e50 100%);
}
.login-card {
  width: 360px;
}
.title {
  text-align: center;
  margin-bottom: 24px;
}
</style>
