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
        <a-form-item :label="t('login.captcha')" required>
          <div class="captcha-row">
            <a-form-item
              name="captchaCode"
              :rules="[{ required: true, message: t('login.captchaRequired') }]"
              class="captcha-input"
            >
              <a-input
                v-model:value="form.captchaCode"
                :placeholder="t('login.captchaPlaceholder')"
                autocomplete="off"
              />
            </a-form-item>
            <img
              v-if="captcha"
              :src="captcha.image"
              :alt="t('login.captcha')"
              :title="t('login.captcha')"
              class="captcha-img"
              @click="loadCaptcha"
            />
          </div>
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
import type { CaptchaChallenge, LoginRequest, LoginResult, User } from '@fs/types';
import { getDeviceFingerprint, setToken } from '@fs/auth';
import { fs } from '@/request';
import { useAuthStore } from '@/store/auth';

const { t } = useI18n();
const router = useRouter();
const auth = useAuthStore();
const submitting = ref(false);
const form = reactive<LoginRequest>({ username: 'admin', password: '123456', captchaCode: '' });
const captcha = ref<CaptchaChallenge | null>(null);

async function loadCaptcha() {
  try {
    captcha.value = await fs.get<CaptchaChallenge>('/auth/captcha');
  } catch {
    /* 业务/网络错误已由 request 层提示 */
  }
}

void loadCaptcha();

async function onFinish() {
  submitting.value = true;
  try {
    const { token, user } = await fs.post<LoginResult>('/auth/login', {
      ...form,
      captchaId: captcha.value?.captchaId,
      deviceFingerprint: getDeviceFingerprint(),
    });
    setToken(token);
    auth.set({ token, user: (user as User) ?? null });
    void message.success(t('login.success'));
    router.push('/');
  } catch {
    // 登录失败（含验证码错误）后旧验证码已被消费，刷新换一张
    await loadCaptcha();
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
.captcha-row {
  display: flex;
  gap: 8px;
}
.captcha-input {
  flex: 1;
}
.captcha-img {
  height: 32px;
  cursor: pointer;
  flex-shrink: 0;
}
</style>
