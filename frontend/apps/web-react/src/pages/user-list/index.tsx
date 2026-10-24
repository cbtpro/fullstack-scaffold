/**
 * 用户列表页：分页查询 + 删除；查看/编辑/新增跳转三态表单页（/user/detail|edit|create）。
 * 操作权限走包装器模式：规则集中在模块级定义，行内按钮经 withPermission 生成，
 * Popconfirm 整体被包裹，无权限时触发器与确认层一起卸载；单次出现的工具栏按钮用 PermissionGuard。
 */
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Card,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
  type TableColumnsType,
} from 'antd';
import { useTranslation } from 'react-i18next';
import type { ID, PageResult, Role, User } from '@fs/types';
import { fs } from '@/request';
import { PermissionGuard, withPermission } from '@/components/permission';
import { useDebounceSearch } from '@/hooks/useDebounceSearch';
import { ImeInput } from '@/components/ImeInput';
import ResetPasswordModal from '@/components/ResetPasswordModal';

// 模块级定义包装组件，避免每次渲染创建新的组件类型导致子树反复卸载
const ViewButton = withPermission({ codes: ['user:view'], fallback: 'disable' })(Button);
const EditButton = withPermission<User>({
  codes: ['user:update'],
  fallback: 'disable',
  // 业务条件：仅启用状态的用户可编辑（与后端独立鉴权并存，这里只控制界面入口）
  customFn: (_, record) => record?.status === 1,
})(Button);
const AssignRoleButton = withPermission({ codes: ['user:assign-role'], fallback: 'disable' })(Button);
const ResetPasswordButton = withPermission({ codes: ['user:reset-password'], fallback: 'disable' })(Button);
const DeleteConfirm = withPermission({ codes: ['user:delete'] })(Popconfirm);

export default function UserListPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [data, setData] = useState<PageResult<User>>({
    list: [],
    total: 0,
    pageNum: 1,
    pageSize: 10,
  });
  const [loading, setLoading] = useState(false);

  // 搜索关键词与防抖调度
  const keywordRef = useRef('');
  const [keyword, setKeyword] = useState('');
  const { scheduleSearch, cancelSearch } = useDebounceSearch({
    getValues: () => ({ keyword: keywordRef.current }),
    onSearch: ({ keyword: kw }) => reload(1, data.pageSize, kw),
  });

  // 分配角色弹窗
  const [roleOpen, setRoleOpen] = useState(false);
  const [roleUser, setRoleUser] = useState<User | null>(null);
  const [roleIds, setRoleIds] = useState<ID[]>([]);
  const [roleOptions, setRoleOptions] = useState<Role[]>([]);

  // 重置密码弹窗
  const [resetOpen, setResetOpen] = useState(false);
  const [resetUser, setResetUser] = useState<User | null>(null);

  // 后端 PageQuery 参数为 page/size（pageNum/pageSize 会被忽略导致翻页失效）
  const reload = (page = 1, size = 10, kw = keywordRef.current) => {
    setLoading(true);
    fs.get<PageResult<User>>('/user/list', { params: { page, size, keyword: kw } })
      .then((res) => setData(res))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    reload(1, 10);
  }, []);

  const onDelete = (record: User) => {
    fs.delete<void>(`/user/${record.id}`).then(() => {
      message.success(t('user.deleteSuccess'));
      reload(data.pageNum, data.pageSize);
    });
  };

  const onAssignRole = (record: User) => {
    setRoleUser(record);
    Promise.all([fs.get<Role[]>('/role/all'), fs.get<ID[]>(`/user/${record.id}/roles`)]).then(
      ([roles, ids]) => {
        setRoleOptions(roles);
        setRoleIds(ids);
        setRoleOpen(true);
      },
    );
  };

  const onSaveRole = () => {
    if (!roleUser) return;
    fs.put<void>(`/user/${roleUser.id}/roles`, roleIds).then(() => {
      message.success(t('user.saveSuccess'));
      setRoleOpen(false);
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
      width: 260,
      render: (_, record) => (
        <Space>
          <ViewButton size="small" onClick={() => navigate(`/user/detail/${record.id}`)}>
            {t('common.view')}
          </ViewButton>
          <EditButton extra={record} size="small" onClick={() => navigate(`/user/edit/${record.id}`)}>
            {t('common.edit')}
          </EditButton>
          <AssignRoleButton size="small" onClick={() => onAssignRole(record)}>
            {t('user.assignRole')}
          </AssignRoleButton>
          <ResetPasswordButton
            size="small"
            onClick={() => {
              setResetUser(record);
              setResetOpen(true);
            }}
          >
            {t('user.resetPassword')}
          </ResetPasswordButton>
          <DeleteConfirm
            title={t('user.deleteConfirm')}
            okText={t('common.confirm')}
            cancelText={t('common.cancel')}
            onConfirm={() => onDelete(record)}
          >
            <Button danger size="small">
              {t('common.delete')}
            </Button>
          </DeleteConfirm>
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
        <PermissionGuard codes={['user:create']}>
          <Button type="primary" onClick={() => navigate('/user/create')}>
            {t('common.create')}
          </Button>
        </PermissionGuard>
      </Space>
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
        <Button
          onClick={() => {
            keywordRef.current = '';
            setKeyword('');
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

      {/* 分配角色弹窗 */}
      <Modal
        title={roleUser ? t('user.assignRoleTitle', { name: roleUser.nickname || roleUser.username }) : ''}
        open={roleOpen}
        onOk={onSaveRole}
        onCancel={() => setRoleOpen(false)}
        destroyOnClose
      >
        <Select
          mode="multiple"
          style={{ width: '100%' }}
          placeholder={t('user.roles')}
          value={roleIds}
          onChange={(v) => setRoleIds(v as ID[])}
          options={roleOptions.map((r) => ({ value: r.id, label: `${r.name} (${r.code})` }))}
        />
      </Modal>

      <ResetPasswordModal
        open={resetOpen}
        user={resetUser}
        onClose={() => {
          setResetOpen(false);
          setResetUser(null);
        }}
      />
    </Card>
  );
}
