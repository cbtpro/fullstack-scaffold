/**
 * 全栈脚手架共享类型。后端 {@code Result<T>} / {@code PageResult<T>} 的前端镜像，
 * 保证前后端契约一致。业务侧可在此扩展领域模型。
 */

/** 登录用户。字段与后端 user-service 返回对齐。 */
export interface User {
  id: number | string;
  username: string;
  /** 密码。仅创建/修改请求携带；后端响应始终不返回（@JsonIgnore） */
  password?: string;
  nickname?: string;
  avatar?: string;
  email?: string;
  phone?: string;
  roles?: string[];
  permissions?: string[];
  status?: number;
  createTime?: string;
  updateTime?: string;
}

/** 登录请求。 */
export interface LoginRequest {
  username: string;
  password: string;
  /** 图形验证码 id（/auth/captcha 下发），登录时原样回传 */
  captchaId?: string;
  /** 用户输入的验证码 */
  captchaCode?: string;
  /** 设备指纹（sha256 hex），登录时上报用于 token 绑定 */
  deviceFingerprint?: string;
}

/** 图形验证码挑战。 */
export interface CaptchaChallenge {
  /** 验证码 id，登录时原样回传 */
  captchaId: string;
  /** base64 data URL 图片 */
  image: string;
}

/** 登录响应。 */
export interface LoginResult {
  token: string;
  user?: User;
  /** 过期时间戳（毫秒），可选，客户端可据此做提前刷新策略 */
  expireAt?: number;
}

/** 菜单/路由元信息。前端静态路由或后端下发菜单均按此结构。 */
export interface Menu {
  path: string;
  name: string;
  component?: string;
  redirect?: string;
  meta?: MenuMeta;
  children?: Menu[];
}

export interface MenuMeta {
  title?: string;
  icon?: string;
  keepAlive?: boolean;
  hidden?: boolean;
  affix?: boolean;
  roles?: string[];
  permissions?: string[];
  link?: string;
}

/** 后端统一响应体，对应 {@code com.scaffold.common.Result<T>}。 */
export interface ApiResponse<T = unknown> {
  code: number;
  message: string;
  data: T;
  timestamp?: number;
}

/** 分页结果，对应 {@code com.scaffold.common.PageResult<T>}。 */
export interface PageResult<T = unknown> {
  list: T[];
  total: number;
  pageNum: number;
  pageSize: number;
}

/** 设备风险等级，对应后端 RiskLevel 枚举名。 */
export type DeviceRiskLevel = 'TRUSTED' | 'NORMAL' | 'RISKY' | 'REVOKED';

/** 登录设备，对应后端 user_device 表记录。 */
export interface UserDevice {
  id: number;
  userId: number;
  deviceId: string;
  fingerprint: string;
  ip?: string;
  riskLevel: DeviceRiskLevel;
  lastLoginTime?: string;
  createTime?: string;
}

/** 分页查询参数，对应 {@code com.scaffold.common.PageQuery}。 */
export interface PageQuery {
  pageNum: number;
  pageSize: number;
  [key: string]: unknown;
}

/** 约定成功业务码。 */
export const SUCCESS_CODE = 0;
/** 约定未授权业务码。 */
export const UNAUTHORIZED_CODE = 401;
