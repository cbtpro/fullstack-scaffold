/**
 * 管理员重置指定用户密码弹窗（无需原密码）。
 * 入口按钮由权限码 user:reset-password 控制；后端接口同口径校验。
 */
import { useEffect, useState } from 'react';
import { Form, Input, Modal, message } from 'antd';
import { useTranslation } from 'react-i18next';
import type { User } from '@fs/types';
import { fs } from '@/request';

interface FormValues {
  newPassword: string;
  confirmPassword: string;
}

interface Props {
  open: boolean;
  user: User | null;
  onClose: () => void;
}

export default function ResetPasswordModal({ open, user, onClose }: Props) {
  const { t } = useTranslation();
  const [form] = Form.useForm<FormValues>();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (open) {
      form.resetFields();
    }
  }, [open, form]);

  const onOk = () => {
    if (!user) return;
    form
      .validateFields()
      .then((values) => {
        setSaving(true);
        return fs
          .put<void>(`/user/${user.id}/password/reset`, { newPassword: values.newPassword })
          .then(() => {
            message.success(t('user.resetPasswordSuccess'));
            onClose();
          })
          .finally(() => setSaving(false));
      })
      .catch(() => {
        /* 校验失败或接口错误：请求层已提示，保留弹窗供用户重试。 */
      });
  };

  return (
    <Modal
      title={
        user
          ? t('user.resetPasswordTitle', { name: user.nickname || user.username })
          : t('user.resetPassword')
      }
      open={open}
      onOk={onOk}
      onCancel={onClose}
      confirmLoading={saving}
      destroyOnClose
      maskClosable={false}
    >
      <p style={{ color: '#888' }}>{t('user.resetPasswordTip')}</p>
      <Form form={form} layout="vertical" autoComplete="off">
        <Form.Item
          name="newPassword"
          label={t('user.newPassword')}
          rules={[
            { required: true, message: t('user.newPasswordRequired') },
            { min: 6, max: 32, message: t('user.passwordLength') },
          ]}
        >
          <Input.Password />
        </Form.Item>
        <Form.Item
          name="confirmPassword"
          label={t('user.confirmPassword')}
          dependencies={['newPassword']}
          rules={[
            { required: true, message: t('user.confirmPasswordRequired') },
            ({ getFieldValue }) => ({
              validator(_, value) {
                if (!value || getFieldValue('newPassword') === value) {
                  return Promise.resolve();
                }
                return Promise.reject(new Error(t('user.passwordNotMatch')));
              },
            }),
          ]}
        >
          <Input.Password />
        </Form.Item>
      </Form>
    </Modal>
  );
}
