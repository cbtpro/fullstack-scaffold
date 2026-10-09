/** 404 页：未匹配任何路由时的兜底展示 */
import { Button, Result } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

export default function NotFoundPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  return (
    <Result
      status="404"
      title="404"
      subTitle={t('error.notFoundDesc')}
      extra={
        <Button type="primary" onClick={() => navigate('/home')}>
          {t('error.backHome')}
        </Button>
      }
    />
  );
}
