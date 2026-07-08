/**
 * 任务日志 API Service。
 * 后端契约：TaskController — GET /api/task/（管理员）/ GET /api/task/self（用户）。
 */
import { request } from '@/utils/request'
import { TASK_ENDPOINTS } from './constants'
import type { GetTaskLogsParams, TaskLog, TaskLogsListData } from './types'

/** 构建查询参数字符串 */
function buildTaskQuery(params: GetTaskLogsParams): string {
  const parts: string[] = []
  if (params.pageNum != null) parts.push(`pageNum=${params.pageNum}`)
  if (params.pageSize != null) parts.push(`pageSize=${params.pageSize}`)
  if (params.channel_id) parts.push(`channel_id=${encodeURIComponent(params.channel_id)}`)
  if (params.task_id) parts.push(`task_id=${encodeURIComponent(params.task_id)}`)
  if (params.start_timestamp != null) parts.push(`start_timestamp=${params.start_timestamp}`)
  if (params.end_timestamp != null) parts.push(`end_timestamp=${params.end_timestamp}`)
  return parts.join('&')
}

/** 获取所有任务日志（管理员） */
export function getAllTaskLogs(params: GetTaskLogsParams = {}): Promise<TaskLogsListData> {
  const query = buildTaskQuery(params)
  return request.get<TaskLogsListData>(`${TASK_ENDPOINTS.ADMIN_LIST}?${query}`)
}

/** 获取当前用户任务日志 */
export function getUserTaskLogs(params: GetTaskLogsParams = {}): Promise<TaskLogsListData> {
  const query = buildTaskQuery(params)
  return request.get<TaskLogsListData>(`${TASK_ENDPOINTS.USER_LIST}?${query}`)
}

export type { TaskLog }
