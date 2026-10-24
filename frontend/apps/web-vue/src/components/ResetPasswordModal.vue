<template>
  <!-- 管理员重置指定用户密码（无需原密码），权限码 user:reset-password -->
  <a-modal
    :title="
      user ? t('user.resetPasswordTitle', { name: user.nickname || user.username }) : t('user.resetPassword')
    "
    :open="open"
    :confirm-loading="saving"
    destroy-on-close
    :mask-closable="false"
    @ok="onOk"
    @cancel="onClose"
  >
    <p class="tip">{{ t('user.resetPasswordTip') }}</p>
    <a-form ref="formRef" :model="form" :rules="rules" layout="vertical" autocomplete="off">
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
import { useI18n } from 'vue-i18n';
import { message } from 'ant-design-vue';
import type { User } from '@fs/types';
import { fs } from '@/request';

const props = defineProps<{ open: boolean; user: User | null }>();
const emit = defineEmits<{ close: [] }>();

const { t } = useI18n();

interface PasswordForm {
  newPassword: string;
  confirmPassword: string;
}

const formRef = ref();
const saving = ref(false);
const form = reactive<PasswordForm>({ newPassword: '', confirmPassword: '' });

const rules = {
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
  if (!props.user) return;
  formRef.value
    .validate()
    .then(() => {
      saving.value = true;
      return fs.put<void>(`/user/${props.user?.id}/password/reset`, {
        newPassword: form.newPassword,
      });
    })
    .then(() => {
      message.success(t('user.resetPasswordSuccess'));
      onClose();
    })
    .catch(() => {
      /* 校验失败或接口错误：拦截器已提示，停留弹窗 */
    })
    .finally(() => {
      saving.value = false;
    });
}
</script>

<style scoped>
.tip {
  color: #888;
}
</style>
