/**
 * 权限组管理页：列表 + 新增/编辑弹窗 + 分配权限弹窗。
 * 权限组将一组权限打包成可复用单元，角色绑定后解析时 UNION 组内权限。
 * 权限树从 /permission/list 拉取扁平数据后前端构建树，
 * 回显时拉取权限组已分配的权限 id 集合。
 */
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Tree,
  Typography,
  message,
  type TableColumnsType,
  type TreeDataNode,
} from 'antd';
import { useTranslation } from 'react-i18next';
import type { ID, PageResult, Permission, PermissionGroup } from '@fs/types';
import { fs } from '@/request';
import { PermissionGuard, withPermission } from '@/components/permission';
import { useDebounceSearch } from '@/hooks/useDebounceSearch';
import { ImeInput } from '@/components/ImeInput';

const EditButton = withPermission({ codes: ['permission-group:update'], fallback: 'disable' })(Button);
const DeleteConfirm = withPermission({ codes: ['permission-group:delete'] })(Popconfirm);
const AssignButton = withPermission({ codes: ['permission-group:assign'], fallback: 'disable' })(Button);

/** 扁平权限列表 → antd Tree 数据 */
function buildTree(list: Permission[]): TreeDataNode[] {
  const map = new Map<ID, TreeDataNode>();
  const roots: TreeDataNode[] = [];
  list.forEach((p) => {
    map.set(p.id, { key: p.id, title: `${p.name} (${p.code})`, children: [] });
  });
  list.forEach((p) => {
    const node = map.get(p.id)!;
    if (p.parentId && map.has(p.parentId)) {
      (map.get(p.parentId)!.children as TreeDataNode[]).push(node);
    } else {
      roots.push(node);
    }
  });
  return roots;
}

export default function PermissionGroupListPage() {
  const { t } = useTranslation();
  const [data, setData] = useState<PageResult<PermissionGroup>>({
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

  // 表单弹窗
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<PermissionGroup | null>(null);
  const [form] = Form.useForm();

  // 权限分配弹窗
  const [permOpen, setPermOpen] = useState(false);
  const [permGroup, setPermGroup] = useState<PermissionGroup | null>(null);
  const [allPerms, setAllPerms] = useState<Permission[]>([]);
  const [checkedKeys, setCheckedKeys] = useState<React.Key[]>([]);

  const treeData = useMemo(() => buildTree(allPerms), [allPerms]);

  const reload = (page = 1, size = 10, kw = keywordRef.current) => {
    setLoading(true);
    fs.get<PageResult<PermissionGroup>>('/permission-group/list', { params: { page, size, keyword: kw } })
      .then((res) => setData(res))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    reload(1, 10);
  }, []);

  const onCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ status: 1, sort: 0 });
    setFormOpen(true);
  };

  const onEdit = (record: PermissionGroup) => {
    setEditing(record);
    form.setFieldsValue({
      code: record.code,
      name: record.name,
      status: record.status ?? 1,
      sort: record.sort ?? 0,
      description: record.description,
    });
    setFormOpen(true);
  };

  const onSave = async () => {
    const values = await form.validateFields();
    if (editing) {
      await fs.put<PermissionGroup>('/permission-group', { ...values, id: editing.id });
    } else {
      await fs.post<PermissionGroup>('/permission-group', values);
    }
    message.success(t('permissionGroup.saveSuccess'));
    setFormOpen(false);
    reload(data.pageNum, data.pageSize);
  };

  const onDelete = (record: PermissionGroup) => {
    fs.delete<void>(`/permission-group/${record.id}`).then(() => {
      message.success(t('permissionGroup.deleteSuccess'));
      reload(data.pageNum, data.pageSize);
    });
  };

  const onAssign = (record: PermissionGroup) => {
    setPermGroup(record);
    setCheckedKeys([]);
    fs.get<Permission[]>('/permission/list').then((perms) => {
      setAllPerms(perms);
      fs.get<ID[]>(`/permission-group/${record.id}/permissions`).then((ids) => {
        setCheckedKeys(ids);
        setPermOpen(true);
      });
    });
  };

  const onSavePerm = () => {
    if (!permGroup) return;
    fs.put<void>(`/permission-group/${permGroup.id}/permissions`, checkedKeys).then(() => {
      message.success(t('permissionGroup.assignSuccess'));
      setPermOpen(false);
    });
  };

  const columns: TableColumnsType<PermissionGroup> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: t('permissionGroup.code'), dataIndex: 'code', width: 160 },
    { title: t('permissionGroup.name'), dataIndex: 'name' },
    { title: t('permissionGroup.sort'), dataIndex: 'sort', width: 80 },
    { title: t('permissionGroup.description'), dataIndex: 'description', render: (v) => v ?? '-' },
    {
      title: t('permissionGroup.status'),
      dataIndex: 'status',
      width: 90,
      render: (s?: number) =>
        s === 1 ? <Tag color="green">{t('user.enabled')}</Tag> : <Tag>{t('user.disabled')}</Tag>,
    },
    { title: t('permissionGroup.createTime'), dataIndex: 'createTime', width: 180 },
    {
      title: t('common.operation'),
      key: '__op',
      width: 240,
      render: (_, record) => (
        <Space>
          <EditButton size="small" onClick={() => onEdit(record)}>
            {t('common.edit')}
          </EditButton>
          <AssignButton size="small" onClick={() => onAssign(record)}>
            {t('permissionGroup.assignPermission')}
          </AssignButton>
          <DeleteConfirm
            title={t('permissionGroup.deleteConfirm')}
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
          {t('permissionGroup.title')}
        </Typography.Title>
        <PermissionGuard codes={['permission-group:create']}>
          <Button type="primary" onClick={onCreate}>
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

      {/* 新增/编辑弹窗 */}
      <Modal
        title={editing ? t('permissionGroup.editTitle') : t('permissionGroup.createTitle')}
        open={formOpen}
        onOk={onSave}
        onCancel={() => setFormOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="code"
            label={t('permissionGroup.code')}
            rules={[{ required: true, message: t('permissionGroup.codeRequired') }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name="name"
            label={t('permissionGroup.name')}
            rules={[{ required: true, message: t('permissionGroup.nameRequired') }]}
          >
            <Input />
          </Form.Item>
          <Form.Item name="sort" label={t('permissionGroup.sort')}>
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="status" label={t('permissionGroup.status')}>
            <Select
              options={[
                { value: 1, label: t('user.enabled') },
                { value: 0, label: t('user.disabled') },
              ]}
            />
          </Form.Item>
          <Form.Item name="description" label={t('permissionGroup.description')}>
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 分配权限弹窗 */}
      <Modal
        title={permGroup ? t('permissionGroup.assignPermissionTitle', { name: permGroup.name }) : ''}
        open={permOpen}
        onOk={onSavePerm}
        onCancel={() => setPermOpen(false)}
        width={520}
        destroyOnClose
      >
        <Tree
          checkable
          treeData={treeData}
          checkedKeys={checkedKeys}
          onCheck={(keys) => setCheckedKeys(keys as React.Key[])}
          defaultExpandAll
        />
      </Modal>
    </Card>
  );
}
