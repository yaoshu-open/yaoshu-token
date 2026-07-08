/**
 * 任务日志 API 类型声明。
 * 后端契约：TaskController — GET /api/task/（管理员）/ GET /api/task/self（用户）。
 */
import type { PageInfo, PageParams } from '@/api/types'

/** 任务日志记录（响应体字段 camelCase；query param 见 GetTaskLogsParams 保留 snake） */
export interface TaskLog {
  id: number
  userId: number
  username?: string
  platform: string
  taskId: string
  action: string
  channelId: number
  /** 秒级时间戳 */
  submitTime: number
  /** 秒级时间戳 */
  finishTime?: number
  progress?: string
  progressMessageEn?: string
  /** JSON 字符串 */
  data?: string
  failReason?: string
  status: string
  other?: string
  createdAt?: number
  updatedAt?: number
}

/** 获取任务日志参数（URL query param 保留 snake_case） */
export interface GetTaskLogsParams extends PageParams {
  channel_id?: string
  task_id?: string
  start_timestamp?: number
  end_timestamp?: number
}

/** 任务日志列表响应（PageInfo 契约） */
export type TaskLogsListData = PageInfo<TaskLog>

/** 任务状态枚举 */
export const TASK_STATUS = {
  NOT_START: 'NOT_START',
  SUBMITTED: 'SUBMITTED',
  IN_PROGRESS: 'IN_PROGRESS',
  SUCCESS: 'SUCCESS',
  FAILURE: 'FAILURE',
  QUEUED: 'QUEUED',
  UNKNOWN: 'UNKNOWN',
} as const

/** 任务动作枚举 */
export const TASK_ACTIONS = {
  MUSIC: 'MUSIC',
  LYRICS: 'LYRICS',
  GENERATE: 'generate',
  TEXT_GENERATE: 'textGenerate',
  FIRST_TAIL_GENERATE: 'firstTailGenerate',
  REFERENCE_GENERATE: 'referenceGenerate',
  REMIX_GENERATE: 'remixGenerate',
} as const

/** 任务平台枚举 */
export const TASK_PLATFORMS = {
  SUNO: 'suno',
  KLING: 'kling',
  RUNWAY: 'runway',
  LUMA: 'luma',
  VIGGLE: 'viggle',
} as const

/** 状态映射（i18n key + variant） */
export const TASK_STATUS_MAPPINGS: Record<string, { labelKey: string; variant: string }> = {
  [TASK_STATUS.SUCCESS]: { labelKey: 'usageLogs.taskStatus.success', variant: 'success' },
  [TASK_STATUS.NOT_START]: { labelKey: 'usageLogs.taskStatus.notStart', variant: 'info' },
  [TASK_STATUS.SUBMITTED]: { labelKey: 'usageLogs.taskStatus.submitted', variant: 'warning' },
  [TASK_STATUS.IN_PROGRESS]: { labelKey: 'usageLogs.taskStatus.inProgress', variant: 'primary' },
  [TASK_STATUS.FAILURE]: { labelKey: 'usageLogs.taskStatus.failure', variant: 'danger' },
  [TASK_STATUS.QUEUED]: { labelKey: 'usageLogs.taskStatus.queued', variant: 'warning' },
  [TASK_STATUS.UNKNOWN]: { labelKey: 'usageLogs.taskStatus.unknown', variant: 'info' },
}

/** 动作映射 */
export const TASK_ACTION_MAPPINGS: Record<string, { labelKey: string; variant: string }> = {
  [TASK_ACTIONS.MUSIC]: { labelKey: 'usageLogs.taskAction.music', variant: 'info' },
  [TASK_ACTIONS.LYRICS]: { labelKey: 'usageLogs.taskAction.lyrics', variant: 'primary' },
  [TASK_ACTIONS.GENERATE]: { labelKey: 'usageLogs.taskAction.generate', variant: 'primary' },
  [TASK_ACTIONS.TEXT_GENERATE]: { labelKey: 'usageLogs.taskAction.textGenerate', variant: 'primary' },
  [TASK_ACTIONS.FIRST_TAIL_GENERATE]: { labelKey: 'usageLogs.taskAction.firstTailGenerate', variant: 'primary' },
  [TASK_ACTIONS.REFERENCE_GENERATE]: { labelKey: 'usageLogs.taskAction.referenceGenerate', variant: 'primary' },
  [TASK_ACTIONS.REMIX_GENERATE]: { labelKey: 'usageLogs.taskAction.remixGenerate', variant: 'primary' },
}

/** 平台映射 */
export const TASK_PLATFORM_MAPPINGS: Record<string, { labelKey: string; variant: string }> = {
  [TASK_PLATFORMS.SUNO]: { labelKey: 'usageLogs.taskPlatform.suno', variant: 'success' },
  [TASK_PLATFORMS.KLING]: { labelKey: 'usageLogs.taskPlatform.kling', variant: 'primary' },
  [TASK_PLATFORMS.RUNWAY]: { labelKey: 'usageLogs.taskPlatform.runway', variant: 'primary' },
  [TASK_PLATFORMS.LUMA]: { labelKey: 'usageLogs.taskPlatform.luma', variant: 'warning' },
  [TASK_PLATFORMS.VIGGLE]: { labelKey: 'usageLogs.taskPlatform.viggle', variant: 'primary' },
}

const FALLBACK_MAPPING = { labelKey: 'usageLogs.taskStatus.unknown', variant: 'info' }

export function getTaskStatusMapping(status: string) {
  return TASK_STATUS_MAPPINGS[status] ?? FALLBACK_MAPPING
}

export function getTaskActionMapping(action: string) {
  return TASK_ACTION_MAPPINGS[action] ?? FALLBACK_MAPPING
}

export function getTaskPlatformMapping(platform: string) {
  return TASK_PLATFORM_MAPPINGS[platform] ?? FALLBACK_MAPPING
}
