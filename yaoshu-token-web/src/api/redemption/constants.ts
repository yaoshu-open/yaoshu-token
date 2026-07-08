/**
 * 兑换码管理常量。
 * 后端契约：ai-docs/后端设计/API_Contract/契约_数据统计与日志.md §五。
 */

export const REDEMPTION_ENDPOINTS = {
  LIST: '/api/redemption/',
  SEARCH: '/api/redemption/search',
  DETAIL: (id: number) => `/api/redemption/${id}`,
  CREATE: '/api/redemption/',
  UPDATE: '/api/redemption/',
  DELETE_INVALID: '/api/redemption/invalid',
  DELETE: (id: number) => `/api/redemption/${id}`,
} as const

/** 默认每页条数 */
export const DEFAULT_PAGE_SIZE = 20

/** 兑换码状态 */
export const REDEMPTION_STATUS = {
  UNUSED: 1,
  USED: 2,
} as const

export const REDEMPTION_STATUS_CONFIG: Record<
  number,
  { i18nKey: string; variant: 'success' | 'info' }
> = {
  1: { i18nKey: 'redemption.status.unused', variant: 'success' },
  2: { i18nKey: 'redemption.status.used', variant: 'info' },
}

export const REDEMPTION_STATUS_OPTIONS = [
  { label: 'redemption.status.all', value: 'all' },
  { label: 'redemption.status.unused', value: '1' },
  { label: 'redemption.status.used', value: '2' },
] as const

/** 校验边界 */
export const REDEMPTION_NAME_MAX_LENGTH = 20
export const REDEMPTION_COUNT_MIN = 1
export const REDEMPTION_COUNT_MAX = 100
