/**
 * 前端双端共用的 ESLint 基础配置（扁平配置）。
 * React 端与 Vue 端各自在此之上叠加框架插件，保证两端规则口径一致。
 * Prettier 负责格式、ESLint 负责代码质量，因此以 eslint-config-prettier 收尾关闭冲突规则。
 *
 * 独立成包而非放在仓库根：pnpm 不做幽灵依赖提升，配置随包解析依赖，
 * 各端只需声明自己的框架插件，避免根目录与子包之间的依赖错位。
 */
import js from '@eslint/js';
import tseslint from 'typescript-eslint';
import prettier from 'eslint-config-prettier';
import globals from 'globals';

export const baseConfig = tseslint.config(
  {
    // ._* 是 macOS 在外置卷上生成的 AppleDouble 元数据，非源码
    ignores: ['dist/**', 'node_modules/**', '.turbo/**', '*.min.js', '**/._*'],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      globals: { ...globals.browser, ...globals.es2021 },
    },
    rules: {
      // 有意忽略的入参/变量以 _ 前缀声明，避免为绕过校验而删除参数
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrorsIgnorePattern: '^_' },
      ],
    },
  },
  prettier,
);
