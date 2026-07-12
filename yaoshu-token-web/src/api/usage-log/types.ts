/**
 * 调用日志 API 类型声明。
 * 后端契约：LogController — GET /api/log/（管理员）/ GET /api/log/self（用户）。
 */
import type { PageInfo, PageParams } from '@/api/types'

// ============================================================================
// 实体类型
// ============================================================================

/** 调用日志记录（响应体字段 camelCase；query param 见 GetLogsParams 保留 snake） */
export interface UsageLog {
  id: number
  userId: number
  createdAt: number
  type: number
  content: string
  username: string
  tokenName: string
  modelName: string
  quota: number
  promptTokens: number
  completionTokens: number
  useTime: number
  isStream: boolean
  channelId: number
  channelName?: string
  tokenId: number
  group: string
  ip: string
  other: string
  requestId: string
  upstreamRequestId: string
}

/** 日志统计 */
export interface LogStatistics {
  quota: number
  rpm: number
  tpm: number
}

/** usage 完整对象（后端 other.usage，含 reasoning_tokens 等扩展字段） */
export interface LogUsage {
  prompt_tokens?: number
  completion_tokens?: number
  total_tokens?: number
  reasoning_tokens?: number
  audio_tokens?: number
  image_tokens?: number
  promptCacheHitTokens?: number
  promptTokenDetails?: {
    cachedTokens?: number
    audioTokens?: number
    imageTokens?: number
  }
  promptTokensDetails?: {
    cachedTokens?: number
    audioTokens?: number
    imageTokens?: number
  }
  completionTokenDetails?: {
    reasoningTokens?: number
    audioTokens?: number
  }
  [key: string]: unknown
}

/** other 字段解析后的扩展数据 */
export interface LogOtherData {
  adminInfo?: {
    isMultiKey?: boolean
    multiKeyIndex?: number
    useChannel?: number[]
    channelAffinity?: ChannelAffinityInfo
    paymentMethod?: string
    callbackPaymentMethod?: string
    callerIp?: string
    serverIp?: string
    version?: string
    nodeName?: string
    adminUsername?: string
    adminId?: number | string
  }
  requestPath?: string
  isStream?: boolean
  audio?: boolean
  audioInput?: number
  audioOutput?: number
  textInput?: number
  textOutput?: number
  cachedTokens?: number
  cacheCreationTokens?: number
  modelRatio?: number
  completionRatio?: number
  modelPrice?: number
  groupRatio?: number
  userGroupRatio?: number
  cache_ratio?: number
  cacheCreationRatio?: number
  isModelMapped?: boolean
  upstreamModelName?: string
  frt?: number
  /** 总耗时（ms），后端契约 §日志响应 other JSON 字段（snake_case） */
  total_latency?: number
  /** 生成耗时（ms，= total_latency - frt） */
  completion_latency?: number
  /** 完整 usage 对象（含 reasoning_tokens/audio_tokens/image_tokens 等扩展字段） */
  usage?: LogUsage
  billingMode?: string
  matchedTier?: string
  isSystemPromptOverwritten?: boolean
  group?: string
  violationFee?: boolean
  violationFeeCode?: string
  feeQuota?: number
  rejectReason?: string
  isTask?: boolean
  taskId?: string
  [key: string]: unknown
}

/** 渠道亲和信息 */
export interface ChannelAffinityInfo {
  ruleName?: string
  selectedGroup?: string
  keySource?: string
  keyPath?: string
  keyKey?: string
  keyHint?: string
  keyFp?: string
  usingGroup?: string
}

// ============================================================================
// 请求/响应类型
// ============================================================================

/**
 * 获取调用日志参数。
 * URL query param 保留 snake_case（与后端契约一致）。
 */
export interface GetLogsParams extends PageParams {
  type?: number
  username?: string
  token_name?: string
  model_name?: string
  start_timestamp?: number
  end_timestamp?: number
  channel?: number
  group?: string
  request_id?: string
  upstream_request_id?: string
}

/** 调用日志列表响应（PageInfo 契约） */
export type LogsListData = PageInfo<UsageLog>

/** 日志统计响应 */
export type LogStatsData = LogStatistics
