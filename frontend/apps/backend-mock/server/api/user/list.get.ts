import { createError, defineEventHandler, getHeader, getQuery } from 'h3';
import type { ApiResponse, PageResult, User } from '@fs/types';
import { verifyToken } from '../../utils/jwt';

const USERS: User[] = [
  {
    id: 1,
    username: 'admin',
    nickname: '管理员',
    email: 'admin@fullstack-scaffold.dev',
    roles: ['ADMIN'],
    permissions: ['*:*:*'],
    status: 1,
    createTime: '2026-01-01 00:00:00',
    updateTime: '2026-01-01 00:00:00',
  },
  {
    id: 2,
    username: 'amy',
    nickname: '艾米',
    email: 'amy@fullstack-scaffold.dev',
    roles: ['USER'],
    permissions: [],
    status: 1,
    createTime: '2026-01-02 10:00:00',
    updateTime: '2026-01-02 10:00:00',
  },
  {
    id: 3,
    username: 'bob',
    nickname: '鲍勃',
    email: 'bob@fullstack-scaffold.dev',
    roles: ['USER'],
    permissions: [],
    status: 0,
    createTime: '2026-01-03 11:30:00',
    updateTime: '2026-01-03 11:30:00',
  },
];

/**
 * GET /api/user/list
 * 校验 token 后返回静态分页用户列表。pageNum/pageSize 从 query 解析。
 */
export default defineEventHandler(async (event) => {
  const auth = getHeader(event, 'authorization') ?? '';
  const token = auth.startsWith('Bearer ') ? auth.slice(7) : '';
  if (!token) {
    throw createError({ statusCode: 401, statusMessage: '未登录' });
  }
  try {
    await verifyToken(token);
  } catch {
    return { code: 401, message: 'token 无效或已过期', data: null } satisfies ApiResponse<null>;
  }
  const query = getQuery(event);
  const pageNum = Math.max(1, Number(query.pageNum ?? 1));
  const pageSize = Math.max(1, Number(query.pageSize ?? 10));
  const start = (pageNum - 1) * pageSize;
  const list = USERS.slice(start, start + pageSize);
  const result: PageResult<User> = { list, total: USERS.length, pageNum, pageSize };
  return { code: 0, message: 'ok', data: result } satisfies ApiResponse<PageResult<User>>;
});
