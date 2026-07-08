import { defineConfig } from 'eslint/config'
import tseslint from 'typescript-eslint'
import pluginVue from 'eslint-plugin-vue'
import globals from 'globals'

// ESLint 9 flat config：Vue3 + TypeScript（typescript-eslint v8 flat 原生 + eslint-plugin-vue 10 flat/recommended）
export default defineConfig([
  {
    ignores: [
      'dist',
      'node_modules',
      'src/types/**/*.d.ts',
      '*.cjs',
      '*.json',
      'eslint.config.js'
    ]
  },
  // TypeScript 推荐规则（含 TS parser）
  ...tseslint.configs.recommended,
  // Vue 3 推荐规则（含 vue-eslint-parser）
  ...pluginVue.configs['flat/recommended'],
  // Vue SFC 内 <script lang="ts"> 用 TS parser 解析
  {
    files: ['**/*.vue'],
    languageOptions: {
      parserOptions: {
        parser: tseslint.parser
      }
    }
  },
  {
    languageOptions: {
      globals: {
        ...globals.browser,
        ...globals.node
      }
    },
    rules: {
      'vue/multi-word-component-names': 'off',
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_' }]
    }
  }
])
