/**
 * 登录设备列表页：查看当前用户的常用登录设备，支持设为可信/吊销/移除。
 * 设备由登录自动登记，无新增入口。操作权限走包装器模式：
 * 已可信的设备不显示"设为可信"，已吊销的不显示"吊销"，避免无效操作。
 */
import { useEffect, useRef, useState } from 'react';
import { Button, Card, Popconfirm, Space, Table, Tag, Typography, message, type TableColumnsType } from 'antd';
import { useTranslation } from 'react-i18next';
import type { DeviceRiskLevel, PageResult, UserDevice } from '@fs/types';
import { fs } from '@/request';
import { withPermission } from '@/components/permission';
import { useDebounceSearch } from '@/hooks/useDebounceSearch';
import { ImeInput } from '@/components/ImeInput';

// 模块级定义包装组件，避免每次渲染创建新的组件类型导致子树反复卸载
const TrustConfirm = withPermission<UserDevice>({
  codes: ['device:trust'],
  fallback: 'hide',
  customFn: (_, r) => r?.riskLevel !== 'TRUSTED',
})(Popconfirm);
const RevokeConfirm = withPermission<UserDevice>({
  codes: ['device:revoke'],
  fallback: 'hide',
  customFn: (_, r) => r?.riskLevel !== 'REVOKED',
})(Popconfirm);
const RemoveConfirm = withPermission<UserDevice>({
  codes: ['device:remove'],
})(Popconfirm);

const RISK_COLORS: Record<DeviceRiskLevel, string> = {
  TRUSTED: 'green',
  NORMAL: 'blue',
  RISKY: 'orange',
  REVOKED: 'red',
};

function maskFingerprint(fp: string): string {
  if (!fp || fp.length <= 16) return fp;
  return `${fp.slice(0, 8)}…${fp.slice(-8)}`;
}

export default function DeviceListPage() {
  const { t } = useTranslation();
  const [data, setData] = useState<PageResult<UserDevice>>({
    list: [],
    total: 0,
    pageNum: 1,
    pageSize: 10,
  });
  const [loading, setLoading] = useState(false);

  const keywordRef = useRef('');
  const [keyword, setKeyword] = useState('');

  const { scheduleSearch, cancelSearch } = useDebounceSearch({
    getValues: () => ({ keyword: keywordRef.current }),
    onSearch: ({ keyword: kw }) => reload(1, data.pageSize, kw),
  });

  const reload = (page = 1, size = 10, kw = keywordRef.current) => {
    setLoading(true);
    fs.get<PageResult<UserDevice>>('/device/list', { params: { page, size, keyword: kw } })
      .then((res) => setData(res))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    reload(1, 10);
  }, []);

  const onTrust = (record: UserDevice) => {
    fs.put<void>(`/device/${record.id}/trust`).then(() => {
      message.success(t('device.trustSuccess'));
      reload(data.pageNum, data.pageSize);
    });
  };

  const onRevoke = (record: UserDevice) => {
    fs.put<void>(`/device/${record.id}/revoke`).then(() => {
      message.success(t('device.revokeSuccess'));
      reload(data.pageNum, data.pageSize);
    });
  };

  const onRemove = (record: UserDevice) => {
    fs.delete<void>(`/device/${record.id}`).then(() => {
      message.success(t('device.removeSuccess'));
      reload(data.pageNum, data.pageSize);
    });
  };

  const columns: TableColumnsType<UserDevice> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: t('device.fingerprint'), dataIndex: 'fingerprint', render: (fp: string) => maskFingerprint(fp) },
    { title: t('device.ip'), dataIndex: 'ip', width: 150, render: (v: string) => v ?? '-' },
    {
      title: t('device.riskLevel'),
      dataIndex: 'riskLevel',
      width: 100,
      render: (level: DeviceRiskLevel) => (
        <Tag color={RISK_COLORS[level]}>{t(`device.${level.toLowerCase()}`)}</Tag>
      ),
    },
    { title: t('device.lastLoginTime'), dataIndex: 'lastLoginTime', width: 180, render: (v: string) => v ?? '-' },
    { title: t('device.createTime'), dataIndex: 'createTime', width: 180, render: (v: string) => v ?? '-' },
    {
      title: t('common.operation'),
      key: '__op',
      width: 220,
      render: (_, record) => (
        <Space>
          <TrustConfirm
            extra={record}
            title={t('device.trustConfirm')}
            okText={t('common.confirm')}
            cancelText={t('common.cancel')}
            onConfirm={() => onTrust(record)}
          >
            <Button size="small">{t('device.trust')}</Button>
          </TrustConfirm>
          <RevokeConfirm
            extra={record}
            title={t('device.revokeConfirm')}
            okText={t('common.confirm')}
            cancelText={t('common.cancel')}
            onConfirm={() => onRevoke(record)}
          >
            <Button danger size="small">{t('device.revoke')}</Button>
          </RevokeConfirm>
          <RemoveConfirm
            title={t('device.removeConfirm')}
            okText={t('common.confirm')}
            cancelText={t('common.cancel')}
            onConfirm={() => onRemove(record)}
          >
            <Button danger size="small">{t('device.remove')}</Button>
          </RemoveConfirm>
        </Space>
      ),
    },
  ];

  return (
    <Card>
      <Typography.Title level={3} style={{ margin: 0, marginBottom: 16 }}>
        {t('device.title')}
      </Typography.Title>
      <Space style={{ marginBottom: 16 }}>
        <ImeInput
          value={keyword}
          onChange={(v) => {
            keywordRef.current = v;
            setKeyword(v);
            scheduleSearch();
          }}
          onCompositionStart={cancelSearch}
          onPressEnter={() => scheduleSearch({ force: true })}
          placeholder={t('common.searchPlaceholder')}
          allowClear
          style={{ width: 240 }}
        />
        <Button type="primary" onClick={() => scheduleSearch({ force: true })}>
          {t('common.search')}
        </Button>
        <Button onClick={() => {
          keywordRef.current = '';
          setKeyword('');
          scheduleSearch({ force: true });
        }}>
          {t('common.reset')}
        </Button>
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
