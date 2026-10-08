import { useEffect, useState } from 'react';
import { Card, Table, Tag, Typography, type TableColumnsType } from 'antd';
import { useTranslation } from 'react-i18next';
import type { PageResult, User } from '@fs/types';
import { fs } from '@/request';

export default function UserListPage() {
  const { t } = useTranslation();
  const [data, setData] = useState<PageResult<User>>({
    list: [],
    total: 0,
    pageNum: 1,
    pageSize: 10,
  });
  const [loading, setLoading] = useState(false);

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
  ];

  return (
    <Card>
      <Typography.Title level={3}>{t('user.list')}</Typography.Title>
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
        }}
      />
    </Card>
  );
}
