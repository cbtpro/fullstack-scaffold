/**
 * 字典管理页：左侧字典类型、右侧该类型下的字典项。
 * 两类数据各自独立增删改，但以「类型 → 项」的从属关系呈现，
 * 选中左侧类型后右侧才可操作，避免字典项脱离类型被孤立维护。
 */
import { useEffect, useState } from 'react';
import {
  Button,
  Card,
  Col,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
  type TableColumnsType,
} from 'antd';
import { useTranslation } from 'react-i18next';
import type { DictItem, DictType, PageResult } from '@fs/types';
import { fs } from '@/request';
import { PermissionGuard, withPermission } from '@/components/permission';

const TypeEditButton = withPermission({ codes: ['dict-type:update'], fallback: 'disable' })(Button);
const TypeDeleteConfirm = withPermission({ codes: ['dict-type:delete'] })(Popconfirm);
const ItemEditButton = withPermission({ codes: ['dict-item:update'], fallback: 'disable' })(Button);
const ItemDeleteConfirm = withPermission({ codes: ['dict-item:delete'] })(Popconfirm);

const STATUS_OPTIONS = (t: (k: string) => string) => [
  { value: 1, label: t('dict.enabled') },
  { value: 0, label: t('dict.disabled') },
];

export default function DictPage() {
  const { t } = useTranslation();

  const [types, setTypes] = useState<PageResult<DictType>>({ list: [], total: 0, pageNum: 1, pageSize: 10 });
  const [typesLoading, setTypesLoading] = useState(false);
  const [selectedType, setSelectedType] = useState<DictType | null>(null);

  const [items, setItems] = useState<PageResult<DictItem>>({ list: [], total: 0, pageNum: 1, pageSize: 10 });
  const [itemsLoading, setItemsLoading] = useState(false);

  const [typeOpen, setTypeOpen] = useState(false);
  const [editingType, setEditingType] = useState<DictType | null>(null);
  const [typeForm] = Form.useForm();

  const [itemOpen, setItemOpen] = useState(false);
  const [editingItem, setEditingItem] = useState<DictItem | null>(null);
  const [itemForm] = Form.useForm();

  const loadTypes = (page = 1, size = 10) => {
    setTypesLoading(true);
    fs.get<PageResult<DictType>>('/dict-type/list', { params: { page, size } })
      .then((res) => setTypes(res))
      .finally(() => setTypesLoading(false));
  };

  const loadItems = (typeId: string | number, page = 1, size = 10) => {
    setItemsLoading(true);
    fs.get<PageResult<DictItem>>('/dict-item/list', { params: { page, size, typeId } })
      .then((res) => setItems(res))
      .finally(() => setItemsLoading(false));
  };

  useEffect(() => {
    loadTypes(1, 10);
  }, []);

  // 点击左侧类型行：右栏切换到该类型的字典项
  const onSelectType = (record: DictType) => {
    setSelectedType(record);
    loadItems(record.id, 1, 10);
  };

  // ------------------------------------------------------------------ 字典类型
  const onCreateType = () => {
    setEditingType(null);
    typeForm.resetFields();
    typeForm.setFieldsValue({ status: 1, sort: 0 });
    setTypeOpen(true);
  };

  const onEditType = (record: DictType) => {
    setEditingType(record);
    typeForm.setFieldsValue({
      code: record.code,
      name: record.name,
      description: record.description,
      status: record.status ?? 1,
      sort: record.sort ?? 0,
    });
    setTypeOpen(true);
  };

  const onSaveType = async () => {
    const values = await typeForm.validateFields();
    if (editingType) {
      await fs.put<DictType>('/dict-type', { ...values, id: editingType.id });
    } else {
      await fs.post<DictType>('/dict-type', values);
    }
    message.success(t('dict.saveSuccess'));
    setTypeOpen(false);
    loadTypes(types.pageNum, types.pageSize);
  };

  const onDeleteType = (record: DictType) => {
    fs.delete<void>(`/dict-type/${record.id}`).then(() => {
      message.success(t('dict.deleteSuccess'));
      // 被删类型正是当前选中项时，清空右栏，避免展示已不存在的字典项
      if (selectedType?.id === record.id) {
        setSelectedType(null);
        setItems({ list: [], total: 0, pageNum: 1, pageSize: 10 });
      }
      loadTypes(types.pageNum, types.pageSize);
    });
  };

  // ------------------------------------------------------------------ 字典项
  const onCreateItem = () => {
    setEditingItem(null);
    itemForm.resetFields();
    itemForm.setFieldsValue({ status: 1, sort: 0 });
    setItemOpen(true);
  };

  const onEditItem = (record: DictItem) => {
    setEditingItem(record);
    itemForm.setFieldsValue({
      label: record.label,
      value: record.value,
      remark: record.remark,
      status: record.status ?? 1,
      sort: record.sort ?? 0,
    });
    setItemOpen(true);
  };

  const onSaveItem = async () => {
    if (!selectedType) return;
    const values = await itemForm.validateFields();
    if (editingItem) {
      await fs.put<DictItem>('/dict-item', { ...values, id: editingItem.id, typeId: selectedType.id });
    } else {
      await fs.post<DictItem>('/dict-item', { ...values, typeId: selectedType.id });
    }
    message.success(t('dict.saveSuccess'));
    setItemOpen(false);
    loadItems(selectedType.id, items.pageNum, items.pageSize);
  };

  const onDeleteItem = (record: DictItem) => {
    fs.delete<void>(`/dict-item/${record.id}`).then(() => {
      message.success(t('dict.deleteSuccess'));
      if (selectedType) loadItems(selectedType.id, items.pageNum, items.pageSize);
    });
  };

  const typeColumns: TableColumnsType<DictType> = [
    { title: t('dict.code'), dataIndex: 'code', width: 150 },
    { title: t('dict.name'), dataIndex: 'name', ellipsis: true },
    { title: t('dict.sort'), dataIndex: 'sort', width: 70 },
    {
      title: t('common.operation'),
      key: '__op',
      width: 150,
      render: (_, record) => (
        <Space>
          <TypeEditButton size="small" onClick={() => onEditType(record)}>
            {t('common.edit')}
          </TypeEditButton>
          <TypeDeleteConfirm
            title={t('dict.deleteTypeConfirm')}
            okText={t('common.confirm')}
            cancelText={t('common.cancel')}
            onConfirm={() => onDeleteType(record)}
          >
            <Button danger size="small">
              {t('common.delete')}
            </Button>
          </TypeDeleteConfirm>
        </Space>
      ),
    },
  ];

  const itemColumns: TableColumnsType<DictItem> = [
    { title: t('dict.label'), dataIndex: 'label', ellipsis: true },
    { title: t('dict.value'), dataIndex: 'value', ellipsis: true },
    { title: t('dict.sort'), dataIndex: 'sort', width: 70 },
    {
      title: t('dict.status'),
      dataIndex: 'status',
      width: 90,
      render: (s?: number) =>
        s === 1 ? <Tag color="green">{t('dict.enabled')}</Tag> : <Tag>{t('dict.disabled')}</Tag>,
    },
    {
      title: t('common.operation'),
      key: '__op',
      width: 150,
      render: (_, record) => (
        <Space>
          <ItemEditButton size="small" onClick={() => onEditItem(record)}>
            {t('common.edit')}
          </ItemEditButton>
          <ItemDeleteConfirm
            title={t('dict.deleteItemConfirm')}
            okText={t('common.confirm')}
            cancelText={t('common.cancel')}
            onConfirm={() => onDeleteItem(record)}
          >
            <Button danger size="small">
              {t('common.delete')}
            </Button>
          </ItemDeleteConfirm>
        </Space>
      ),
    },
  ];

  return (
    <Card>
      <Typography.Title level={3} style={{ margin: 0, marginBottom: 16 }}>
        {t('dict.title')}
      </Typography.Title>
      <Row gutter={16}>
        <Col span={10}>
          <Card
            size="small"
            title={t('dict.types')}
            extra={
              <PermissionGuard codes={['dict-type:create']}>
                <Button type="primary" size="small" onClick={onCreateType}>
                  {t('common.create')}
                </Button>
              </PermissionGuard>
            }
          >
            <Table
              rowKey="id"
              size="small"
              loading={typesLoading}
              dataSource={types.list}
              columns={typeColumns}
              rowClassName={(record) => (selectedType?.id === record.id ? 'dict-type-selected' : '')}
              onRow={(record) => ({ onClick: () => onSelectType(record) })}
              pagination={{
                total: types.total,
                current: types.pageNum,
                pageSize: types.pageSize,
                showTotal: (total) => t('user.total', { count: total }),
                onChange: (page, pageSize) => loadTypes(page, pageSize),
              }}
            />
          </Card>
        </Col>
        <Col span={14}>
          <Card
            size="small"
            title={selectedType ? `${t('dict.items')} - ${selectedType.name}` : t('dict.items')}
            extra={
              <PermissionGuard codes={['dict-item:create']}>
                <Button type="primary" size="small" disabled={!selectedType} onClick={onCreateItem}>
                  {t('common.create')}
                </Button>
              </PermissionGuard>
            }
          >
            <Table
              rowKey="id"
              size="small"
              loading={itemsLoading}
              dataSource={items.list}
              columns={itemColumns}
              locale={{ emptyText: selectedType ? undefined : t('dict.selectTypeFirst') }}
              pagination={{
                total: items.total,
                current: items.pageNum,
                pageSize: items.pageSize,
                showTotal: (total) => t('user.total', { count: total }),
                onChange: (page, pageSize) => selectedType && loadItems(selectedType.id, page, pageSize),
              }}
            />
          </Card>
        </Col>
      </Row>

      {/* 字典类型表单 */}
      <Modal
        title={editingType ? t('dict.editTypeTitle') : t('dict.createTypeTitle')}
        open={typeOpen}
        onOk={onSaveType}
        onCancel={() => setTypeOpen(false)}
        destroyOnClose
      >
        <Form form={typeForm} layout="vertical">
          <Form.Item
            name="code"
            label={t('dict.code')}
            rules={[{ required: true, message: t('dict.codeRequired') }]}
          >
            <Input disabled={!!editingType} />
          </Form.Item>
          <Form.Item
            name="name"
            label={t('dict.name')}
            rules={[{ required: true, message: t('dict.nameRequired') }]}
          >
            <Input />
          </Form.Item>
          <Form.Item name="sort" label={t('dict.sort')}>
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="status" label={t('dict.status')}>
            <Select options={STATUS_OPTIONS(t)} />
          </Form.Item>
          <Form.Item name="description" label={t('dict.description')}>
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 字典项表单 */}
      <Modal
        title={editingItem ? t('dict.editItemTitle') : t('dict.createItemTitle')}
        open={itemOpen}
        onOk={onSaveItem}
        onCancel={() => setItemOpen(false)}
        destroyOnClose
      >
        <Form form={itemForm} layout="vertical">
          <Form.Item
            name="label"
            label={t('dict.label')}
            rules={[{ required: true, message: t('dict.labelRequired') }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name="value"
            label={t('dict.value')}
            rules={[{ required: true, message: t('dict.valueRequired') }]}
          >
            <Input />
          </Form.Item>
          <Form.Item name="sort" label={t('dict.sort')}>
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="status" label={t('dict.status')}>
            <Select options={STATUS_OPTIONS(t)} />
          </Form.Item>
          <Form.Item name="remark" label={t('dict.remark')}>
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
