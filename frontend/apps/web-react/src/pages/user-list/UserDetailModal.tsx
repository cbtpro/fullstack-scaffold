/**
 * 用户详情弹窗。按 id 拉取后端最新数据展示，避免列表数据过期。
 */
import { useEffect, useState } from 'react';
import { Descriptions, Modal, Spin, Tag } from 'antd';
import { useTranslation } from 'react-i18next';
import type { User } from '@fs/types';
import { fs } from '@/request';

interface Props {
  open: boolean;
  record?: User;
  onClose: () => void;
}

export default function UserDetailModal({ open, record, onClose }: Props) {
  const { t } = useTranslation();
  const [detail, setDetail] = useState<User | null>(null);

  useEffect(() => {
    setDetail(null);
    if (!open || !record?.id) return;
    let active = true;
    fs.get<User>(`/user/${record.id}`)
      .then((u) => {
        if (active) setDetail(u);
      })
      .catch(() => {
        /* 业务错误已由请求层统一提示 */
      });
    return () => {
      active = false;
    };
  }, [open, record?.id]);

  const items = detail
    ? [
        { label: t('login.username'), children: detail.username },
        { label: t('user.nickname'), children: detail.nickname ?? '-' },
        { label: t('user.email'), children: detail.email ?? '-' },
        { label: t('user.phone'), children: detail.phone ?? '-' },
        {
          label: t('user.status'),
          children: (
            <Tag color={detail.status === 1 ? 'green' : 'default'}>
              {detail.status === 1 ? t('user.enabled') : t('user.disabled')}
            </Tag>
          ),
        },
        { label: t('user.createTime'), children: detail.createTime ?? '-' },
        { label: t('user.updateTime'), children: detail.updateTime ?? '-' },
      ]
    : [];

  return (
    <Modal open={open} title={t('user.detailTitle')} footer={null} onCancel={onClose}>
      {detail ? (
        <Descriptions column={1} size="small" items={items} />
      ) : (
        <Spin style={{ display: 'block', margin: '48px auto' }} />
      )}
    </Modal>
  );
}
