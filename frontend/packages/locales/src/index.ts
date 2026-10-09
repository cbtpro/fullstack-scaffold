/**
 * i18n 资源与工厂。React 端用 i18next，Vue 端用 vue-i18n，翻译资源一份。
 *
 * 工厂内对框架库使用动态 import，避免 React 应用打包 vue-i18n（反之亦然），
 * 同时资源对象用 export const / export default（不用 export =）规避 ESM 警告。
 */

export const zhCN = {
  common: {
    confirm: '确认',
    cancel: '取消',
    save: '保存',
    create: '新增',
    view: '查看',
    edit: '编辑',
    delete: '删除',
    search: '查询',
    reset: '重置',
    loading: '加载中...',
    success: '成功',
    failed: '失败',
    back: '返回',
    operation: '操作',
  },
  menu: {
    home: '首页',
    profile: '个人中心',
    user: '用户管理',
  },
  login: {
    title: '欢迎登录',
    username: '用户名',
    usernamePlaceholder: '请输入用户名',
    password: '密码',
    passwordPlaceholder: '请输入密码',
    submit: '登录',
    usernameRequired: '请输入用户名',
    passwordRequired: '请输入密码',
    success: '登录成功',
    failed: '登录失败',
    logout: '退出登录',
  },
  user: {
    list: '用户列表',
    total: '共 {count} 条',
    nickname: '昵称',
    email: '邮箱',
    phone: '手机号',
    roles: '角色',
    status: '状态',
    enabled: '启用',
    disabled: '禁用',
    createTime: '创建时间',
    updateTime: '更新时间',
    createTitle: '新增用户',
    editTitle: '编辑用户',
    detailTitle: '用户详情',
    saveSuccess: '保存成功',
    deleteSuccess: '删除成功',
    deleteConfirm: '确认删除该用户？',
    passwordHint: '不填默认为 123456',
    passwordEditHint: '留空则不修改密码',
    emailInvalid: '邮箱格式不正确',
  },
};

export const enUS = {
  common: {
    confirm: 'Confirm',
    cancel: 'Cancel',
    save: 'Save',
    create: 'Create',
    view: 'View',
    edit: 'Edit',
    delete: 'Delete',
    search: 'Search',
    reset: 'Reset',
    loading: 'Loading...',
    success: 'Success',
    failed: 'Failed',
    back: 'Back',
    operation: 'Actions',
  },
  menu: {
    home: 'Home',
    profile: 'Profile',
    user: 'User Management',
  },
  login: {
    title: 'Sign in',
    username: 'Username',
    usernamePlaceholder: 'Enter username',
    password: 'Password',
    passwordPlaceholder: 'Enter password',
    submit: 'Sign in',
    usernameRequired: 'Please enter username',
    passwordRequired: 'Please enter password',
    success: 'Signed in',
    failed: 'Sign in failed',
    logout: 'Sign out',
  },
  user: {
    list: 'User List',
    total: 'Total {count}',
    nickname: 'Nickname',
    email: 'Email',
    phone: 'Phone',
    roles: 'Roles',
    status: 'Status',
    enabled: 'Enabled',
    disabled: 'Disabled',
    createTime: 'Created At',
    updateTime: 'Updated At',
    createTitle: 'Create User',
    editTitle: 'Edit User',
    detailTitle: 'User Detail',
    saveSuccess: 'Saved',
    deleteSuccess: 'Deleted',
    deleteConfirm: 'Delete this user?',
    passwordHint: 'Defaults to 123456 if left empty',
    passwordEditHint: 'Leave blank to keep current password',
    emailInvalid: 'Invalid email format',
  },
};

export type Locale = 'zh-CN' | 'en-US';

/** 默认资源合集，可直接喂给 createReactI18n / createVueI18n。 */
export const messages = {
  'zh-CN': zhCN,
  'en-US': enUS,
};

export type Messages = typeof messages;

/** 资源结构：locale -> namespace -> 翻译键。 */
export type Resources = Record<string, Record<string, unknown>>;

/**
 * 创建 i18next 实例（React 端用）。返回已 init 的实例，可直接与
 * react-i18next 桥接。动态加载 i18next，避免 Vue 应用打包负担。
 *
 * <p>i18next 与 vue-i18n 的资源结构不同：i18next 要求 `{ lng: { namespace: { key } } }`，
 * 而 vue-i18n 要求 `{ lng: { key } }`。组件调用 `t('login.title')` 时，i18next 在默认
 * namespace `translation` 下查找嵌套 key `login.title`，故需将资源包裹到 `translation` 下。
 */
export async function createReactI18n(resources: Resources = messages, locale: Locale = 'zh-CN') {
  const { default: i18next } = await import('i18next');
  const instance = i18next.createInstance();
  // 将 { lng: { login: {...}, common: {...} } } 转为 { lng: { translation: { login: {...}, common: {...} } } }
  const wrapped: Resources = {};
  for (const [lng, val] of Object.entries(resources)) {
    wrapped[lng] = { translation: val };
  }
  await instance.init({
    resources: wrapped as never,
    lng: locale,
    fallbackLng: 'zh-CN',
    interpolation: { escapeValue: false },
  });
  return instance;
}

/**
 * 创建 vue-i18n 实例（Vue 端用）。返回可直接 app.use 的 i18n 对象。
 * 动态加载 vue-i18n，避免 React 应用打包负担。
 */
export async function createVueI18n(resources: Resources = messages, locale: Locale = 'zh-CN') {
  const { createI18n } = await import('vue-i18n');
  return createI18n({
    legacy: false,
    locale,
    fallbackLocale: 'zh-CN',
    messages: resources as never,
  });
}
