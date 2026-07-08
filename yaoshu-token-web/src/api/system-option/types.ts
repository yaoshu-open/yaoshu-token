
/** 系统配置项（key-value） */
export interface SystemOption {
  key: string
  value: string
}

/** 更新配置请求 */
export interface UpdateOptionRequest {
  key: string
  value: string | boolean | number
}

/** 支付合规确认响应 */
export interface ConfirmPaymentComplianceResponse {
  confirmed: boolean
  termsVersion: string
  confirmedAt: number
  confirmedBy: number
}

/** 删除日志响应 */
export interface DeleteLogsResponse {
  deletedCount: number
}

/** 上游渠道 */
export interface UpstreamChannel {
  id: number
  name: string
  baseUrl: string
  status: number
  type?: number
}

/** 上游渠道列表响应 */
export interface UpstreamChannelsResponse {
  channels: UpstreamChannel[]
}

/** 比率差异 */
export interface RatioDifference {
  current: number | string | null
  upstreams: Record<string, number | string>
}

/** 差异映射 */
export type DifferencesMap = Record<string, Record<string, RatioDifference>>

/** 测试结果 */
export interface TestResult {
  name: string
  status: 'success' | 'error'
  error?: string
}

/** 上游比率同步请求 */
export interface FetchUpstreamRatiosRequest {
  upstreams: Array<{ id: number; name: string; baseUrl: string; endpoint: string }>
  timeout: number
}

/** 上游比率同步响应 */
export interface UpstreamRatiosResponse {
  differences: DifferencesMap
  testResults: TestResult[]
}

/** 启用模型列表项 */
export interface EnabledModel {
  modelName: string
  channelId?: number
}

/** 渠道亲和缓存统计 */
export interface ChannelAffinityStats {
  totalEntries: number
  activeEntries: number
  expiredEntries: number
  hitRate: number
  topRules: Array<{ ruleName: string; count: number }>
}
