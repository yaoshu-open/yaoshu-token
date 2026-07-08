import type { RankingPeriod } from './types'

/** 后端端点（契约_公共与系统 §二） */
export const ENDPOINTS = {
  RANKINGS: '/api/rankings'
} as const

/** Mock 闭环开关：后端未就绪时 DEV 环境启用 */
export const USE_MOCK = import.meta.env.VITE_RANKINGS_MOCK === 'true'

/** 合法的 period 值 */
export const VALID_PERIODS: RankingPeriod[] = [
  'today',
  'week',
  'month',
  'year',
  'all'
]

/** period 默认值（URL 非法值 fallback） */
export const DEFAULT_PERIOD: RankingPeriod = 'week'

/** period 国际化 key 映射 */
export const PERIOD_LABEL_KEYS: Record<RankingPeriod, string> = {
  today: 'rankings.period.today',
  week: 'rankings.period.week',
  month: 'rankings.period.month',
  year: 'rankings.period.year',
  all: 'rankings.period.allTime'
}

/** period 描述国际化 key（ModelsSection 副标题：模型维度用量） */
export const PERIOD_DESCRIPTION_KEYS: Record<RankingPeriod, string> = {
  today: 'rankings.models.desc.today',
  week: 'rankings.models.desc.week',
  month: 'rankings.models.desc.month',
  year: 'rankings.models.desc.year',
  all: 'rankings.models.desc.allTime'
}

/** period 描述国际化 key（MarketShareSection 副标题：供应商维度份额） */
export const MARKET_PERIOD_DESCRIPTION_KEYS: Record<RankingPeriod, string> = {
  today: 'rankings.market.desc.today',
  week: 'rankings.market.desc.week',
  month: 'rankings.market.desc.month',
  year: 'rankings.market.desc.year',
  all: 'rankings.market.desc.allTime'
}

/**
 * 供应商固定调色板——市场份额图与图例圆点共用。
 * 未知供应商回退到 FALLBACK_PALETTE 循环取色。
 */
export const VENDOR_COLOURS: Record<string, string> = {
  OpenAI: '#10a37f',
  Anthropic: '#d97757',
  Google: '#4285f4',
  DeepSeek: '#7c5cff',
  Alibaba: '#ff9900',
  xAI: '#1f2937',
  Meta: '#1877f2',
  Moonshot: '#ec4899',
  Zhipu: '#06b6d4',
  Mistral: '#ff7000',
  ByteDance: '#3b82f6',
  Tencent: '#22c55e',
  MiniMax: '#a855f7',
  Cohere: '#fb923c',
  Baidu: '#ef4444',
  Others: '#94a3b8'
}

export const FALLBACK_PALETTE = [
  '#0ea5e9',
  '#22c55e',
  '#a855f7',
  '#f97316',
  '#14b8a6',
  '#eab308',
  '#ec4899',
  '#84cc16',
  '#6366f1',
  '#10b981',
  '#f43f5e',
  '#0891b2',
  '#94a3b8'
]

/** 供应商列表中最多展示的条目数 */
export const MAX_VENDORS_IN_LIST = 12

/** tooltip 单维度内最多展示的条目数（超出折叠为 "+N more"） */
export const TOOLTIP_MAX_ROWS = 10
