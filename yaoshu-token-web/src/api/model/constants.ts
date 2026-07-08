/**
 * 模型管理 API 端点常量 + Mock 开关 + 全量业务常量。
 * 后端契约：ai-docs/后端设计/API_Contract/契约_模型与部署.md。
 */

// ============================================================================
// Mock 开关
// ============================================================================

const DEV = import.meta.env.DEV
const VITE_MODEL_MOCK = import.meta.env.VITE_MODEL_MOCK === 'true'
export const USE_MOCK = DEV && VITE_MODEL_MOCK

// ============================================================================
// API 端点
// ============================================================================

export const MODEL_ENDPOINTS = {
  /** 模型列表 */
  LIST: '/api/models/',
  /** 搜索模型 */
  SEARCH: '/api/models/search',
  /** 模型详情 */
  DETAIL: '/api/models',
  /** 创建模型 */
  CREATE: '/api/models/',
  /** 更新模型 */
  UPDATE: '/api/models/',
  /** 删除模型 */
  DELETE: '/api/models',
  /** 缺失模型扫描 */
  MISSING: '/api/models/missing',
  /** 上游同步预览 */
  SYNC_PREVIEW: '/api/models/sync_upstream/preview',
  /** 正式同步上游 */
  SYNC: '/api/models/sync_upstream',
} as const

export const VENDOR_ENDPOINTS = {
  /** 供应商列表 */
  LIST: '/api/vendors/',
  /** 搜索供应商 */
  SEARCH: '/api/vendors/search',
  /** 供应商详情 */
  DETAIL: '/api/vendors',
  /** 创建供应商 */
  CREATE: '/api/vendors/',
  /** 更新供应商 */
  UPDATE: '/api/vendors/',
  /** 删除供应商 */
  DELETE: '/api/vendors',
} as const

export const PREFILL_GROUP_ENDPOINTS = {
  /** 预填组列表 */
  LIST: '/api/prefill_group',
  /** 创建预填组 */
  CREATE: '/api/prefill_group',
  /** 更新预填组 */
  UPDATE: '/api/prefill_group',
  /** 删除预填组 */
  DELETE: '/api/prefill_group',
} as const

// ============================================================================
// 分页
// ============================================================================

export const DEFAULT_PAGE_SIZE = 20

// ============================================================================
// 名称匹配规则
// ============================================================================

export const NAME_RULE_OPTIONS = [
  { label: 'Exact Match', value: 0 },
  { label: 'Prefix Match', value: 1 },
  { label: 'Contains Match', value: 2 },
  { label: 'Suffix Match', value: 3 },
] as const

export const NAME_RULE_CONFIG: Record<number, { label: string; color: string; description: string }> = {
  0: { label: 'Exact', color: 'success', description: 'Match model name exactly' },
  1: { label: 'Prefix', color: 'primary', description: 'Match models starting with this name' },
  2: { label: 'Contains', color: 'warning', description: 'Match models containing this name' },
  3: { label: 'Suffix', color: 'info', description: 'Match models ending with this name' },
}

// ============================================================================
// 模型状态
// ============================================================================

export const MODEL_STATUS_OPTIONS = [
  { label: 'model.status.all', value: 'all' },
  { label: 'model.status.enabled', value: 'enabled' },
  { label: 'model.status.disabled', value: 'disabled' },
] as const

export const MODEL_STATUS_CONFIG: Record<number, { label: string; type: 'success' | 'info' }> = {
  1: { label: 'model.status.enabled', type: 'success' },
  0: { label: 'model.status.disabled', type: 'info' },
}

// ============================================================================
// 同步状态
// ============================================================================

export const SYNC_STATUS_OPTIONS = [
  { label: 'model.syncStatus.all', value: 'all' },
  { label: 'model.syncStatus.official', value: 'yes' },
  { label: 'model.syncStatus.noSync', value: 'no' },
] as const

// ============================================================================
// 配额类型
// ============================================================================

export const QUOTA_TYPE_CONFIG: Record<number, { label: string; type: string }> = {
  0: { label: 'Usage-based', type: 'primary' },
  1: { label: 'Per-call', type: 'success' },
}

// ============================================================================
// 端点模板
// ============================================================================

export const ENDPOINT_TEMPLATES: Record<string, { path: string; method: string }> = {
  openai: { path: '/v1/chat/completions', method: 'POST' },
  'openai-response': { path: '/v1/responses', method: 'POST' },
  anthropic: { path: '/v1/messages', method: 'POST' },
  gemini: { path: '/v1beta/models/{model}:generateContent', method: 'POST' },
  'jina-rerank': { path: '/rerank', method: 'POST' },
  'image-generation': { path: '/v1/images/generations', method: 'POST' },
  embeddings: { path: '/v1/embeddings', method: 'POST' },
}

// ============================================================================
// 同步选项
// ============================================================================

export const SYNC_LOCALE_OPTIONS = [
  { label: 'Chinese', value: 'zh' as const },
  { label: 'English', value: 'en' as const },
  { label: 'Japanese', value: 'ja' as const },
]

export const SYNC_SOURCE_OPTIONS = [
  { label: 'Official Repository', value: 'official' as const },
  { label: 'Configuration File', value: 'config' as const },
]
