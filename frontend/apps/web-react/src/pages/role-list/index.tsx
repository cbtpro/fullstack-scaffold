/**
 * 角色管理页：列表 + 新增/编辑弹窗 + 分配权限弹窗。
 * 权限树从 /permission/list 拉取扁平数据后前端构建树，
 * 回显时拉取角色已分配的权限 id 集合。
 */
import { useEffect, useMemo, useState } from 'react';
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
import type { PageResult, Permission, PermissionGroup, Role } from '@fs/types';
import { fs } from '@/request';
import { PermissionGuard, withPermission } from '@/components/permission';

const EditButton = withPermission({ codes: ['role:update'], fallback: 'disable' })(Button);
const DeleteConfirm = withPermission({ codes: ['role:delete'] })(Popconfirm);
const AssignButton = withPermission({ codes: ['role:assign'], fallback: 'disable' })(Button);
const AssignGroupButton = withPermission({ codes: ['role:assign-group'], fallback: 'disable' })(Button);

/** 扁平权限列表 → antd Tree 数据 */
function buildTree(list: Permission[]): TreeDataNode[] {
  const map = new Map<number, TreeDataNode>();
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

export default function RoleListPage() {
  const { t } = useTranslation();
  const [data, setData] = useState<PageResult<Role>>({ list: [], total: 0, pageNum: 1, pageSize: 10 });
  const [loading, setLoading] = useState(false);

  // 表单弹窗
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Role | null>(null);
  const [form] = Form.useForm();

  // 权限分配弹窗
  const [permOpen, setPermOpen] = useState(false);
  const [permRole, setPermRole] = useState<Role | null>(null);
  const [allPerms, setAllPerms] = useState<Permission[]>([]);
  const [checkedKeys, setCheckedKeys] = useState<React.Key[]>([]);

  // 权限组分配弹窗
  const [groupOpen, setGroupOpen] = useState(false);
  const [groupRole, setGroupRole] = useState<Role | null>(null);
  const [allGroups, setAllGroups] = useState<PermissionGroup[]>([]);
  const [selectedGroupIds, setSelectedGroupIds] = useState<number[]>([]);

  const treeData = useMemo(() => buildTree(allPerms), [allPerms]);

  const reload = (page = 1, size = 10) => {
    setLoading(true);
    fs.get<PageResult<Role>>('/role/list', { params: { page, size } })
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

  const onEdit = (record: Role) => {
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
      await fs.put<Role>('/role', { ...values, id: editing.id });
    } else {
      await fs.post<Role>('/role', values);
    }
    message.success(t('role.saveSuccess'));
    setFormOpen(false);
    reload(data.pageNum, data.pageSize);
  };

  const onDelete = (record: Role) => {
    fs.delete<void>(`/role/${record.id}`).then(() => {
      message.success(t('role.deleteSuccess'));
      reload(data.pageNum, data.pageSize);
    });
  };

  const onAssign = (record: Role) => {
    setPermRole(record);
    setCheckedKeys([]);
    fs.get<Permission[]>('/permission/list').then((perms) => {
      setAllPerms(perms);
      fs.get<number[]>(`/role/${record.id}/permissions`).then((ids) => {
        setCheckedKeys(ids);
        setPermOpen(true);
      });
    });
  };

  const onSavePerm = () => {
    if (!permRole) return;
    fs.put<void>(`/role/${permRole.id}/permissions`, checkedKeys).then(() => {
      message.success(t('role.assignSuccess'));
      setPermOpen(false);
    });
  };

  const onAssignGroup = (record: Role) => {
    setGroupRole(record);
    setSelectedGroupIds([]);
    fs.get<PermissionGroup[]>('/permission-group/all').then((groups) => {
      setAllGroups(groups);
      fs.get<number[]>(`/role/${record.id}/groups`).then((ids) => {
        setSelectedGroupIds(ids);
        setGroupOpen(true);
      });
    });
  };

  const onSaveGroup = () => {
    if (!groupRole) return;
    fs.put<void>(`/role/${groupRole.id}/groups`, selectedGroupIds).then(() => {
      message.success(t('role.assignGroupSuccess'));
      setGroupOpen(false);
    });
  };

  const columns: TableColumnsType<Role> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: t('role.code'), dataIndex: 'code', width: 160 },
    { title: t('role.name'), dataIndex: 'name' },
    { title: t('role.sort'), dataIndex: 'sort', width: 80 },
    { title: t('role.description'), dataIndex: 'description', render: (v) => v ?? '-' },
    {
      title: t('role.status'),
      dataIndex: 'status',
      width: 90,
      render: (s?: number) =>
        s === 1 ? <Tag color="green">{t('user.enabled')}</Tag> : <Tag>{t('user.disabled')}</Tag>,
    },
    { title: t('role.createTime'), dataIndex: 'createTime', width: 180 },
    {
      title: t('common.operation'),
      key: '__op',
      width: 320,
      render: (_, record) => (
        <Space>
          <EditButton size="small" onClick={() => onEdit(record)}>
            {t('common.edit')}
          </EditButton>
          <AssignButton size="small" onClick={() => onAssign(record)}>
            {t('role.assignPermission')}
          </AssignButton>
          <AssignGroupButton size="small" onClick={() => onAssignGroup(record)}>
            {t('role.assignGroup')}
          </AssignGroupButton>
          <DeleteConfirm
            title={t('role.deleteConfirm')}
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
          {t('role.title')}
        </Typography.Title>
        <PermissionGuard codes={['role:create']}>
          <Button type="primary" onClick={onCreate}>
            {t('common.create')}
          </Button>
        </PermissionGuard>
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
        title={editing ? t('role.editTitle') : t('role.createTitle')}
        open={formOpen}
        onOk={onSave}
        onCancel={() => setFormOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="code" label={t('role.code')} rules={[{ required: true, message: t('role.codeRequired') }]}>
            <Input />
          </Form.Item>
          <Form.Item name="name" label={t('role.name')} rules={[{ required: true, message: t('role.nameRequired') }]}>
            <Input />
          </Form.Item>
          <Form.Item name="sort" label={t('role.sort')}>
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="status" label={t('role.status')}>
            <Select
              options={[
                { value: 1, label: t('user.enabled') },
                { value: 0, label: t('user.disabled') },
              ]}
            />
          </Form.Item>
          <Form.Item name="description" label={t('role.description')}>
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 分配权限弹窗 */}
      <Modal
        title={permRole ? t('role.assignPermissionTitle', { name: permRole.name }) : ''}
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

      {/* 分配权限组弹窗 */}
      <Modal
        title={groupRole ? t('role.assignGroupTitle', { name: groupRole.name }) : ''}
        open={groupOpen}
        onOk={onSaveGroup}
        onCancel={() => setGroupOpen(false)}
        width={480}
        destroyOnClose
      >
        <Select
          mode="multiple"
          style={{ width: '100%' }}
          value={selectedGroupIds}
          onChange={(vals: number[]) => setSelectedGroupIds(vals)}
          options={allGroups.map((g) => ({ value: g.id, label: `${g.name} (${g.code})` }))}
        />
      </Modal>
    </Card>
  );
}
