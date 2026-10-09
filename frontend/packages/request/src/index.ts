/**
 * axios HTTP 客户端封装。依赖 @fs/auth 注入 token、@fs/types 对齐响应契约。
 *
 * - createRequest 工厂返回 FsRequest，带 typed get/post/put/delete，自动解包 ApiResponse.data
 * - 401 自动清 token 并回调 onAuthExpired（业务侧跳登录页）
 * - 全局 loading 计数总线，供布局订阅显示/隐藏骨架
 *
 * 依赖方向：@fs/request → @fs/auth + @fs/types，单向不循环。
 */
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios';
import axios from 'axios';
import { clearToken, getToken } from '@fs/auth';
import { SUCCESS_CODE, UNAUTHORIZED_CODE, type ApiResponse } from '@fs/types';

/** 业务错误（响应到达但 code 非 0）。 */
export class FsBusinessError extends Error {
  code: number;
  constructor(message: string, code: number) {
    super(message);
    this.name = 'FsBusinessError';
    this.code = code;
  }
}

/** createRequest 配置。 */
export interface RequestConfig {
  baseURL?: string;
  timeout?: number;
  withCredentials?: boolean;
  headers?: Record<string, string>;
  /** 自定义 Authorization 头拼装，默认 `Bearer ${token}`。 */
  buildAuthHeader?: (token: string) => string;
  /** 业务成功的回调（code === 0）。 */
  onSuccess?: (data: unknown, response: AxiosResponse<ApiResponse>) => void;
  /** 业务错误（code 非 0 且非 401）回调。 */
  onBusinessError?: (error: FsBusinessError) => void;
  /** 401 未授权回调，业务侧通常跳登录页。 */
  onAuthExpired?: () => void;
  /** 网络层错误回调。 */
  onNetworkError?: (error: unknown) => void;
}

/** loading 计数总线，订阅者可据此显示全局骨架。 */
interface LoadingBus {
  subscribe(listener: (loading: boolean) => void): () => void;
  start(): void;
  done(): void;
}

function createLoadingBus(): LoadingBus {
  const listeners = new Set<(loading: boolean) => void>();
  let count = 0;
  let active = false;
  const notify = () => listeners.forEach((fn) => fn(active));
  return {
    subscribe(listener) {
      listeners.add(listener);
      listener(active);
      return () => listeners.delete(listener);
    },
    start() {
      count++;
      if (!active) {
        active = true;
        notify();
      }
    },
    done() {
      if (count > 0) count--;
      if (count === 0 && active) {
        active = false;
        notify();
      }
    },
  };
}

/** 单例全局 loading 总线，供布局直接订阅。 */
export const loadingBus = createLoadingBus();

/** 对外请求客户端。 */
export interface FsRequest {
  instance: AxiosInstance;
  get<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T>;
  post<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>;
  put<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>;
  delete<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T>;
  request<T = unknown>(config: AxiosRequestConfig): Promise<T>;
}

export function createRequest(config: RequestConfig = {}): FsRequest {
  const instance = axios.create({
    baseURL: config.baseURL,
    timeout: config.timeout ?? 15000,
    withCredentials: config.withCredentials ?? false,
    headers: config.headers,
  });

  const buildHeader = config.buildAuthHeader ?? ((token) => `Bearer ${token}`);

  instance.interceptors.request.use((req) => {
    loadingBus.start();
    const token = getToken();
    if (token && req.headers) {
      req.headers.Authorization = buildHeader(token);
    }
    return req;
  });

  instance.interceptors.response.use(
    (response) => {
      loadingBus.done();
      const body = response.data as ApiResponse;
      if (body && typeof body.code === 'number') {
        if (body.code === SUCCESS_CODE) {
          config.onSuccess?.(body.data, response as AxiosResponse<ApiResponse>);
          return body.data;
        }
        if (body.code === UNAUTHORIZED_CODE) {
          clearToken();
          config.onAuthExpired?.();
          return Promise.reject(new FsBusinessError(body.message, body.code));
        }
        const error = new FsBusinessError(body.message, body.code);
        config.onBusinessError?.(error);
        return Promise.reject(error);
      }
      return response.data;
    },
    (error) => {
      loadingBus.done();
      // HTTP 401/403：token 缺失、过期或权限不足，统一清 token 并触发 onAuthExpired
      const status = error.response?.status;
      if (status === 401 || status === 403) {
        clearToken();
        config.onAuthExpired?.();
        const body = error.response?.data as ApiResponse | undefined;
        const message = body?.message ?? (status === 401 ? '未登录或token无效' : '无权限访问');
        return Promise.reject(new FsBusinessError(message, status));
      }
      config.onNetworkError?.(error);
      return Promise.reject(error);
    },
  );

  const unwrap = <T>(p: Promise<unknown>): Promise<T> => p as Promise<T>;

  return {
    instance,
    get: <T = unknown>(url: string, cfg?: AxiosRequestConfig) => unwrap<T>(instance.get(url, cfg)),
    post: <T = unknown>(url: string, data?: unknown, cfg?: AxiosRequestConfig) =>
      unwrap<T>(instance.post(url, data, cfg)),
    put: <T = unknown>(url: string, data?: unknown, cfg?: AxiosRequestConfig) =>
      unwrap<T>(instance.put(url, data, cfg)),
    delete: <T = unknown>(url: string, cfg?: AxiosRequestConfig) => unwrap<T>(instance.delete(url, cfg)),
    request: <T = unknown>(cfg: AxiosRequestConfig) => unwrap<T>(instance.request(cfg)),
  };
}
