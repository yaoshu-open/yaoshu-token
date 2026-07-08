import { createI18n } from 'vue-i18n'
import zhCN from '@/locales/zh-CN.json'
import en from '@/locales/en.json'

// 8 语言注册清单（M0 仅加载 zh-CN/en 最小 key，M1 从 default 迁移完整翻译资源）
export const SUPPORTED_LOCALES = [
  'en',
  'fr',
  'ja',
  'ru',
  'vi',
  'zh',
  'zh-CN',
  'zh-TW'
] as const

export type LocaleKey = (typeof SUPPORTED_LOCALES)[number]

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  fallbackLocale: 'en',
  messages: {
    'zh-CN': zhCN,
    en
  }
})

export default i18n
