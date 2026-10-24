<template>
  <!-- 本人修改密码：身份由后端从 token 解析，前端不传 userId；成功后清登录态回登录页 -->
  <a-modal
    :title="t('user.changePasswordTitle')"
    :open="open"
    :confirm-loading="saving"
    destroy-on-close
    :mask-closable="false"
    @ok="onOk"
    @cancel="onClose"
  >
    <a-form ref="formRef" :model="form" :rules="rules" layout="vertical" autocomplete="off">
      <a-form-item :label="t('user.oldPassword')" name="oldPassword">
        <a-input-password v-model:value="form.oldPassword" />
      </a-form-item>
      <a-form-item :label="t('user.newPassword')" name="newPassword">
        <a-input-password v-model:value="form.newPassword" />
      </a-form-item>
      <a-form-item :label="t('user.confirmPassword')" name="confirmPassword">
        <a-input-password v-model:value="form.confirmPassword" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { message } from 'ant-design-vue';
import { clearToken } from '@fs/auth';
import { fs } from '@/request';
import { useAuthStore } from '@/store/auth';

const props = defineProps<{ open: boolean }>();
const emit = defineEmits<{ close: [] }>();

const { t } = useI18n();
const router = useRouter();
const auth = useAuthStore();

interface PasswordForm {
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
}

const formRef = ref();
const saving = ref(false);
const form = reactive<PasswordForm>({ oldPassword: '', newPassword: '', confirmPassword: '' });

const rules = {
  oldPassword: [{ required: true, message: t('user.oldPasswordRequired'), trigger: 'blur' }],
  newPassword: [
    { required: true, message: t('user.newPasswordRequired'), trigger: 'blur' },
    { min: 6, max: 32, message: t('user.passwordLength'), trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: t('user.confirmPasswordRequired'), trigger: 'blur' },
    {
      validator: (_rule: unknown, value: string) =>
        !value || value === form.newPassword ? Promise.resolve() : Promise.reject(t('user.passwordNotMatch')),
      trigger: 'blur',
    },
  ],
};

watch(
  () => props.open,
  (open) => {
    if (open) {
      form.oldPassword = '';
      form.newPassword = '';
      form.confirmPassword = '';
      formRef.value?.clearValidate?.();
    }
  },
);

function onClose() {
  emit('close');
}

function onOk() {
  formRef.value
    .validate()
    .then(() => {
      saving.value = true;
      return fs.post<void>('/auth/password', {
        oldPassword: form.oldPassword,
        newPassword: form.newPassword,
      });
    })
    .then(() => {
      message.success(t('user.changePasswordSuccess'));
      clearToken();
      auth.clear();
      onClose();
      router.push('/login');
    })
    .catch(() => {
      /* 校验失败或接口错误：拦截器已提示，停留弹窗 */
    })
    .finally(() => {
      saving.value = false;
    });
}
</script>
