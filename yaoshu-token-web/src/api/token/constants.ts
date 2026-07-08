/**
 * 令牌管理 API 端点常量 + Mock 开关 + 业务常量。
 */

// ============================================================================
// Mock 开关
// ============================================================================

const DEV = import.meta.env.DEV
const VITE_TOKEN_MOCK = import.meta.env.VITE_TOKEN_MOCK === 'true'
export const USE_MOCK = DEV && VITE_TOKEN_MOCK

// ============================================================================
// API 端点
// ============================================================================

export const TOKEN_ENDPOINTS = {
  LIST: '/api/token/',
  SEARCH: '/api/token/search',
  DETAIL: '/api/token',
  CREATE: '/api/token/',
  UPDATE: '/api/token/',
  DELETE: '/api/token',
  BATCH_DELETE: '/api/token/batch',
} as const

// ============================================================================
// 分页
// ============================================================================

export const DEFAULT_PAGE_SIZE = 20

// ============================================================================
// 令牌状态
// ============================================================================

export const TOKEN_STATUS = {
  ENABLED: 1,
  DISABLED: 2,
  EXPIRED: 3,
  EXHAUSTED: 4,
} as const

export const TOKEN_STATUS_CONFIG: Record<
  number,
  { labelKey: string; type: 'success' | 'info' | 'warning' | 'danger' }
> = {
  1: { labelKey: 'token.status.enabled', type: 'success' },
  2: { labelKey: 'token.status.disabled', type: 'info' },
  3: { labelKey: 'token.status.expired', type: 'warning' },
  4: { labelKey: 'token.status.exhausted', type: 'danger' },
}

export const TOKEN_STATUS_OPTIONS = [
  { label: 'token.status.all', value: 'all' },
  { label: 'token.status.enabled', value: 'enabled' },
  { label: 'token.status.disabled', value: 'disabled' },
  { label: 'token.status.expired', value: 'expired' },
  { label: 'token.status.exhausted', value: 'exhausted' },
] as const

// ============================================================================
// 批量复制格式（T-TK-02）
// ============================================================================

export const COPY_FORMAT_OPTIONS = [
  { label: 'Name + Key', value: 'name_key' },
  { label: 'Key Only', value: 'key_only' },
] as const
