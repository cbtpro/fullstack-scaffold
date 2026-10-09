import { useEffect, useState } from 'react';
import { Card, Space, Table, Tag, Typography, type TableColumnsType } from 'antd';
import { useTranslation } from 'react-i18next';
import type { PageResult, User } from '@fs/types';
import { fs } from '@/request';
import AuthButton from '@/components/AuthButton';

export default function UserListPage() {
  const { t } = useTranslation();
  const [data, setData] = useState<PageResult<User>>({
    list: [],
    total: 0,
    pageNum: 1,
    pageSize: 10,
  });
  const [loading, setLoading] = useState(false);

  const reload = (pageNum = 1, pageSize = 10) => {
    setLoading(true);
    fs.get<PageResult<User>>('/user/list', { params: { pageNum, pageSize } })
      .then((res) => setData(res))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    let active = true;
    setLoading(true);
    fs.get<PageResult<User>>('/user/list', { params: { pageNum: 1, pageSize: 10 } })
      .then((res) => {
        if (active) setData(res);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  const columns: TableColumnsType<User> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: t('login.username'), dataIndex: 'username' },
    { title: 'Nickname', dataIndex: 'nickname' },
    { title: 'Email', dataIndex: 'email' },
    {
      title: 'Roles',
      dataIndex: 'roles',
      render: (roles?: string[]) => roles?.map((r) => <Tag key={r}>{r}</Tag>) ?? null,
    },
    {
      title: t('common.success'),
      dataIndex: 'status',
      render: (s?: number) =>
        s === 1 ? <Tag color="green">{t('common.success')}</Tag> : <Tag>{t('common.failed')}</Tag>,
    },
    { title: 'createTime', dataIndex: 'createTime' },
    {
      title: t('common.operation'),
      key: '__op',
      width: 200,
      render: () => (
        <Space>
          <AuthButton perm="user:update" size="small">编辑</AuthButton>
          <AuthButton perm="user:delete" size="small" danger>删除</AuthButton>
        </Space>
      ),
    },
  ];

  return (
    <Card>
      <Space style={{ marginBottom: 16, width: '100%', justifyContent: 'space-between' }}>
        <Typography.Title level={3} style={{ margin: 0 }}>{t('user.list')}</Typography.Title>
        <AuthButton perm="user:create" type="primary" onClick={() => reload()}>
          {t('common.create', '新增')}
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
    </Card>
  );
}
