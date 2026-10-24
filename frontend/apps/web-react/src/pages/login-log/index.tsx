/**
 * 登录日志页：查询登录/登出审计记录。日志由登录入口写入、只读，
 * 页面只提供检索与事件/结果筛选。
 */
import { useEffect, useRef, useState } from 'react';
import { Button, Card, Select, Space, Table, Tag, Typography, type TableColumnsType } from 'antd';
import { useTranslation } from 'react-i18next';
import type { LoginLog, PageResult } from '@fs/types';
import { fs } from '@/request';
import { useDebounceSearch } from '@/hooks/useDebounceSearch';
import { ImeInput } from '@/components/ImeInput';

/** 结果筛选：'ALL' 表示不加条件 */
type ResultFilter = 'ALL' | 'SUCCESS' | 'FAIL';
/** 事件筛选：'' 表示不加条件 */
type EventFilter = '' | 'LOGIN' | 'LOGOUT';

export default function LoginLogPage() {
  const { t } = useTranslation();
  const [data, setData] = useState<PageResult<LoginLog>>({
    list: [],
    total: 0,
    pageNum: 1,
    pageSize: 10,
  });
  const [loading, setLoading] = useState(false);

  const keywordRef = useRef('');
  const [keyword, setKeyword] = useState('');
  const eventRef = useRef<EventFilter>('');
  const [eventFilter, setEventFilter] = useState<EventFilter>('');
  const resultRef = useRef<ResultFilter>('ALL');
  const [resultFilter, setResultFilter] = useState<ResultFilter>('ALL');

  const { scheduleSearch, cancelSearch } = useDebounceSearch({
    getValues: () => ({
      keyword: keywordRef.current,
      event: eventRef.current,
      result: resultRef.current,
    }),
    onSearch: ({ keyword: kw, event, result }) => reload(1, data.pageSize, kw, event, result),
  });

  const reload = (
    page = 1,
    size = 10,
    kw = keywordRef.current,
    event = eventRef.current,
    result = resultRef.current,
  ) => {
    setLoading(true);
    fs.get<PageResult<LoginLog>>('/login-log/list', {
      params: {
        page,
        size,
        keyword: kw,
        eventType: event || undefined,
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

  const columns: TableColumnsType<LoginLog> = [
    { title: t('loginLog.username'), dataIndex: 'username', width: 140 },
    {
      title: t('loginLog.eventType'),
      dataIndex: 'eventType',
      width: 110,
      render: (v: string) => (v === 'LOGOUT' ? t('loginLog.eventLogout') : t('loginLog.eventLogin')),
    },
    {
      title: t('loginLog.result'),
      dataIndex: 'success',
      width: 90,
      render: (success: boolean) => (
        <Tag color={success ? 'green' : 'red'}>{success ? t('loginLog.success') : t('loginLog.failed')}</Tag>
      ),
    },
    { title: t('loginLog.message'), dataIndex: 'message', ellipsis: true, render: (v: string) => v ?? '-' },
    { title: t('loginLog.clientIp'), dataIndex: 'clientIp', width: 140, render: (v: string) => v ?? '-' },
    {
      title: t('loginLog.userAgent'),
      dataIndex: 'userAgent',
      ellipsis: true,
      render: (v: string) => v ?? '-',
    },
    { title: t('loginLog.createTime'), dataIndex: 'createTime', width: 180 },
  ];

  return (
    <Card>
      <Typography.Title level={3} style={{ margin: 0, marginBottom: 16 }}>
        {t('loginLog.title')}
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
        <Select<EventFilter>
          value={eventFilter}
          onChange={(v) => {
            eventRef.current = v;
            setEventFilter(v);
            scheduleSearch({ force: true });
          }}
          style={{ width: 140 }}
          options={[
            { value: '', label: t('loginLog.allEvent') },
            { value: 'LOGIN', label: t('loginLog.eventLogin') },
            { value: 'LOGOUT', label: t('loginLog.eventLogout') },
          ]}
        />
        <Select<ResultFilter>
          value={resultFilter}
          onChange={(v) => {
            resultRef.current = v;
            setResultFilter(v);
            scheduleSearch({ force: true });
          }}
          style={{ width: 140 }}
          options={[
            { value: 'ALL', label: t('loginLog.allResult') },
            { value: 'SUCCESS', label: t('loginLog.success') },
            { value: 'FAIL', label: t('loginLog.failed') },
          ]}
        />
        <Button type="primary" onClick={() => scheduleSearch({ force: true })}>
          {t('common.search')}
        </Button>
        <Button
          onClick={() => {
            keywordRef.current = '';
            setKeyword('');
            eventRef.current = '';
            setEventFilter('');
            resultRef.current = 'ALL';
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
