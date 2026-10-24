/**
 * 全栈脚手架共享类型。后端 {@code Result<T>} / {@code PageResult<T>} 的前端镜像，
 * 保证前后端契约一致。业务侧可在此扩展领域模型。
 */

/**
 * 实体主键。后端雪花主键为 19 位 Long，超出 JS 安全整数范围，序列化为字符串下发；
 * 小整数（如 parentId=0）仍为数字，故取值类型统一为 number | string。
 */
export type ID = number | string;

/** 角色。对应后端 sys_role 表。 */
export interface Role {
  id: ID;
  code: string;
  name: string;
  status?: number;
  sort?: number;
  description?: string;
  createTime?: string;
  updateTime?: string;
}

/** 权限。对应后端 sys_permission 表，覆盖菜单/按钮/接口三类。 */
export interface Permission {
  id: ID;
  parentId: ID;
  code: string;
  name: string;
  /** 类型：1菜单 2按钮 3接口 */
  type: number;
  path?: string;
  component?: string;
  icon?: string;
  sort?: number;
  status?: number;
  createTime?: string;
  updateTime?: string;
  /** 树形展示用：子节点 */
  children?: Permission[];
}

/** 权限组。对应后端 sys_permission_group 表，将一组权限打包成可复用单元。 */
export interface PermissionGroup {
  id: ID;
  code: string;
  name: string;
  description?: string;
  sort?: number;
  status?: number;
  createTime?: string;
  updateTime?: string;
}

/** 登录用户。字段与后端 user-service 返回对齐。 */
export interface User {
  id: ID;
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

/** 本人修改密码请求，用户身份由 token 解析。 */
export interface ChangePasswordRequest {
  oldPassword: string;
  newPassword: string;
}

/** 管理员重置密码请求。 */
export interface ResetPasswordRequest {
  newPassword: string;
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
  id: ID;
  userId: ID;
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

/** 操作日志，对应后端 sys_operation_log 表记录（只读，写入由后端切面完成）。 */
export interface OperationLog {
  id: ID;
  module: string;
  action: string;
  description?: string;
  httpMethod?: string;
  uri?: string;
  /** 请求参数快照，后端已脱敏并截断 */
  params?: string;
  success: boolean;
  errorMessage?: string;
  operatorId?: ID;
  operatorName?: string;
  clientIp?: string;
  durationMs?: number;
  createTime: string;
}

/** 登录日志，对应后端 sys_login_log 表记录（只读，写入由登录入口完成）。 */
export interface LoginLog {
  id: ID;
  username: string;
  /** LOGIN 登录 / LOGOUT 登出 */
  eventType: string;
  success: boolean;
  message?: string;
  clientIp?: string;
  userAgent?: string;
  createTime: string;
}

/** 字典类型，对应后端 sys_dict_type 表。 */
export interface DictType {
  id: ID;
  code: string;
  name: string;
  description?: string;
  sort: number;
  /** 启用状态：1 启用 0 禁用 */
  status?: number;
  createTime?: string;
  updateTime?: string;
}

/** 字典项，对应后端 sys_dict_item 表。 */
export interface DictItem {
  id: ID;
  typeId: ID;
  label: string;
  value: string;
  remark?: string;
  sort: number;
  status?: number;
  createTime?: string;
  updateTime?: string;
}

/** 本人资料维护入参，对应后端 ProfileUpdateRequest。 */
export interface ProfileUpdatePayload {
  nickname?: string;
  email?: string;
  phone?: string;
}

/** 约定成功业务码。 */
export const SUCCESS_CODE = 0;
/** 约定未授权业务码。 */
export const UNAUTHORIZED_CODE = 401;
