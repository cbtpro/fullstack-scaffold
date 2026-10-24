/**
 * web-vue 的 ESLint 配置：在双端共用基础上叠加 Vue 专属规则。
 * .vue 单文件组件需由 vue-eslint-parser 解析，脚本块内的 TS 再交给 typescript-eslint 解析。
 *
 * 取 flat/essential（优先级 A，只含正确性规则）而非 flat/recommended：
 * 后者含缩进、换行、属性顺序等格式规则，会与 Prettier 争夺格式决定权。
 */
import pluginVue from 'eslint-plugin-vue';
import tseslint from 'typescript-eslint';
import { baseConfig } from '@fs/eslint-config';

export default [
  ...baseConfig,
  ...pluginVue.configs['flat/essential'],
  {
    files: ['**/*.vue'],
    languageOptions: {
      parserOptions: { parser: tseslint.parser },
    },
    rules: {
      // 页面组件惯例用多单词命名，路由级组件无需再包一层前缀
      'vue/multi-word-component-names': 'off',
    },
  },
];
