/**
 * 个人中心页：查看与维护本人资料（昵称、邮箱、手机）。
 * 用户名只读展示；可改字段与后端 ProfileUpdateRequest 一一对应，
 * userId 由后端从 token 解析，前端不传。
 */
import { useEffect, useState } from 'react';
import { Button, Card, Descriptions, Form, Input, Skeleton, Typography, message } from 'antd';
import { useTranslation } from 'react-i18next';
import type { ProfileUpdatePayload, User } from '@fs/types';
import { fs } from '@/request';
import { useAuth } from '@/store/auth';

export default function ProfilePage() {
  const { t } = useTranslation();
  const setAuth = useAuth((s) => s.set);
  const [profile, setProfile] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<ProfileUpdatePayload>();

  useEffect(() => {
    let active = true;
    fs.get<User>('/user/profile')
      .then((data) => {
        if (!active) return;
        setProfile(data);
        form.setFieldsValue({
          nickname: data.nickname,
          email: data.email,
          phone: data.phone,
        });
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [form]);

  const onSave = async () => {
    const values = await form.validateFields();
    setSaving(true);
    fs.put<User>('/user/profile', values)
      .then((updated) => {
        setProfile(updated);
        form.setFieldsValue({
          nickname: updated.nickname,
          email: updated.email,
          phone: updated.phone,
        });
        // 同步顶栏展示的昵称，避免与已保存资料不一致
        setAuth({ user: updated });
        message.success(t('profile.saveSuccess'));
      })
      .finally(() => setSaving(false));
  };

  const onReset = () => {
    if (!profile) return;
    form.setFieldsValue({
      nickname: profile.nickname,
      email: profile.email,
      phone: profile.phone,
    });
  };

  return (
    <Card>
      <Typography.Title level={3} style={{ margin: 0, marginBottom: 16 }}>
        {t('profile.title')}
      </Typography.Title>
      {loading ? (
        <Skeleton active />
      ) : (
        <>
          <Descriptions column={1} bordered size="small" style={{ marginBottom: 24 }}>
            <Descriptions.Item label={t('profile.username')}>{profile?.username ?? '-'}</Descriptions.Item>
          </Descriptions>
          <Typography.Title level={5}>{t('profile.basicInfo')}</Typography.Title>
          <Form form={form} layout="vertical" style={{ maxWidth: 420 }}>
            <Form.Item name="nickname" label={t('profile.nickname')}>
              <Input maxLength={32} />
            </Form.Item>
            <Form.Item
              name="email"
              label={t('profile.email')}
              rules={[
                {
                  type: 'email',
                  message: t('profile.emailInvalid'),
                },
              ]}
            >
              <Input maxLength={64} />
            </Form.Item>
            <Form.Item name="phone" label={t('profile.phone')}>
              <Input maxLength={20} />
            </Form.Item>
            <Form.Item>
              <Button type="primary" loading={saving} onClick={onSave}>
                {t('common.save')}
              </Button>
              <Button style={{ marginLeft: 8 }} onClick={onReset}>
                {t('profile.reset')}
              </Button>
            </Form.Item>
          </Form>
        </>
      )}
    </Card>
  );
}
