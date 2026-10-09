/**
 * 新增 / 编辑用户弹窗。编辑模式密码留空表示不修改；
 * 新增模式密码可留空，后端默认 123456。
 */
import { useEffect, useState } from 'react';
import { Form, Input, Modal, Select } from 'antd';
import { useTranslation } from 'react-i18next';
import type { User } from '@fs/types';
import { fs } from '@/request';

interface Props {
  open: boolean;
  mode: 'create' | 'edit';
  record?: User;
  onCancel: () => void;
  onSaved: () => void;
}

export default function UserFormModal({ open, mode, record, onCancel, onSaved }: Props) {
  const { t } = useTranslation();
  const [form] = Form.useForm<User>();
  const [saving, setSaving] = useState(false);
  const isEdit = mode === 'edit';

  // 打开时回填编辑数据 / 重置新增表单
  useEffect(() => {
    if (!open) return;
    if (isEdit && record) {
      form.setFieldsValue(record);
    } else {
      form.resetFields();
    }
  }, [open, isEdit, record, form]);

  const handleOk = () => {
    form
      .validateFields()
      .then((values) => {
        setSaving(true);
        const req = isEdit
          ? fs.put<void>('/user', { ...values, id: record?.id })
          : fs.post<void>('/user', values);
        return req.then(onSaved);
      })
      .catch(() => {
        // 表单校验失败：antd 已就地提示，这里仅吞掉 reject 避免 unhandled rejection
      })
      .finally(() => setSaving(false));
  };

  return (
    <Modal
      open={open}
      title={isEdit ? t('user.editTitle') : t('user.createTitle')}
      confirmLoading={saving}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnClose
    >
      <Form form={form} layout="vertical" initialValues={{ status: 1 }}>
        <Form.Item
          name="username"
          label={t('login.username')}
          rules={[{ required: true, message: t('login.usernameRequired') }]}
        >
          <Input autoComplete="off" />
        </Form.Item>
        <Form.Item name="nickname" label={t('user.nickname')}>
          <Input autoComplete="off" />
        </Form.Item>
        <Form.Item
          name="password"
          label={t('login.password')}
          extra={isEdit ? t('user.passwordEditHint') : t('user.passwordHint')}
        >
          <Input.Password autoComplete="new-password" />
        </Form.Item>
        <Form.Item name="email" label={t('user.email')} rules={[{ type: 'email', message: t('user.emailInvalid') }]}>
          <Input autoComplete="off" />
        </Form.Item>
        <Form.Item name="phone" label={t('user.phone')}>
          <Input autoComplete="off" />
        </Form.Item>
        <Form.Item name="status" label={t('user.status')}>
          <Select
            options={[
              { value: 1, label: t('user.enabled') },
              { value: 0, label: t('user.disabled') },
            ]}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}
