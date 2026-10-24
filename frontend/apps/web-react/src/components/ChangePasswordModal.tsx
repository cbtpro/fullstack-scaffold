/**
 * 本人修改密码弹窗。用户身份由后端从 token 解析，前端不传 userId。
 * 修改成功后清除本地登录态并跳转登录页（JWT 无法主动吊销，旧 token 仍有效至过期，
 * 但密码已变更，重新登录获取新 claims；Sa-Token 模式下后端登出会使会话失效）。
 */
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Form, Input, Modal, message } from 'antd';
import { useTranslation } from 'react-i18next';
import { clearToken } from '@fs/auth';
import { fs } from '@/request';
import { useAuth } from '@/store/auth';

interface FormValues {
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
}

export default function ChangePasswordModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [form] = Form.useForm<FormValues>();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (open) {
      form.resetFields();
    }
  }, [open, form]);

  const onOk = () => {
    form
      .validateFields()
      .then((values) => {
        setSaving(true);
        return fs
          .post<void>('/auth/password', {
            oldPassword: values.oldPassword,
            newPassword: values.newPassword,
          })
          .then(() => {
            message.success(t('user.changePasswordSuccess'));
            clearToken();
            useAuth.getState().clear();
            onClose();
            navigate('/login', { replace: true });
          })
          .finally(() => setSaving(false));
      })
      .catch(() => {
        /* 校验失败或接口错误：请求层已提示，保留弹窗供用户重试。 */
      });
  };

  return (
    <Modal
      title={t('user.changePasswordTitle')}
      open={open}
      onOk={onOk}
      onCancel={onClose}
      confirmLoading={saving}
      destroyOnClose
      maskClosable={false}
    >
      <Form form={form} layout="vertical" autoComplete="off">
        <Form.Item
          name="oldPassword"
          label={t('user.oldPassword')}
          rules={[{ required: true, message: t('user.oldPasswordRequired') }]}
        >
          <Input.Password />
        </Form.Item>
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
