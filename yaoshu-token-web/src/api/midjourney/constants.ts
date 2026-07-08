/**
 * Midjourney 任务日志 API 端点常量 + Mock 开关 + 状态映射表。
 */

// ============================================================================
// Mock 开关
// ============================================================================

const DEV = import.meta.env.DEV
const VITE_MJ_MOCK = import.meta.env.VITE_MJ_MOCK === 'true'
export const USE_MOCK = DEV && VITE_MJ_MOCK

// ============================================================================
// API 端点
// ============================================================================

export const MJ_ENDPOINTS = {
  ADMIN_LIST: '/api/mj/',
  USER_LIST: '/api/mj/self',
} as const

// ============================================================================
// 分页
// ============================================================================

export const DEFAULT_PAGE_SIZE = 20

// ============================================================================
// 任务类型（17 种 + fallback）
// ============================================================================

export const MJ_TASK_TYPES = {
  IMAGINE: 'IMAGINE',
  UPSCALE: 'UPSCALE',
  VIDEO: 'VIDEO',
  EDITS: 'EDITS',
  VARIATION: 'VARIATION',
  HIGH_VARIATION: 'HIGH_VARIATION',
  LOW_VARIATION: 'LOW_VARIATION',
  PAN: 'PAN',
  DESCRIBE: 'DESCRIBE',
  BLEND: 'BLEND',
  UPLOAD: 'UPLOAD',
  SHORTEN: 'SHORTEN',
  REROLL: 'REROLL',
  INPAINT: 'INPAINT',
  SWAP_FACE: 'SWAP_FACE',
  ZOOM: 'ZOOM',
  CUSTOM_ZOOM: 'CUSTOM_ZOOM',
  MODAL: 'MODAL',
} as const

// ============================================================================
// 任务状态（6 种）
// ============================================================================

export const MJ_TASK_STATUS = {
  NOT_START: 'NOT_START',
  SUBMITTED: 'SUBMITTED',
  IN_PROGRESS: 'IN_PROGRESS',
  SUCCESS: 'SUCCESS',
  FAILURE: 'FAILURE',
  MODAL: 'MODAL',
} as const

// ============================================================================
// 提交结果码（4 种，管理员列）
// ============================================================================

export const MJ_SUBMIT_RESULT_CODES = {
  NOT_SUBMITTED: 0,
  SUBMITTED: 1,
  WAITING: 21,
  DUPLICATE: 22,
} as const

// ============================================================================
// StatusBadge variant 类型（收敛 default 15 色 → 6 variant）
// ============================================================================

export type MjBadgeVariant =
  | 'success'
  | 'warning'
  | 'danger'
  | 'info'
  | 'neutral'
  | 'primary'

export interface MjStatusMapping {
  /** i18n key 后缀，完整 key = `midjourney.taskType.${suffix}` 等 */
  labelKey: string
  variant: MjBadgeVariant
}

// ============================================================================
// 任务类型映射
// ============================================================================

export const MJ_TASK_TYPE_MAPPINGS: Record<string, MjStatusMapping> = {
  [MJ_TASK_TYPES.IMAGINE]: { labelKey: 'IMAGINE', variant: 'primary' },
  [MJ_TASK_TYPES.UPSCALE]: { labelKey: 'UPSCALE', variant: 'warning' },
  [MJ_TASK_TYPES.VIDEO]: { labelKey: 'VIDEO', variant: 'warning' },
  [MJ_TASK_TYPES.EDITS]: { labelKey: 'EDITS', variant: 'warning' },
  [MJ_TASK_TYPES.VARIATION]: { labelKey: 'VARIATION', variant: 'primary' },
  [MJ_TASK_TYPES.HIGH_VARIATION]: { labelKey: 'HIGH_VARIATION', variant: 'primary' },
  [MJ_TASK_TYPES.LOW_VARIATION]: { labelKey: 'LOW_VARIATION', variant: 'primary' },
  [MJ_TASK_TYPES.PAN]: { labelKey: 'PAN', variant: 'info' },
  [MJ_TASK_TYPES.DESCRIBE]: { labelKey: 'DESCRIBE', variant: 'warning' },
  [MJ_TASK_TYPES.BLEND]: { labelKey: 'BLEND', variant: 'success' },
  [MJ_TASK_TYPES.UPLOAD]: { labelKey: 'UPLOAD', variant: 'primary' },
  [MJ_TASK_TYPES.SHORTEN]: { labelKey: 'SHORTEN', variant: 'danger' },
  [MJ_TASK_TYPES.REROLL]: { labelKey: 'REROLL', variant: 'primary' },
  [MJ_TASK_TYPES.INPAINT]: { labelKey: 'INPAINT', variant: 'success' },
  [MJ_TASK_TYPES.SWAP_FACE]: { labelKey: 'SWAP_FACE', variant: 'primary' },
  [MJ_TASK_TYPES.ZOOM]: { labelKey: 'ZOOM', variant: 'success' },
  [MJ_TASK_TYPES.CUSTOM_ZOOM]: { labelKey: 'CUSTOM_ZOOM', variant: 'success' },
  [MJ_TASK_TYPES.MODAL]: { labelKey: 'MODAL', variant: 'success' },
}

// ============================================================================
// 任务状态映射
// ============================================================================

export const MJ_STATUS_MAPPINGS: Record<string, MjStatusMapping> = {
  [MJ_TASK_STATUS.SUCCESS]: { labelKey: 'SUCCESS', variant: 'success' },
  [MJ_TASK_STATUS.NOT_START]: { labelKey: 'NOT_START', variant: 'neutral' },
  [MJ_TASK_STATUS.SUBMITTED]: { labelKey: 'SUBMITTED', variant: 'warning' },
  [MJ_TASK_STATUS.IN_PROGRESS]: { labelKey: 'IN_PROGRESS', variant: 'primary' },
  [MJ_TASK_STATUS.FAILURE]: { labelKey: 'FAILURE', variant: 'danger' },
  [MJ_TASK_STATUS.MODAL]: { labelKey: 'MODAL', variant: 'warning' },
}

// ============================================================================
// 提交结果映射
// ============================================================================

export const MJ_SUBMIT_RESULT_MAPPINGS: Record<string, MjStatusMapping> = {
  [String(MJ_SUBMIT_RESULT_CODES.SUBMITTED)]: { labelKey: '1', variant: 'success' },
  [String(MJ_SUBMIT_RESULT_CODES.WAITING)]: { labelKey: '21', variant: 'success' },
  [String(MJ_SUBMIT_RESULT_CODES.DUPLICATE)]: { labelKey: '22', variant: 'warning' },
  [String(MJ_SUBMIT_RESULT_CODES.NOT_SUBMITTED)]: { labelKey: '0', variant: 'warning' },
}

// ============================================================================
// 查找辅助
// ============================================================================

const FALLBACK_MAPPING: MjStatusMapping = { labelKey: 'UNKNOWN', variant: 'neutral' }

export function getMjTaskTypeMapping(action: string): MjStatusMapping {
  return MJ_TASK_TYPE_MAPPINGS[action] ?? FALLBACK_MAPPING
}

export function getMjStatusMapping(status: string): MjStatusMapping {
  return MJ_STATUS_MAPPINGS[status] ?? FALLBACK_MAPPING
}

export function getMjSubmitResultMapping(code: number): MjStatusMapping {
  return MJ_SUBMIT_RESULT_MAPPINGS[String(code)] ?? FALLBACK_MAPPING
}
