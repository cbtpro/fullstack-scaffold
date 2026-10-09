/**
 * 用户列表页：分页查询 + 查看 / 新增 / 编辑 弹窗。
 * 操作按钮经 AuthButton 按 RBAC 权限码控制渲染。
 */
import { useEffect, useState } from 'react';
import { Button, Card, Popconfirm, Space, Table, Tag, Typography, message, type TableColumnsType } from 'antd';
import { useTranslation } from 'react-i18next';
import type { PageResult, User } from '@fs/types';
import { fs } from '@/request';
import AuthButton from '@/components/AuthButton';
import { usePermission } from '@/hooks/usePermission';
import UserFormModal from './UserFormModal';
import UserDetailModal from './UserDetailModal';

type ModalState =
  | { mode: 'create' }
  | { mode: 'edit'; record: User }
  | { mode: 'view'; record: User }
  | null;

export default function UserListPage() {
  const { t } = useTranslation();
  const { can } = usePermission();
  const [data, setData] = useState<PageResult<User>>({
    list: [],
    total: 0,
    pageNum: 1,
    pageSize: 10,
  });
  const [loading, setLoading] = useState(false);
  const [modal, setModal] = useState<ModalState>(null);

  // 后端 PageQuery 参数为 page/size（pageNum/pageSize 会被忽略导致翻页失效）
  const reload = (page = 1, size = 10) => {
    setLoading(true);
    fs.get<PageResult<User>>('/user/list', { params: { page, size } })
      .then((res) => setData(res))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    reload(1, 10);
  }, []);

  const onSaved = () => {
    setModal(null);
    message.success(t('user.saveSuccess'));
    reload(data.pageNum, data.pageSize);
  };

  const onDelete = (record: User) => {
    fs.delete<void>(`/user/${record.id}`).then(() => {
      message.success(t('user.deleteSuccess'));
      reload(data.pageNum, data.pageSize);
    });
  };

  const columns: TableColumnsType<User> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: t('login.username'), dataIndex: 'username' },
    { title: t('user.nickname'), dataIndex: 'nickname', render: (v) => v ?? '-' },
    { title: t('user.email'), dataIndex: 'email', render: (v) => v ?? '-' },
    {
      title: t('user.roles'),
      dataIndex: 'roles',
      render: (roles?: string[]) => roles?.map((r) => <Tag key={r}>{r}</Tag>) ?? '-',
    },
    {
      title: t('user.status'),
      dataIndex: 'status',
      width: 90,
      render: (s?: number) =>
        s === 1 ? <Tag color="green">{t('user.enabled')}</Tag> : <Tag>{t('user.disabled')}</Tag>,
    },
    { title: t('user.createTime'), dataIndex: 'createTime', width: 180 },
    {
      title: t('common.operation'),
      key: '__op',
      width: 200,
      render: (_, record) => (
        <Space>
          <AuthButton perm="user:view" size="small" onClick={() => setModal({ mode: 'view', record })}>
            {t('common.view')}
          </AuthButton>
          <AuthButton perm="user:update" size="small" onClick={() => setModal({ mode: 'edit', record })}>
            {t('common.edit')}
          </AuthButton>
          {/* Popconfirm 需要 element 子节点挂触发器，无权限时不渲染 Popconfirm 本身 */}
          {can('user:delete') && (
            <Popconfirm
              title={t('user.deleteConfirm')}
              okText={t('common.confirm')}
              cancelText={t('common.cancel')}
              onConfirm={() => onDelete(record)}
            >
              <Button danger size="small">
                {t('common.delete')}
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card>
      <Space style={{ marginBottom: 16, width: '100%', justifyContent: 'space-between' }}>
        <Typography.Title level={3} style={{ margin: 0 }}>
          {t('user.list')}
        </Typography.Title>
        <AuthButton perm="user:create" type="primary" onClick={() => setModal({ mode: 'create' })}>
          {t('common.create')}
        </AuthButton>
      </Space>
      <Table
        rowKey="id"
        size="middle"
        loading={loading}
        dataSource={data.list}
        columns={columns}
        pagination={{
          total: data.total,
          current: data.pageNum,
          pageSize: data.pageSize,
          showTotal: (total) => t('user.total', { count: total }),
          onChange: (page, pageSize) => reload(page, pageSize),
        }}
      />
      <UserFormModal
        open={modal?.mode === 'create' || modal?.mode === 'edit'}
        mode={modal?.mode === 'edit' ? 'edit' : 'create'}
        record={modal?.mode === 'edit' ? modal.record : undefined}
        onCancel={() => setModal(null)}
        onSaved={onSaved}
      />
      <UserDetailModal
        open={modal?.mode === 'view'}
        record={modal?.mode === 'view' ? modal.record : undefined}
        onClose={() => setModal(null)}
      />
    </Card>
  );
}
