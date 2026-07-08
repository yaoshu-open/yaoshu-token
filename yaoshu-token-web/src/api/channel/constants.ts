/**
 * 渠道 API 端点常量 + Mock 开关 + 全量业务常量。
 * 后端契约：ai-docs/后端设计/API_Contract/契约_渠道管理.md。
 */
import type { UaOverrideMode } from './types'

// ============================================================================
// API 端点
// ============================================================================

export const CHANNEL_ENDPOINTS = {
  /** 渠道列表（分页） */
  LIST: '/api/channel/',
  /** 搜索渠道 */
  SEARCH: '/api/channel/search',
  /** 单个渠道详情 */
  DETAIL: '/api/channel',
  /** 创建渠道 */
  CREATE: '/api/channel/',
  /** 更新渠道 */
  UPDATE: '/api/channel/',
  /** 批量删除 */
  BATCH_DELETE: '/api/channel/batch',
  /** 批量设置标签 */
  BATCH_TAG: '/api/channel/batch/tag',
  /** 测试渠道 */
  TEST: '/api/channel/test',
  /** 更新余额 */
  UPDATE_BALANCE: '/api/channel/update_balance',
  /** 获取上游模型 */
  FETCH_MODELS: '/api/channel/fetch_models',
  /** 复制渠道 */
  COPY: '/api/channel/copy',
  /** 修复能力 */
  FIX: '/api/channel/fix',
  /** 删除禁用渠道 */
  DELETE_DISABLED: '/api/channel/disabled',
  /** 获取渠道密钥（需2FA） */
  KEY: '/api/channel',
  /** 测试所有渠道 */
  TEST_ALL: '/api/channel/test',
  /** 更新所有渠道余额 */
  UPDATE_BALANCE_ALL: '/api/channel/update_balance',
  /** 所有可用模型 */
  MODELS: '/api/channel/models',
  /** 所有启用模型 */
  MODELS_ENABLED: '/api/channel/models_enabled',
  /** 多密钥管理 */
  MULTI_KEY: '/api/channel/multi_key/manage',
  /** 编辑标签（覆盖操作） */
  EDIT_TAG: '/api/channel/tag',
  /** 启用标签下所有渠道 */
  TAG_ENABLED: '/api/channel/tag/enabled',
  /** 禁用标签下所有渠道 */
  TAG_DISABLED: '/api/channel/tag/disabled',
  /** 标签当前模型列表（query: tag） */
  TAG_MODELS: '/api/channel/tag/models',
  /** Ollama 删除模型 */
  OLLAMA_DELETE: '/api/channel/ollama/delete',
  /** Ollama 版本 */
  OLLAMA_VERSION: '/api/channel/ollama/version',
  /** Codex OAuth 启动 */
  CODEX_OAUTH_START: '/api/channel/codex/oauth/start',
  /** Codex OAuth 完成 */
  CODEX_OAUTH_COMPLETE: '/api/channel/codex/oauth/complete',
  /** Codex 刷新凭证 */
  CODEX_REFRESH: '/api/channel/codex/refresh',
  /** Codex 用量 */
  CODEX_USAGE: '/api/channel/codex/usage',
  /** 预填组 */
  PREFILL_GROUP: '/api/prefill_group',
  /** 用户分组列表 */
  GROUPS: '/api/group/',
  /** 上游更新 - 检测单渠道 */
  UPSTREAM_UPDATE_DETECT: '/api/channel/upstream_updates/detect',
  /** 上游更新 - 批量检测 */
  UPSTREAM_UPDATE_DETECT_ALL: '/api/channel/upstream_updates/detect_all',
  /** 上游更新 - 应用单渠道 */
  UPSTREAM_UPDATE_APPLY: '/api/channel/upstream_updates/apply',
  /** 上游更新 - 批量应用 */
  UPSTREAM_UPDATE_APPLY_ALL: '/api/channel/upstream_updates/apply_all'
} as const

/** Mock 开关（DEV 环境 + env flag 启用） */
export const USE_MOCK =
  import.meta.env.DEV && import.meta.env.VITE_CHANNEL_MOCK === 'true'

// ============================================================================
// 渠道类型（57 种，label 为 i18n key）
// ============================================================================

export const CHANNEL_TYPES = {
  0: 'Unknown',
  1: 'OpenAI',
  2: 'Midjourney',
  3: 'Azure',
  4: 'Ollama',
  5: 'MidjourneyPlus',
  6: 'OpenAIMax',
  7: 'OhMyGPT',
  8: 'Custom',
  9: 'AILS',
  10: 'AI Proxy',
  11: 'PaLM',
  12: 'API2GPT',
  13: 'AIGC2D',
  14: 'Anthropic',
  15: 'Baidu',
  16: 'Zhipu',
  17: 'Ali',
  18: 'Xunfei',
  19: '360',
  20: 'OpenRouter',
  21: 'AI Proxy Library',
  22: 'FastGPT',
  23: 'Tencent',
  24: 'Gemini',
  25: 'Moonshot',
  26: 'Zhipu V4',
  27: 'Perplexity',
  31: 'LingYiWanWu',
  33: 'AWS',
  34: 'Cohere',
  35: 'MiniMax',
  36: 'SunoAPI',
  37: 'Dify',
  38: 'Jina',
  39: 'Cloudflare',
  40: 'SiliconFlow',
  41: 'Vertex AI',
  42: 'Mistral',
  43: 'DeepSeek',
  44: 'MokaAI',
  45: 'VolcEngine',
  46: 'Baidu V2',
  47: 'Xinference',
  48: 'xAI',
  49: 'Coze',
  50: 'Kling',
  51: 'Jimeng',
  52: 'Vidu',
  53: 'Submodel',
  54: 'DoubaoVideo',
  55: 'Sora',
  56: 'Replicate',
  57: 'Codex'
} as const

const CHANNEL_TYPE_DISPLAY_ORDER: number[] = [
  1, 14, 33, 24, 43, 3, 41, 48, 42, 34, 20, 4, 40, 27, 25, 17, 26, 15, 46, 23,
  18, 45, 31, 35, 49, 19, 47, 37, 38, 39, 11, 8, 57, 22, 21, 44, 2, 5, 36, 50,
  51, 52, 53, 54, 55, 56
]

export const CHANNEL_TYPE_OPTIONS: { value: number; label: string }[] = (() => {
  const ordered: { value: number; label: string }[] = []
  const seen = new Set<number>()
  for (const id of CHANNEL_TYPE_DISPLAY_ORDER) {
    const label = CHANNEL_TYPES[id as keyof typeof CHANNEL_TYPES]
    if (label) {
      ordered.push({ value: id, label })
      seen.add(id)
    }
  }
  for (const [key, label] of Object.entries(CHANNEL_TYPES)) {
    const id = Number(key)
    if (id !== 0 && !seen.has(id)) {
      ordered.push({ value: id, label })
    }
  }
  return ordered
})()

// ============================================================================
// 渠道状态
// ============================================================================

export const CHANNEL_STATUS = {
  UNKNOWN: 0,
  ENABLED: 1,
  MANUAL_DISABLED: 2,
  AUTO_DISABLED: 3
} as const

export const CHANNEL_STATUS_CONFIG = {
  [CHANNEL_STATUS.UNKNOWN]: {
    variant: 'neutral' as const,
    label: 'channel.status.unknown'
  },
  [CHANNEL_STATUS.ENABLED]: {
    variant: 'success' as const,
    label: 'channel.status.enabled'
  },
  [CHANNEL_STATUS.MANUAL_DISABLED]: {
    variant: 'danger' as const,
    label: 'channel.status.disabled'
  },
  [CHANNEL_STATUS.AUTO_DISABLED]: {
    variant: 'warning' as const,
    label: 'channel.status.autoDisabled'
  }
}

export const CHANNEL_STATUS_OPTIONS = [
  { value: 'all', label: 'channel.statusFilter.all' },
  { value: 'enabled', label: 'channel.statusFilter.enabled' },
  { value: 'disabled', label: 'channel.statusFilter.disabled' }
] as const

// ============================================================================
// 多密钥
// ============================================================================

export const MULTI_KEY_STATUS = {
  ENABLED: 1,
  MANUAL_DISABLED: 2,
  AUTO_DISABLED: 3
} as const

export const MULTI_KEY_STATUS_CONFIG = {
  [MULTI_KEY_STATUS.ENABLED]: {
    variant: 'success' as const,
    label: 'channel.multiKey.statusEnabled'
  },
  [MULTI_KEY_STATUS.MANUAL_DISABLED]: {
    variant: 'neutral' as const,
    label: 'channel.multiKey.statusManualDisabled'
  },
  [MULTI_KEY_STATUS.AUTO_DISABLED]: {
    variant: 'danger' as const,
    label: 'channel.multiKey.statusAutoDisabled'
  }
}

export const MULTI_KEY_MODES = [
  { value: 'random', label: 'channel.multiKey.modeRandom' },
  { value: 'polling', label: 'channel.multiKey.modePolling' }
] as const

/** 多密钥状态筛选选项 */
export const MULTI_KEY_FILTER_OPTIONS = [
  { value: 'all', label: 'channel.multiKey.filterAll' },
  { value: '1', label: 'channel.multiKey.statusEnabled' },
  { value: '2', label: 'channel.multiKey.statusManualDisabled' },
  { value: '3', label: 'channel.multiKey.statusAutoDisabled' }
] as const

/** 多密钥操作确认提示 i18n key */
export const MULTI_KEY_CONFIRM_MESSAGES = {
  DELETE: 'channel.multiKey.confirmDelete',
  ENABLE: 'channel.multiKey.confirmEnable',
  DISABLE: 'channel.multiKey.confirmDisable',
  ENABLE_ALL: 'channel.multiKey.confirmEnableAll',
  DISABLE_ALL: 'channel.multiKey.confirmDisableAll',
  DELETE_DISABLED: 'channel.multiKey.confirmDeleteDisabled'
} as const

export const ADD_MODE_OPTIONS = [
  { value: 'single', label: 'channel.addMode.single' },
  { value: 'batch', label: 'channel.addMode.batch' },
  { value: 'multi_to_single', label: 'channel.addMode.multiToSingle' }
] as const

// ============================================================================
// 排序选项
// ============================================================================

export const SORT_OPTIONS = [
  { value: 'priority', label: 'channel.sort.priority' },
  { value: 'id', label: 'channel.sort.id' },
  { value: 'name', label: 'channel.sort.name' },
  { value: 'balance', label: 'channel.sort.balance' },
  { value: 'response_time', label: 'channel.sort.responseTime' }
] as const

// ============================================================================
// UA 覆盖模式（渠道级行为配置，中性通用字段）
// ============================================================================

export const UA_OVERRIDE_MODE_OPTIONS: { value: UaOverrideMode; label: string }[] = [
  { value: 'AUTO', label: 'channel.edit.advanced.uaOverrideAuto' },
  { value: 'FORCE_IDE', label: 'channel.edit.advanced.uaOverrideForceIde' },
  { value: 'OFF', label: 'channel.edit.advanced.uaOverrideOff' }
]

// ============================================================================
// 余额/响应时间阈值
// ============================================================================

export const BALANCE_THRESHOLDS = {
  LOW: 1,
  MEDIUM: 10,
  HIGH: 100
} as const

export const RESPONSE_TIME_THRESHOLDS = {
  EXCELLENT: 500,
  GOOD: 1000,
  FAIR: 2000,
  POOR: 5000
} as const

export const RESPONSE_TIME_CONFIG = {
  EXCELLENT: { variant: 'success' as const, label: 'channel.responseTime.excellent' },
  GOOD: { variant: 'info' as const, label: 'channel.responseTime.good' },
  FAIR: { variant: 'warning' as const, label: 'channel.responseTime.fair' },
  POOR: { variant: 'danger' as const, label: 'channel.responseTime.poor' },
  UNKNOWN: { variant: 'neutral' as const, label: 'channel.responseTime.unknown' }
} as const

// ============================================================================
// 默认值
// ============================================================================

export const DEFAULT_PAGE_SIZE = 20

export const CHANNELS_TABLE_PAGE_SIZE_OPTIONS = [10, 20, 50, 100]

export const DEFAULT_CHANNEL_VALUES = {
  name: '',
  type: 0,
  baseUrl: '',
  key: '',
  models: '',
  group: 'default',
  status: CHANNEL_STATUS.ENABLED,
  priority: 0,
  weight: 0,
  autoBan: 1,
  remark: ''
} as const

// ============================================================================
// 渠道类型特殊配置
// ============================================================================

/** 可获取上游模型的渠道类型 */
export const MODEL_FETCHABLE_TYPES = new Set([
  1, 4, 14, 17, 20, 23, 24, 25, 26, 27, 31, 34, 35, 40, 42, 43, 47, 48
])

/** 类型对应的密钥格式提示 */
export const TYPE_TO_KEY_PROMPT: Record<number, string> = {
  15: 'channel.keyPrompt.baidu',
  18: 'channel.keyPrompt.xunfei',
  22: 'channel.keyPrompt.fastgpt',
  23: 'channel.keyPrompt.tencent',
  33: 'channel.keyPrompt.aws',
  50: 'channel.keyPrompt.kling',
  51: 'channel.keyPrompt.jimeng',
  57: 'channel.keyPrompt.codex'
}

/** 渠道类型警告 */
export const CHANNEL_TYPE_WARNINGS: Record<number, string> = {
  3: 'channel.typeWarning.azure',
  8: 'channel.typeWarning.custom',
  37: 'channel.typeWarning.dify'
}
