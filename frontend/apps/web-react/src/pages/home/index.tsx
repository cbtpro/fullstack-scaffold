import { Card, Typography } from 'antd';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/store/auth';

export default function HomePage() {
  const { t } = useTranslation();
  const user = useAuth((s) => s.user);
  return (
    <Card>
      <Typography.Title level={3}>{t('menu.home')}</Typography.Title>
      <Typography.Paragraph>
        {user
          ? `${t('login.success')}，${user.nickname ?? user.username}`
          : t('login.success')}
      </Typography.Paragraph>
    </Card>
  );
}
