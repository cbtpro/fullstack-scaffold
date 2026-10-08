import { createError, defineEventHandler, getHeader } from 'h3';
import type { ApiResponse, User } from '@fs/types';
import { verifyToken } from '../../utils/jwt';

const MOCK_USER_BY_USERNAME: Record<string, User> = {
  admin: {
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
};

/**
 * GET /api/user/info
 * 从 Authorization 头解析 token，返回当前用户信息。
 */
export default defineEventHandler(async (event) => {
  const auth = getHeader(event, 'authorization') ?? '';
  const token = auth.startsWith('Bearer ') ? auth.slice(7) : '';
  if (!token) {
    throw createError({ statusCode: 401, statusMessage: '未登录' });
  }
  try {
    const payload = await verifyToken(token);
    const user = MOCK_USER_BY_USERNAME[payload.username];
    if (!user) {
      return { code: 401, message: '用户不存在', data: null } satisfies ApiResponse<null>;
    }
    return { code: 0, message: 'ok', data: user } satisfies ApiResponse<User>;
  } catch {
    return { code: 401, message: 'token 无效或已过期', data: null } satisfies ApiResponse<null>;
  }
});
