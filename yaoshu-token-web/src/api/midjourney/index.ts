/**
 * Midjourney 任务日志 API Service。
 * 后端契约：MjLogController — GET /api/mj/（管理员）/ GET /api/mj/self（用户）。
 *
 * Mock 闭环：DEV + VITE_MJ_MOCK=true 时切换 mock 实现。
 */
import { request } from '@/utils/request'
import { MJ_ENDPOINTS, USE_MOCK } from './constants'
import type { GetMjLogsParams, MjLogsListData } from './types'

/**
 * 获取 MJ 任务日志（管理员全量）。
 * 后端 PageHelper 分页，pageNum 为 1-based。
 */
export function getAllMjLogs(params: GetMjLogsParams = {}): Promise<MjLogsListData> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGetAllMjLogs(params))
  }
  const query = buildMjQuery(params)
  return request.get<MjLogsListData>(`${MJ_ENDPOINTS.ADMIN_LIST}?${query}`)
}

/** 获取当前用户 MJ 任务日志 */
export function getUserMjLogs(params: GetMjLogsParams = {}): Promise<MjLogsListData> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGetUserMjLogs(params))
  }
  const query = buildMjQuery(params)
  return request.get<MjLogsListData>(`${MJ_ENDPOINTS.USER_LIST}?${query}`)
}

/** 构建查询参数字符串（跳过空值） */
function buildMjQuery(params: GetMjLogsParams): string {
  const parts: string[] = []
  if (params.pageNum != null) parts.push(`pageNum=${params.pageNum}`)
  if (params.pageSize != null) parts.push(`pageSize=${params.pageSize}`)
  if (params.channel_id) parts.push(`channel_id=${encodeURIComponent(params.channel_id)}`)
  if (params.mj_id) parts.push(`mj_id=${encodeURIComponent(params.mj_id)}`)
  if (params.start_timestamp != null) parts.push(`start_timestamp=${params.start_timestamp}`)
  if (params.end_timestamp != null) parts.push(`end_timestamp=${params.end_timestamp}`)
  return parts.join('&')
}
