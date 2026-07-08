/**
 * 系统配置 API Service。
 * 后端契约：OptionController — GET/PUT /api/option/。
 */
import { request } from '@/utils/request'
import { OPTION_ENDPOINTS } from './constants'
import type {
  ChannelAffinityStats,
  ConfirmPaymentComplianceResponse,
  DeleteLogsResponse,
  EnabledModel,
  FetchUpstreamRatiosRequest,
  SystemOption,
  UpstreamChannelsResponse,
  UpstreamRatiosResponse,
  UpdateOptionRequest,
} from './types'

/** 获取全量系统配置 */
export function getSystemOptions(): Promise<SystemOption[]> {
  return request.get<SystemOption[]>(OPTION_ENDPOINTS.LIST)
}

/** 更新单条配置 */
export function updateSystemOption(payload: UpdateOptionRequest): Promise<void> {
  return request.put<void>(OPTION_ENDPOINTS.UPDATE, payload)
}

/** 批量更新配置 */
export function batchUpdateSystemOptions(payload: UpdateOptionRequest[]): Promise<void> {
  return request.put<void>(OPTION_ENDPOINTS.UPDATE, payload)
}

/** 确认支付合规 */
export function confirmPaymentCompliance(): Promise<ConfirmPaymentComplianceResponse> {
  return request.post<ConfirmPaymentComplianceResponse>(OPTION_ENDPOINTS.PAYMENT_COMPLIANCE, { confirmed: true })
}

/** 删除指定时间前的日志 */
export function deleteLogsBefore(targetTimestamp: number): Promise<DeleteLogsResponse> {
  return request.delete<DeleteLogsResponse>(OPTION_ENDPOINTS.DELETE_LOGS, { params: { target_timestamp: targetTimestamp } })
}

/** 重置模型倍率 */
export function resetModelRatios(): Promise<void> {
  return request.post<void>(OPTION_ENDPOINTS.RESET_MODEL_RATIO)
}

/** 获取上游渠道列表 */
export function getUpstreamChannels(): Promise<UpstreamChannelsResponse> {
  return request.get<UpstreamChannelsResponse>(OPTION_ENDPOINTS.UPSTREAM_CHANNELS)
}

/** 拉取上游倍率 */
export function fetchUpstreamRatios(payload: FetchUpstreamRatiosRequest): Promise<UpstreamRatiosResponse> {
  return request.post<UpstreamRatiosResponse>(OPTION_ENDPOINTS.UPSTREAM_FETCH, payload)
}

/** 获取启用模型列表（T-ST-01 unset 筛选数据源） */
export function getEnabledModels(): Promise<EnabledModel[]> {
  return request.get<EnabledModel[]>(OPTION_ENDPOINTS.ENABLED_MODELS)
}

/** 获取渠道亲和缓存统计 */
export function getChannelAffinityStats(): Promise<ChannelAffinityStats> {
  return request.get<ChannelAffinityStats>(OPTION_ENDPOINTS.CHANNEL_AFFINITY_STATS)
}
