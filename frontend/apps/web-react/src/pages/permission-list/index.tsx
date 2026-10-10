/**
 * 权限管理页：树形表格展示 + 新增/编辑/删除弹窗。
 * 权限分三类：1菜单、2按钮、3接口，通过 parentId 构成树。
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
  Typography,
  message,
  type TableColumnsType,
} from 'antd';
import { useTranslation } from 'react-i18next';
import type { Permission } from '@fs/types';
import { fs } from '@/request';
import { PermissionGuard, withPermission } from '@/components/permission';

const EditButton = withPermission({ codes: ['permission:update'], fallback: 'disable' })(Button);
const DeleteConfirm = withPermission({ codes: ['permission:delete'] })(Popconfirm);

const TYPE_COLORS: Record<number, string> = { 1: 'blue', 2: 'green', 3: 'orange' };

/** 扁平列表 → 带 children 的树（用于 Table 树形展示） */
function buildTree(list: Permission[]): Permission[] {
  const map = new Map<number, Permission>();
  const roots: Permission[] = [];
  list.forEach((p) => map.set(p.id, { ...p, children: [] }));
  list.forEach((p) => {
    const node = map.get(p.id)!;
    if (p.parentId && map.has(p.parentId)) {
      map.get(p.parentId)!.children!.push(node);
    } else {
      roots.push(node);
    }
  });
  return roots;
}

export default function PermissionListPage() {
  const { t } = useTranslation();
  const [list, setList] = useState<Permission[]>([]);
  const [loading, setLoading] = useState(false);

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Permission | null>(null);
  const [form] = Form.useForm();

  const treeData = useMemo(() => buildTree(list), [list]);

  const reload = () => {
    setLoading(true);
    fs.get<Permission[]>('/permission/list')
      .then((res) => setList(res))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    reload();
  }, []);

  const parentOptions = useMemo(
    () => [{ value: 0, label: t('permission.root') }, ...list.map((p) => ({ value: p.id, label: `${p.name} (${p.code})` }))],
    [list, t],
  );

  const onCreate = (parentId?: number) => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ parentId: parentId ?? 0, type: 1, status: 1, sort: 0 });
    setFormOpen(true);
  };

  const onEdit = (record: Permission) => {
    setEditing(record);
    form.setFieldsValue({
      parentId: record.parentId,
      code: record.code,
      name: record.name,
      type: record.type,
      path: record.path,
      component: record.component,
      icon: record.icon,
      sort: record.sort ?? 0,
      status: record.status ?? 1,
    });
    setFormOpen(true);
  };

  const onSave = async () => {
    const values = await form.validateFields();
    if (editing) {
      await fs.put<Permission>('/permission', { ...values, id: editing.id });
    } else {
      await fs.post<Permission>('/permission', values);
    }
    message.success(t('permission.saveSuccess'));
    setFormOpen(false);
    reload();
  };

  const onDelete = (record: Permission) => {
    fs.delete<void>(`/permission/${record.id}`).then(() => {
      message.success(t('permission.deleteSuccess'));
      reload();
    });
  };

  const columns: TableColumnsType<Permission> = [
    { title: t('permission.name'), dataIndex: 'name' },
    { title: t('permission.code'), dataIndex: 'code', width: 200 },
    {
      title: t('permission.type'),
      dataIndex: 'type',
      width: 100,
      render: (type: number) => (
        <Tag color={TYPE_COLORS[type]}>{t(`permission.type${type === 1 ? 'Menu' : type === 2 ? 'Button' : 'Api'}`)}</Tag>
      ),
    },
    { title: t('permission.path'), dataIndex: 'path', render: (v) => v ?? '-', width: 140 },
    { title: t('permission.sort'), dataIndex: 'sort', width: 70 },
    {
      title: t('common.operation'),
      key: '__op',
      width: 200,
      render: (_, record) => (
        <Space>
          <EditButton size="small" onClick={() => onEdit(record)}>
            {t('common.edit')}
          </EditButton>
          <DeleteConfirm
            title={t('permission.deleteConfirm')}
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
          {t('permission.title')}
        </Typography.Title>
        <PermissionGuard codes={['permission:create']}>
          <Button type="primary" onClick={() => onCreate()}>
            {t('common.create')}
          </Button>
        </PermissionGuard>
      </Space>
      <Table
        rowKey="id"
        size="middle"
        loading={loading}
        dataSource={treeData}
        columns={columns}
        pagination={false}
        expandable={{ defaultExpandAllRows: true }}
      />

      <Modal
        title={editing ? t('permission.editTitle') : t('permission.createTitle')}
        open={formOpen}
        onOk={onSave}
        onCancel={() => setFormOpen(false)}
        destroyOnClose
        width={560}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="parentId" label={t('permission.parentId')}>
            <Select options={parentOptions} />
          </Form.Item>
          <Form.Item name="type" label={t('permission.type')} rules={[{ required: true, message: t('permission.typeRequired') }]}>
            <Select
              options={[
                { value: 1, label: t('permission.typeMenu') },
                { value: 2, label: t('permission.typeButton') },
                { value: 3, label: t('permission.typeApi') },
              ]}
            />
          </Form.Item>
          <Form.Item name="code" label={t('permission.code')} rules={[{ required: true, message: t('permission.codeRequired') }]}>
            <Input />
          </Form.Item>
          <Form.Item name="name" label={t('permission.name')} rules={[{ required: true, message: t('permission.nameRequired') }]}>
            <Input />
          </Form.Item>
          <Form.Item name="path" label={t('permission.path')}>
            <Input />
          </Form.Item>
          <Form.Item name="component" label={t('permission.component')}>
            <Input />
          </Form.Item>
          <Form.Item name="icon" label={t('permission.icon')}>
            <Input />
          </Form.Item>
          <Form.Item name="sort" label={t('permission.sort')}>
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="status" label={t('permission.status')}>
            <Select
              options={[
                { value: 1, label: t('user.enabled') },
                { value: 0, label: t('user.disabled') },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
