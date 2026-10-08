import { createError, defineEventHandler, readBody } from 'h3';
import type { ApiResponse, LoginRequest, LoginResult, User } from '@fs/types';
import { signToken } from '../utils/jwt';

/**
 * POST /api/login
 * 校验 admin/123456 后签发 token。仅作示例，禁止用于生产。
 */
const MOCK_USERS: Record<string, { password: string; user: User }> = {
  admin: {
    password: '123456',
    user: {
      id: 1,
      username: 'admin',
      nickname: '管理员',
      avatar: '',
      email: 'admin@fullstack-scaffold.dev',
      roles: ['ADMIN'],
      permissions: ['*:*:*'],
      status: 1,
      createTime: '2026-01-01 00:00:00',
      updateTime: '2026-01-01 00:00:00',
    },
  },
};

export default defineEventHandler(async (event) => {
  const body = await readBody<LoginRequest>(event);
  const username = body?.username?.trim();
  const password = body?.password;
  if (!username || !password) {
    throw createError({ statusCode: 400, statusMessage: '用户名或密码不能为空' });
  }
  const record = MOCK_USERS[username];
  if (!record || record.password !== password) {
    return {
      code: 401,
      message: '用户名或密码错误',
      data: null,
    } satisfies ApiResponse<null>;
  }
  const token = await signToken({
    sub: String(record.user.id),
    username: record.user.username,
    roles: record.user.roles,
  });
  const result: LoginResult = { token, user: record.user };
  return { code: 0, message: 'ok', data: result } satisfies ApiResponse<LoginResult>;
});
