/**
 * Dashboard API Service。
 * 后端契约：`契约_数据统计与日志.md` §二。
 */
import { request } from '@/utils/request'
import { DASHBOARD_ENDPOINTS } from './constants'
import type { AnalyticsParams, GetQuotaDatesParams, QuotaDataItem, QuotaDate } from './types'

/** 获取当前用户配额趋势数据（近 24h/30d） */
export function getUserQuotaDates(params: GetQuotaDatesParams): Promise<QuotaDate[]> {
  const query = `start_timestamp=${params.start_timestamp}&end_timestamp=${params.end_timestamp}&default_time=${params.default_time}`
  return request.get<QuotaDate[]>(`${DASHBOARD_ENDPOINTS.USER_QUOTA_DATES}?${query}`)
}

/** admin：全量配额数据（按 model_name + created_at 聚合） */
export function getAdminQuotaDates(params: AnalyticsParams): Promise<QuotaDataItem[]> {
  return request.get<QuotaDataItem[]>(DASHBOARD_ENDPOINTS.ADMIN_QUOTA_DATES, { params })
}

/** 用户自己的分析数据（QuotaDataItem 格式，用于 models 板块普通用户视角） */
export function getSelfAnalytics(params: AnalyticsParams): Promise<QuotaDataItem[]> {
  return request.get<QuotaDataItem[]>(DASHBOARD_ENDPOINTS.USER_QUOTA_DATES, { params })
}

/** admin：按用户聚合的配额数据 */
export function getQuotaDatesByUsers(params: AnalyticsParams): Promise<QuotaDataItem[]> {
  return request.get<QuotaDataItem[]>(DASHBOARD_ENDPOINTS.USERS_QUOTA_DATES, { params })
}
