/**
 * 操作日志页：查询审计记录。日志由后端切面写入、只读，
 * 页面只提供检索与结果筛选，参数快照通过展开行查看。
 */
import { useEffect, useRef, useState } from 'react';
import { Button, Card, Select, Space, Table, Tag, Typography, type TableColumnsType } from 'antd';
import { useTranslation } from 'react-i18next';
import type { OperationLog, PageResult } from '@fs/types';
import { fs } from '@/request';
import { useDebounceSearch } from '@/hooks/useDebounceSearch';
import { ImeInput } from '@/components/ImeInput';

/** 结果筛选：'ALL' 表示不加条件 */
type ResultFilter = 'ALL' | 'SUCCESS' | 'FAIL';

export default function OperationLogPage() {
  const { t } = useTranslation();
  const [data, setData] = useState<PageResult<OperationLog>>({
    list: [],
    total: 0,
    pageNum: 1,
    pageSize: 10,
  });
  const [loading, setLoading] = useState(false);

  const keywordRef = useRef('');
  const [keyword, setKeyword] = useState('');
  const filterRef = useRef<ResultFilter>('ALL');
  const [resultFilter, setResultFilter] = useState<ResultFilter>('ALL');

  const { scheduleSearch, cancelSearch } = useDebounceSearch({
    getValues: () => ({ keyword: keywordRef.current, result: filterRef.current }),
    onSearch: ({ keyword: kw, result }) => reload(1, data.pageSize, kw, result),
  });

  const reload = (page = 1, size = 10, kw = keywordRef.current, result = filterRef.current) => {
    setLoading(true);
    fs.get<PageResult<OperationLog>>('/operation-log/list', {
      params: {
        page,
        size,
        keyword: kw,
        // 后端 success 为可空布尔，'ALL' 时不传该参数
        success: result === 'ALL' ? undefined : result === 'SUCCESS',
      },
    })
      .then((res) => setData(res))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    reload(1, 10);
  }, []);

  const onFilterChange = (value: ResultFilter) => {
    filterRef.current = value;
    setResultFilter(value);
    scheduleSearch({ force: true });
  };

  const columns: TableColumnsType<OperationLog> = [
    { title: t('operationLog.module'), dataIndex: 'module', width: 120 },
    { title: t('operationLog.action'), dataIndex: 'action', width: 120 },
    { title: t('operationLog.description'), dataIndex: 'description', ellipsis: true },
    {
      title: t('operationLog.operator'),
      dataIndex: 'operatorName',
      width: 120,
      render: (v: string) => v ?? '-',
    },
    { title: t('operationLog.clientIp'), dataIndex: 'clientIp', width: 140, render: (v: string) => v ?? '-' },
    {
      title: t('operationLog.result'),
      dataIndex: 'success',
      width: 90,
      render: (success: boolean) => (
        <Tag color={success ? 'green' : 'red'}>
          {success ? t('operationLog.success') : t('operationLog.failed')}
        </Tag>
      ),
    },
    {
      title: t('operationLog.duration'),
      dataIndex: 'durationMs',
      width: 100,
      render: (v: number) => (v == null ? '-' : t('operationLog.durationValue', { value: v })),
    },
    { title: t('operationLog.createTime'), dataIndex: 'createTime', width: 180 },
  ];

  return (
    <Card>
      <Typography.Title level={3} style={{ margin: 0, marginBottom: 16 }}>
        {t('operationLog.title')}
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
        <Select<ResultFilter>
          value={resultFilter}
          onChange={onFilterChange}
          style={{ width: 160 }}
          options={[
            { value: 'ALL', label: t('operationLog.allResult') },
            { value: 'SUCCESS', label: t('operationLog.success') },
            { value: 'FAIL', label: t('operationLog.failed') },
          ]}
        />
        <Button type="primary" onClick={() => scheduleSearch({ force: true })}>
          {t('common.search')}
        </Button>
        <Button
          onClick={() => {
            keywordRef.current = '';
            setKeyword('');
            filterRef.current = 'ALL';
            setResultFilter('ALL');
            scheduleSearch({ force: true });
          }}
        >
          {t('common.reset')}
        </Button>
      </Space>
      <Table
        rowKey="id"
        size="middle"
        loading={loading}
        dataSource={data.list}
        columns={columns}
        expandable={{
          expandedRowRender: (record) => (
            <Space direction="vertical" size={4} style={{ width: '100%' }}>
              <div>
                <Typography.Text type="secondary">{t('operationLog.uri')}: </Typography.Text>
                <Typography.Text>{`${record.httpMethod ?? ''} ${record.uri ?? '-'}`}</Typography.Text>
              </div>
              <div>
                <Typography.Text type="secondary">{t('operationLog.params')}: </Typography.Text>
                <pre style={{ margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
                  {record.params ?? '-'}
                </pre>
              </div>
              {!record.success && (
                <div>
                  <Typography.Text type="secondary">{t('operationLog.errorMessage')}: </Typography.Text>
                  <Typography.Text type="danger">{record.errorMessage ?? '-'}</Typography.Text>
                </div>
              )}
            </Space>
          ),
        }}
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
