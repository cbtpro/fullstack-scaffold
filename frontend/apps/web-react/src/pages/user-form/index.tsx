/**
 * 用户三态页：新建 / 详情 / 编辑共享一份字段、校验与布局。
 * 模式由路由派生：/user/create、/user/detail/:id、/user/edit/:id。
 * record 为已保存快照，表单仅是其草稿：进入编辑=快照复制，取消编辑=回详情恢复快照，
 * 保存成功=以接口返回值更新快照后跳转详情，页面自身不维护"脏"状态。
 */
import { useEffect, useState } from 'react';
import { Navigate, useMatch, useNavigate, useParams } from 'react-router-dom';
import { Button, Card, Form, Input, Select, Skeleton, Space, Tag, Typography, message } from 'antd';
import { useTranslation } from 'react-i18next';
import type { User } from '@fs/types';
import { fs } from '@/request';
import { usePermission } from '@/hooks/usePermission';

type Mode = 'create' | 'detail' | 'edit';

/** 后端时间形如 2026-10-09T12:34:56，转为可读文本 */
function formatTime(v?: string) {
  return v ? v.replace('T', ' ').slice(0, 19) : '-';
}

export default function UserFormPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const params = useParams();
  const { can } = usePermission();
  const matchedEdit = useMatch('/user/edit/:id');
  const matchedDetail = useMatch('/user/detail/:id');
  const mode: Mode = matchedEdit ? 'edit' : matchedDetail ? 'detail' : 'create';
  const id = mode === 'create' ? undefined : params.id;

  const [record, setRecord] = useState<User | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  useEffect(() => {
    if (!id) {
      setRecord(null);
      setError(false);
      setLoading(false);
      form.resetFields();
      return;
    }
    // active 标志防竞态：id 变化或 StrictMode 卸载重挂后，旧请求响应一律丢弃
    let active = true;
    setRecord(null);
    setError(false);
    setLoading(true);
    fs.get<User>(`/user/${id}`)
      .then((res) => {
        if (active) setRecord(res);
      })
      .catch(() => {
        if (active) setError(true);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [id, form]);

  // 进入编辑=快照复制到表单；取消编辑回到详情后再进编辑，同样恢复为快照
  useEffect(() => {
    if (mode === 'edit' && record) {
      form.setFieldsValue({
        username: record.username,
        nickname: record.nickname,
        email: record.email,
        phone: record.phone,
        status: record.status ?? 1,
      });
    }
  }, [mode, record, form]);

  async function handleSave() {
    let values;
    try {
      values = await form.validateFields();
    } catch {
      return; // 校验失败：表单已就地提示
    }
    setSaving(true);
    try {
      const saved =
        mode === 'edit'
          ? await fs.put<User>('/user', { ...values, id: record?.id })
          : await fs.post<User>('/user', values);
      setRecord(saved);
      message.success(t('user.saveSuccess'));
      navigate(`/user/detail/${saved.id}`, { replace: true });
    } finally {
      setSaving(false);
    }
  }

  // 权限不足直接重定向，避免页面先渲染再被接口拒绝
  if (mode === 'create' && !can('user:create')) {
    return <Navigate to="/user" replace />;
  }
  if (mode === 'edit' && !can('user:update')) {
    return <Navigate to={`/user/detail/${id}`} replace />;
  }

  const title =
    mode === 'create' ? t('user.createTitle') : mode === 'edit' ? t('user.editTitle') : t('user.detailTitle');

  return (
    <Card
      title={title}
      extra={
        <Space>
          {mode === 'detail' ? (
            <>
              <Button onClick={() => navigate('/user')}>{t('user.backToList')}</Button>
              {can('user:update') && (
                <Button type="primary" onClick={() => navigate(`/user/edit/${id}`)}>
                  {t('common.edit')}
                </Button>
              )}
            </>
          ) : (
            <>
              <Button disabled={saving} onClick={() => navigate(mode === 'edit' ? `/user/detail/${id}` : '/user')}>
                {mode === 'edit' ? t('user.cancelEdit') : t('common.cancel')}
              </Button>
              <Button type="primary" loading={saving} onClick={handleSave}>
                {t('common.save')}
              </Button>
            </>
          )}
        </Space>
      }
    >
      {loading ? (
        <Skeleton active />
      ) : error ? (
        <div>
          <Typography.Text type="danger">{t('user.loadFailed')}</Typography.Text>
          <div style={{ marginTop: 16 }}>
            <Space>
              <Button onClick={() => navigate('/user')}>{t('user.backToList')}</Button>
              {can('user:create') && (
                <Button type="primary" onClick={() => navigate('/user/create')}>
                  {t('common.create')}
                </Button>
              )}
            </Space>
          </div>
        </div>
      ) : mode === 'detail' ? (
        <Form layout="vertical" style={{ maxWidth: 480 }}>
          <Form.Item label={t('login.username')}>{record?.username ?? '-'}</Form.Item>
          <Form.Item label={t('user.nickname')}>{record?.nickname || '-'}</Form.Item>
          <Form.Item label={t('user.email')}>{record?.email || '-'}</Form.Item>
          <Form.Item label={t('user.phone')}>{record?.phone || '-'}</Form.Item>
          <Form.Item label={t('user.roles')}>{record?.roles?.length ? record.roles.join('、') : '-'}</Form.Item>
          <Form.Item label={t('user.status')}>
            <Tag color={record?.status === 1 ? 'green' : 'red'}>
              {record?.status === 1 ? t('user.enabled') : t('user.disabled')}
            </Tag>
          </Form.Item>
          <Form.Item label={t('user.createTime')}>{formatTime(record?.createTime)}</Form.Item>
          <Form.Item label={t('user.updateTime')}>{formatTime(record?.updateTime)}</Form.Item>
        </Form>
      ) : (
        <Form
          form={form}
          layout="vertical"
          style={{ maxWidth: 480 }}
          disabled={saving}
          initialValues={{ status: 1 }}
        >
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
            extra={mode === 'edit' ? t('user.passwordEditHint') : t('user.passwordHint')}
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
      )}
    </Card>
  );
}
