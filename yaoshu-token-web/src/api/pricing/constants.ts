import type { TokenUnit } from './types'

/** 排序选项 */
export const SORT_OPTIONS = {
  NAME: 'name',
  PRICE_LOW: 'price-low',
  PRICE_HIGH: 'price-high'
} as const

export type SortOption = (typeof SORT_OPTIONS)[keyof typeof SORT_OPTIONS]

/** 过滤全选值 */
export const FILTER_ALL = 'all'

/** 配额类型选项 */
export const QUOTA_TYPES = {
  ALL: 'all',
  TOKEN: 'token',
  REQUEST: 'request'
} as const

export type QuotaTypeOption = (typeof QUOTA_TYPES)[keyof typeof QUOTA_TYPES]

/** 端点类型选项 */
export const ENDPOINT_TYPES = {
  ALL: 'all',
  OPENAI: 'openai',
  OPENAI_RESPONSE: 'openai-response',
  ANTHROPIC: 'anthropic',
  GEMINI: 'gemini',
  JINA_RERANK: 'jina-rerank',
  IMAGE_GENERATION: 'image-generation',
  EMBEDDINGS: 'embeddings',
  OPENAI_VIDEO: 'openai-video'
} as const

export type EndpointTypeOption =
  (typeof ENDPOINT_TYPES)[keyof typeof ENDPOINT_TYPES]

/** 过滤区段 key */
export const FILTER_SECTIONS = {
  PRICING_TYPE: 'pricingType',
  ENDPOINT_TYPE: 'endpointType',
  VENDOR: 'vendor',
  GROUP: 'group',
  TAG: 'tag'
} as const

/** 模型行最大标签显示数 */
export const MAX_TAGS_DISPLAY = 5

/** 过滤项最大显示数（超出显示"更多"） */
export const MAX_FILTER_ITEMS = 5

/** 排除的分组 */
export const EXCLUDED_GROUPS = ['', 'auto']

/** 配额类型值 */
export const QUOTA_TYPE_VALUES = {
  TOKEN: 0,
  REQUEST: 1
} as const

/** Token 单位除数（S5: 移除 K，仅保留 M） */
export const TOKEN_UNIT_DIVISORS = {
  M: 1
} as const

/** 默认 Token 单位 */
export const DEFAULT_TOKEN_UNIT: TokenUnit = 'M'

/** 视图模式 */
export const VIEW_MODES = {
  CARD: 'card',
  TABLE: 'table'
} as const

export type ViewMode = (typeof VIEW_MODES)[keyof typeof VIEW_MODES]

/** 表格默认每页条数 */
export const DEFAULT_PRICING_PAGE_SIZE = 20

/** Mock 开关：DEV + VITE_PRICING_MOCK=true 时启用 */
export const USE_MOCK =
  import.meta.env.DEV && import.meta.env.VITE_PRICING_MOCK === 'true'
