/** 403 页：已登录但权限不足访问被守卫路由时的兜底展示 */
import { Button, Result } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

export default function ForbiddenPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  return (
    <Result
      status="403"
      title="403"
      subTitle={t('error.forbiddenDesc')}
      extra={
        <Button type="primary" onClick={() => navigate('/home')}>
          {t('error.backHome')}
        </Button>
      }
    />
  );
}
