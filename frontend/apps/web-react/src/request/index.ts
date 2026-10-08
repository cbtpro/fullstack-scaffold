/**
 * HTTP 客户端单例。基于 @fs/request 的 createRequest 工厂，
 * 配置业务约定的鉴权失败/业务错误/网络错误回调。
 */
import { createRequest } from '@fs/request';
import { clearToken } from '@fs/auth';
import { message } from 'antd';

export const fs = createRequest({
  baseURL: '/api',
  timeout: 15000,
  onAuthExpired() {
    clearToken();
    if (location.pathname !== '/login') {
      location.href = '/login';
    }
  },
  onBusinessError(error) {
    void message.error(error.message);
  },
  onNetworkError() {
    void message.error('网络异常，请稍后重试');
  },
});
