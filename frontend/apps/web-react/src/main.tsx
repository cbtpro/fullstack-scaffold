import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { ConfigProvider, Spin } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { RouterProvider } from 'react-router-dom';
import { I18nextProvider } from 'react-i18next';
import { createReactI18n } from '@fs/locales';
import { router } from './router';
import './index.css';

const i18n = await createReactI18n();

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ConfigProvider locale={zhCN} theme={{ token: { borderRadius: 0 } }}>
      <I18nextProvider i18n={i18n}>
        <RouterProvider router={router} fallbackElement={<Spin />} />
      </I18nextProvider>
    </ConfigProvider>
  </StrictMode>,
);
