/**
 * 用户管理常量。
 */

// ============================================================================
// Mock 开关
// ============================================================================

const DEV = import.meta.env.DEV
const VITE_USER_MOCK = import.meta.env.VITE_USER_MOCK === 'true'
export const USE_MOCK = DEV && VITE_USER_MOCK

// ============================================================================
// API 端点
// ============================================================================

export const USER_ENDPOINTS = {
  LIST: '/api/user/',
  SEARCH: '/api/user/search',
  DETAIL: '/api/user',
  CREATE: '/api/user/',
  UPDATE: '/api/user/',
  DELETE: '/api/user',
  MANAGE: '/api/user/manage',
  /** 当前用户可用模型列表（Dashboard SetupGuide curl 预览消费） */
  USER_MODELS: '/api/user/models',
} as const

// ============================================================================
// 分页
// ============================================================================

export const DEFAULT_PAGE_SIZE = 20

// ============================================================================
// 用户状态
// ============================================================================

export const USER_STATUS = {
  ENABLED: 1,
  DISABLED: 2,
  DELETED: 3,
} as const

export const USER_STATUS_CONFIG: Record<number, { label: string; type: 'success' | 'info' | 'danger' }> = {
  1: { label: 'Enabled', type: 'success' },
  2: { label: 'Disabled', type: 'info' },
  3: { label: 'Deleted', type: 'danger' },
}

export const USER_STATUS_OPTIONS = [
  { label: 'user.status.all', value: 'all' },
  { label: 'user.status.enabled', value: '1' },
  { label: 'user.status.disabled', value: '2' },
] as const

// ============================================================================
// 用户角色
// ============================================================================

export const USER_ROLES = {
  COMMON: 1,
  ADMIN: 2,
  ROOT: 3,
} as const

export const USER_ROLE_CONFIG: Record<number, { label: string; type: 'info' | 'warning' | 'danger' }> = {
  1: { label: 'user.role.common', type: 'info' },
  2: { label: 'user.role.admin', type: 'warning' },
  3: { label: 'common.root', type: 'danger' },
}

export const USER_ROLE_OPTIONS = [
  { label: 'user.role.all', value: 'all' },
  { label: 'user.role.common', value: '1' },
  { label: 'user.role.admin', value: '2' },
  { label: 'common.root', value: '3' },
] as const
