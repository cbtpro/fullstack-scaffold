<template>
  <a-modal
    :open="open"
    :title="isEdit ? t('user.editTitle') : t('user.createTitle')"
    :confirm-loading="saving"
    @ok="handleOk"
    @cancel="emit('update:open', false)"
  >
    <a-form ref="formRef" :model="form" :label-col="{ span: 5 }" :wrapper-col="{ span: 17 }">
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
      <a-form-item :label="t('login.password')" name="password" :extra="isEdit ? t('user.passwordEditHint') : t('user.passwordHint')">
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
  </a-modal>
</template>

<script setup lang="ts">
/**
 * 新增 / 编辑用户弹窗。编辑模式密码留空表示不修改；
 * 新增模式密码可留空，后端默认 123456。
 */
import { computed, reactive, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import type { User } from '@fs/types';
import { fs } from '@/request';

const props = defineProps<{
  open: boolean;
  mode: 'create' | 'edit';
  record?: User | null;
}>();

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void;
  (e: 'saved'): void;
}>();

const { t } = useI18n();
const formRef = ref();
const saving = ref(false);
const isEdit = computed(() => props.mode === 'edit');

const emptyForm = () => ({
  username: '',
  nickname: '',
  password: '',
  email: '',
  phone: '',
  status: 1,
});
const form = reactive(emptyForm());

// 打开时回填编辑数据 / 重置新增表单
watch(
  () => props.open,
  (open) => {
    if (!open) return;
    Object.assign(form, emptyForm());
    if (isEdit.value && props.record) {
      const { username, nickname, email, phone, status } = props.record;
      Object.assign(form, { username, nickname, email, phone, status: status ?? 1 });
    }
  },
);

async function handleOk() {
  try {
    await formRef.value.validate();
  } catch {
    return; // 校验失败：表单已就地提示
  }
  saving.value = true;
  try {
    const payload = { ...form };
    if (isEdit.value) {
      await fs.put('/user', { ...payload, id: props.record?.id });
    } else {
      await fs.post('/user', payload);
    }
    emit('saved');
  } finally {
    saving.value = false;
  }
}
</script>
