/**
 * 调用日志 API Service。
 * 后端契约：LogController — GET /api/log/（管理员）/ GET /api/log/self（用户）。
 */
import { request } from '@/utils/request'
import { LOG_ENDPOINTS } from './constants'
import type { GetLogsParams, LogStatsData, LogsListData, UsageLog } from './types'

/** 构建查询参数字符串（跳过空值，保留 snake_case query param） */
function buildLogQuery(params: GetLogsParams): string {
  const parts: string[] = []
  if (params.pageNum != null) parts.push(`pageNum=${params.pageNum}`)
  if (params.pageSize != null) parts.push(`pageSize=${params.pageSize}`)
  if (params.type != null && params.type !== 0) parts.push(`type=${params.type}`)
  if (params.username) parts.push(`username=${encodeURIComponent(params.username)}`)
  if (params.token_name) parts.push(`token_name=${encodeURIComponent(params.token_name)}`)
  if (params.model_name) parts.push(`model_name=${encodeURIComponent(params.model_name)}`)
  if (params.start_timestamp != null) parts.push(`start_timestamp=${params.start_timestamp}`)
  if (params.end_timestamp != null) parts.push(`end_timestamp=${params.end_timestamp}`)
  if (params.channel != null) parts.push(`channel=${params.channel}`)
  if (params.group) parts.push(`group=${encodeURIComponent(params.group)}`)
  if (params.request_id) parts.push(`request_id=${encodeURIComponent(params.request_id)}`)
  if (params.upstream_request_id) parts.push(`upstream_request_id=${encodeURIComponent(params.upstream_request_id)}`)
  return parts.join('&')
}

/** 获取所有调用日志（管理员） */
export function getAllLogs(params: GetLogsParams = {}): Promise<LogsListData> {
  const query = buildLogQuery(params)
  return request.get<LogsListData>(`${LOG_ENDPOINTS.ADMIN_LIST}?${query}`)
}

/** 获取当前用户调用日志 */
export function getUserLogs(params: GetLogsParams = {}): Promise<LogsListData> {
  const query = buildLogQuery(params)
  return request.get<LogsListData>(`${LOG_ENDPOINTS.USER_LIST}?${query}`)
}

/** 获取日志统计（管理员） */
export function getLogStats(params: GetLogsParams = {}): Promise<LogStatsData> {
  const query = buildLogQuery(params)
  return request.get<LogStatsData>(`${LOG_ENDPOINTS.ADMIN_STAT}?${query}`)
}

/** 获取当前用户日志统计 */
export function getUserLogStats(params: GetLogsParams = {}): Promise<LogStatsData> {
  const query = buildLogQuery(params)
  return request.get<LogStatsData>(`${LOG_ENDPOINTS.USER_STAT}?${query}`)
}

export type { UsageLog }
