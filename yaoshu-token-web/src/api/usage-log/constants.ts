/** 调用日志 API 端点常量 + 日志类型枚举 + 状态映射。 */

const DEV = import.meta.env.DEV
const VITE_LOG_MOCK = import.meta.env.VITE_LOG_MOCK === 'true'
export const USE_MOCK = DEV && VITE_LOG_MOCK

export const LOG_ENDPOINTS = {
  ADMIN_LIST: '/api/log/',
  USER_LIST: '/api/log/self',
  ADMIN_STAT: '/api/log/stat',
  USER_STAT: '/api/log/self/stat',
} as const

export const DEFAULT_PAGE_SIZE = 20

/** 日志类型枚举（后端 type 字段值） */
export const LOG_TYPE_ENUM = {
  UNKNOWN: 0,
  TOPUP: 1,
  CONSUME: 2,
  MANAGE: 3,
  SYSTEM: 4,
  ERROR: 5,
  REFUND: 6,
} as const

/** 日志类型映射（i18n key + StatusBadge variant） */
export const LOG_TYPE_MAPPINGS: Record<number, { labelKey: string; variant: string }> = {
  0: { labelKey: 'usageLogs.types.unknown', variant: 'info' },
  1: { labelKey: 'usageLogs.types.topup', variant: 'primary' },
  2: { labelKey: 'usageLogs.types.consume', variant: 'success' },
  3: { labelKey: 'usageLogs.types.manage', variant: 'warning' },
  4: { labelKey: 'usageLogs.types.system', variant: 'info' },
  5: { labelKey: 'usageLogs.types.error', variant: 'danger' },
  6: { labelKey: 'usageLogs.types.refund', variant: 'info' },
}

const FALLBACK_MAPPING = { labelKey: 'usageLogs.types.unknown', variant: 'info' }

export function getLogTypeMapping(type: number) {
  return LOG_TYPE_MAPPINGS[type] ?? FALLBACK_MAPPING
}
