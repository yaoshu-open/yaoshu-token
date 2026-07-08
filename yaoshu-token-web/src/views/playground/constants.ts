/**
 * Playground 模块常量。
 */
import type { ComposerTranslation } from 'vue-i18n'
import type { PlaygroundConfig, ParameterEnabled, Message } from '@/api/playground/types'

export const MESSAGE_ROLES = {
  USER: 'user',
  ASSISTANT: 'assistant',
  SYSTEM: 'system'
} as const

export const MESSAGE_STATUS = {
  LOADING: 'loading',
  STREAMING: 'streaming',
  COMPLETE: 'complete',
  ERROR: 'error'
} as const

// 默认分组：使用 'default' 作为安全回退
export const DEFAULT_GROUP = 'default' as const

// 默认配置（持久化初始值）
export const DEFAULT_CONFIG: PlaygroundConfig = {
  model: 'gpt-4o',
  group: DEFAULT_GROUP,
  temperature: 0.7,
  top_p: 1,
  max_tokens: 4096,
  frequency_penalty: 0,
  presence_penalty: 0,
  seed: null,
  stream: true,
  systemPrompt: ''
}

// 默认参数启用开关
export const DEFAULT_PARAMETER_ENABLED: ParameterEnabled = {
  temperature: false,
  top_p: false,
  max_tokens: false,
  frequency_penalty: false,
  presence_penalty: false,
  seed: false
}

// localStorage 键
export const STORAGE_KEYS = {
  CONFIG: 'playground_config',
  MESSAGES: 'playground_messages',
  PARAMETER_ENABLED: 'playground_parameter_enabled'
} as const

// max_tokens 上限（流式 OOM 防护）
export const MAX_TOKENS_LIMIT = 32768

// 错误信息 i18n key 映射（消费方用 i18n.global.t() 翻译）
export const ERROR_MESSAGES = {
  API_REQUEST_ERROR: 'playground.errors.apiRequestError',
  NETWORK_ERROR: 'playground.errors.networkError',
  PARSE_ERROR: 'playground.errors.parseError',
  STREAM_START_ERROR: 'playground.errors.streamStartError',
  CONNECTION_CLOSED: 'playground.errors.connectionClosed',
  INTERRUPTED: 'playground.errors.interrupted',
  JSON_INVALID: 'playground.errors.jsonInvalid',
  CONFIG_INVALID: 'playground.errors.configInvalid',
  EXPORT_FAILED: 'playground.errors.exportFailed',
  IMPORT_FAILED: 'playground.errors.importFailed'
} as const

// 建议列表（S8: 改为函数形式以支持 i18n）
export interface SuggestionItem {
  key: string
  text: string
  color: string
}

export function createSuggestionList(t: ComposerTranslation): SuggestionItem[] {
  return [
    { key: 'analyze', text: t('playground.suggestions.analyze'), color: '#76d0eb' },
    { key: 'surprise', text: t('playground.suggestions.surprise'), color: '#76d0eb' },
    { key: 'summarize', text: t('playground.suggestions.summarize'), color: '#ea8444' },
    { key: 'code', text: t('playground.suggestions.code'), color: '#6c71ff' },
    { key: 'advice', text: t('playground.suggestions.advice'), color: '#76d0eb' }
  ]
}

// 类型 re-export（方便 composables 引用）
export type { Message, PlaygroundConfig, ParameterEnabled }
