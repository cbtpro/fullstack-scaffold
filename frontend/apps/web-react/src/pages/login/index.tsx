import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Form, Input, Typography, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { CaptchaChallenge, LoginRequest, LoginResult, User } from '@fs/types';
import { getDeviceFingerprint, setToken } from '@fs/auth';
import { fs } from '@/request';
import { useAuth } from '@/store/auth';

export default function LoginPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const setAuth = useAuth((s) => s.set);
  const [submitting, setSubmitting] = useState(false);
  const [captcha, setCaptcha] = useState<CaptchaChallenge | null>(null);

  const loadCaptcha = useCallback(async () => {
    try {
      setCaptcha(await fs.get<CaptchaChallenge>('/auth/captcha'));
    } catch {
      /* 业务/网络错误已由 request 层提示 */
    }
  }, []);

  useEffect(() => {
    void loadCaptcha();
  }, [loadCaptcha]);

  const onFinish = async (values: LoginRequest) => {
    setSubmitting(true);
    try {
      const { token, user } = await fs.post<LoginResult>('/auth/login', {
        ...values,
        captchaId: captcha?.captchaId,
        deviceFingerprint: getDeviceFingerprint(),
      });
      setToken(token);
      setAuth({ token, user: (user as User) ?? null });
      void message.success(t('login.success'));
      navigate('/', { replace: true });
    } catch {
      // 登录失败（含验证码错误）后旧验证码已被消费，刷新换一张
      void loadCaptcha();
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
          <Form.Item label={t('login.captcha')} required>
            <div style={{ display: 'flex', gap: 8 }}>
              <Form.Item
                name="captchaCode"
                noStyle
                rules={[{ required: true, message: t('login.captchaRequired') }]}
              >
                <Input placeholder={t('login.captchaPlaceholder')} autoComplete="off" />
              </Form.Item>
              {captcha && (
                <img
                  src={captcha.image}
                  alt={t('login.captcha')}
                  title={t('login.captcha')}
                  onClick={() => void loadCaptcha()}
                  style={{ height: 32, cursor: 'pointer', flexShrink: 0 }}
                />
              )}
            </div>
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
