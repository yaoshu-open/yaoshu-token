/**
 * Midjourney 任务日志 API 类型声明。
 * 后端契约：MjLogController — GET /api/mj/（管理员）/ GET /api/mj/self（用户）。
 */
import type { PageInfo, PageParams } from '@/api/types'

// ============================================================================
// 实体类型
// ============================================================================

/** MJ 任务日志记录（响应体字段 camelCase；query param 见 GetMjLogsParams 保留 snake） */
export interface MidjourneyLog {
  id: number
  userId: number
  channelId: number
  code: number
  mjId: string
  action: string
  submitTime: number
  finishTime?: number
  startTime?: number
  failReason?: string
  progress: string
  prompt: string
  promptEn?: string
  description?: string
  status: string
  imageUrl?: string
  videoUrl?: string
  buttons?: string
  properties?: string
  quota?: number
  createdAt?: number
}

// ============================================================================
// 请求/响应类型
// ============================================================================

// channel_id/mj_id/start_timestamp/end_timestamp 为 URL query param，保留 snake_case（非 JSON body）
export interface GetMjLogsParams extends PageParams {
  channel_id?: string
  mj_id?: string
  start_timestamp?: number
  end_timestamp?: number
}

/** MJ 日志列表响应（经拦截器解包后的业务数据，PageInfo 契约） */
export type MjLogsListData = PageInfo<MidjourneyLog>
