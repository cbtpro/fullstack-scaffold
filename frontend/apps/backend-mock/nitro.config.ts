import { defineNitroConfig } from 'nitropack';

/**
 * Nitro 配置。CORS 直接用 routeRules 开启，避免额外中间件。
 */
export default defineNitroConfig({
  compatibilityDate: '2026-10-08',
  srcDir: 'server',
  runtimeConfig: {
    jwtSecret: 'fullstack-scaffold-mock-secret-please-change-in-production',
    jwtExpiresIn: '2h',
  },
  routeRules: {
    '/api/**': {
      cors: true,
    },
  },
});
