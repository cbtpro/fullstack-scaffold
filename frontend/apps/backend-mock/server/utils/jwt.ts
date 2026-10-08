/**
 * Mock 端 JWT 工具。用 jose 签发/校验 HS256，与后端 auth-starter 的 jjwt 约定一致：
 * Authorization 头为 `Bearer <token>`，payload 内嵌 sub/username/roles。
 */
import { SignJWT, jwtVerify, type JWTPayload } from 'jose';

/** 默认 HS256 密钥，生产环境务必替换。 */
const SECRET = 'fullstack-scaffold-mock-secret-please-change-in-production';
const secretKey = new TextEncoder().encode(SECRET);
const ISSUER = 'fullstack-scaffold-mock';
const AUDIENCE = 'fullstack-scaffold-web';
const EXPIRES_IN = '2h';

export interface MockJwtPayload extends JWTPayload {
  sub: string;
  username: string;
  roles?: string[];
}

/** 签发 token。 */
export async function signToken(payload: Omit<MockJwtPayload, 'iss' | 'aud' | 'iat' | 'exp'>): Promise<string> {
  return new SignJWT({ ...payload })
    .setProtectedHeader({ alg: 'HS256', typ: 'JWT' })
    .setIssuedAt()
    .setIssuer(ISSUER)
    .setAudience(AUDIENCE)
    .setExpirationTime(EXPIRES_IN)
    .sign(secretKey);
}

/** 校验 token，失败抛错。 */
export async function verifyToken(token: string): Promise<MockJwtPayload> {
  const { payload } = await jwtVerify(token, secretKey, {
    issuer: ISSUER,
    audience: AUDIENCE,
  });
  return payload as MockJwtPayload;
}
