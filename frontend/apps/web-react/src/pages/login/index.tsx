import { useState } from 'react';
import { Button, Card, Form, Input, Typography, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { LoginRequest, LoginResult, User } from '@fs/types';
import { setToken } from '@fs/auth';
import { fs } from '@/request';
import { useAuth } from '@/store/auth';

export default function LoginPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const setAuth = useAuth((s) => s.set);
  const [submitting, setSubmitting] = useState(false);

  const onFinish = async (values: LoginRequest) => {
    setSubmitting(true);
    try {
      const { token, user } = await fs.post<LoginResult>('/auth/login', values);
      setToken(token);
      setAuth({ token, user: (user as User) ?? null });
      void message.success(t('login.success'));
      navigate('/', { replace: true });
    } catch {
      /* onBusinessError 已展示 */
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(135deg, #1f1f2e 0%, #2c3e50 100%)',
      }}
    >
      <Card style={{ width: 360 }}>
        <Typography.Title level={3} style={{ textAlign: 'center', marginBottom: 24 }}>
          {t('login.title')}
        </Typography.Title>
        <Form
          layout="vertical"
          onFinish={onFinish}
          initialValues={{ username: 'admin', password: '123456' }}
        >
          <Form.Item
            name="username"
            label={t('login.username')}
            rules={[{ required: true, message: t('login.usernameRequired') }]}
          >
            <Input placeholder={t('login.usernamePlaceholder')} autoComplete="username" />
          </Form.Item>
          <Form.Item
            name="password"
            label={t('login.password')}
            rules={[{ required: true, message: t('login.passwordRequired') }]}
          >
            <Input.Password placeholder={t('login.passwordPlaceholder')} autoComplete="current-password" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0 }}>
            <Button type="primary" htmlType="submit" block loading={submitting}>
              {t('login.submit')}
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
}
