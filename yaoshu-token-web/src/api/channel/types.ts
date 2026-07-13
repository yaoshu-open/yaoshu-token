/**
 * 渠道 API 类型声明（全量）。
 * 后端契约：ai-docs/后端设计/API_Contract/契约_渠道管理.md。
 *
 * T-CH-03 标签编辑类型保留；T-CH-01/02 主体迁移新增 Channel 全量类型。
 */
import type { PageInfo, PageParams } from '@/api/types'

// ============================================================================
// UA 覆盖模式（渠道级行为配置，中性通用字段）
// ============================================================================

export type UaOverrideMode = 'AUTO' | 'FORCE_IDE' | 'OFF'

// ============================================================================
// 标签编辑类型（T-CH-03，保留）
// ============================================================================

/** 标签编辑请求参数（PUT body） */
export interface TagOperationParams {
  /** 原标签名（必填） */
  tag: string
  /** 新标签名（留空=解散标签） */
  newTag?: string
  /** 模型重定向 JSON 字符串 */
  modelMapping?: string
  /** 模型列表（逗号分隔字符串） */
  models?: string
  /** 分组列表（逗号分隔字符串） */
  groups?: string
  /** 参数覆盖 JSON 字符串（旧格式直接覆盖 / 新格式 operations 数组） */
  paramOverride?: string
  /** 请求头覆盖 JSON 字符串（支持 {api_key} 变量） */
  headerOverride?: string
  /** 优先级 */
  priority?: number
  /** 权重 */
  weight?: number
}

/** 标签编辑响应（PUT 成功无业务数据，拦截器解包后为 void） */
export type TagEditResponse = void

/** 标签模型列表响应（data 为逗号分隔字符串，经拦截器解包后直接返回 string） */
export type TagModelsResponse = string

// ============================================================================
// Channel 主体类型（T-CH-01/02 新增）
// ============================================================================

/** 渠道可用模型项 */
export interface ChannelModel {
  id: string
  [key: string]: unknown
}

/** 模型列表响应（经拦截器解包后直接返回 ChannelModel[]） */
export type ChannelModelsResponse = ChannelModel[]

/** 分组列表响应（经拦截器解包后直接返回 string[]） */
export type GroupsResponse = string[]

/** 多密钥渠道信息 */
export interface ChannelInfo {
  isMultiKey: boolean
  multiKeySize: number
  multiKeyStatusList?: Record<string, number>
  multiKeyDisabledReason?: Record<string, string>
  multiKeyDisabledTime?: Record<string, number>
  multiKeyPollingIndex: number
  multiKeyMode: 'random' | 'polling'
}

/** 渠道主体 */
export interface Channel {
  id: number
  type: number
  key: string
  openaiOrganization?: string | null
  testModel?: string | null
  status: number // 1: enabled, 2: manual disabled, 3: auto disabled
  name: string
  weight?: number | null
  createdTime: number
  testTime: number
  responseTime: number // 毫秒
  baseUrl?: string | null
  other: string
  balance: number // USD
  balanceUpdatedTime: number
  models: string
  group: string
  usedQuota: number
  modelMapping?: string | null
  statusCodeMapping?: string | null
  priority?: number | null
  autoBan?: number | null
  otherInfo: string
  tag?: string | null
  setting?: string | null
  paramOverride?: string | null
  headerOverride?: string | null
  remark: string
  maxInputTokens: number
  channelInfo: ChannelInfo
  settings: string // other_settings JSON
  /** UA 覆盖模式（渠道级行为配置，中性通用字段） */
  uaOverrideMode?: UaOverrideMode
}

/** 渠道设置 */
export interface ChannelSettings {
  forceFormat?: boolean
  thinkingToContent?: boolean
  proxy?: string
  passThroughBodyEnabled?: boolean
  systemPrompt?: string
  systemPromptOverride?: boolean
}

/** 渠道其他设置 */
export interface ChannelOtherSettings {
  azureResponsesVersion?: string
  vertexKeyType?: 'json' | 'api_key'
  openrouterEnterprise?: boolean
  awsKeyType?: 'ak_sk' | 'api_key'
  allowServiceTier?: boolean
  disableStore?: boolean
  allowSafetyIdentifier?: boolean
  allowIncludeObfuscation?: boolean
  allowInferenceGeo?: boolean
  allowSpeed?: boolean
  claudeBetaQuery?: boolean
  upstreamModelUpdateCheckEnabled?: boolean
  upstreamModelUpdateAutoSyncEnabled?: boolean
  upstreamModelUpdateIgnoredModels?: string[]
  upstreamModelUpdateLastCheckTime?: number
  upstreamModelUpdateLastDetectedModels?: string[]
}

// ============================================================================
// API 响应类型
// ============================================================================

// 经 request 拦截器解包后直接返回业务数据（PageInfo 契约 + typeCounts 额外字段）
export type GetChannelsResponse = PageInfo<Channel> & {
  typeCounts?: Record<string, number>
}

export type SearchChannelsResponse = PageInfo<Channel> & {
  typeCounts?: Record<string, number>
}

// 经拦截器解包后直接返回 Channel（Java 后端剥离 Go 信封，Result.data = Channel）
export type GetChannelResponse = Channel

// channel test 成功响应：flag=true 时 Result.data，仅返回耗时
// 失败由后端抛异常（flag:false + msg），经拦截器 reject，消费方走 catch（错误类型机制待后端完善，见工单）
export interface ChannelTestResponse {
  time?: number
}

// 批量渠道测试单条结果（对齐后端 ChannelBatchTestItem record）
export interface ChannelBatchTestItem {
  channelId: number
  channelName: string
  testModel: string
  success: boolean
  responseTime: number
  statusChanged: boolean
  error: string | null
}

// 批量渠道测试响应：GET /api/channel/test
// results 仅含实际被测渠道（手动禁用渠道被过滤），故 results.length 可能小于 total
export interface ChannelBatchTestResponse {
  total: number
  completed: number
  results: ChannelBatchTestItem[]
}

// channel 余额查询成功响应：flag=true 时 Result.data，仅返回余额
// 失败由后端抛异常（flag:false + msg），经拦截器 reject，消费方走 catch
export interface ChannelBalanceResponse {
  balance?: number
}

// 经拦截器解包后返回 string[]（业务错误由拦截器 reject，进 catch）
export type FetchModelsResponse = string[]

// 经拦截器解包后返回新渠道 ID（Go 响应 data: {id} → Java Result.data = {id}）
export interface CopyChannelResponse {
  id: number
}

// ============================================================================
// 多密钥管理类型
// ============================================================================

export interface KeyStatus {
  index: number
  status: number // 1: enabled, 2: manual disabled, 3: auto disabled
  disabledTime?: number
  reason?: string
  keyPreview?: string
}

// 经拦截器解包后直接返回多密钥状态（仅 get_key_status action 返回此结构；其他 action 成功返回扁平数据，失败由拦截器 reject）
export interface MultiKeyStatusResponse {
  keys: KeyStatus[]
  total: number
  page: number
  pageSize: number
  totalPages: number
  enabledCount: number
  manualDisabledCount: number
  autoDisabledCount: number
}

/** 多密钥确认动作（联合类型，用于 MultiKeyManageDialog 确认流） */
export type MultiKeyConfirmAction =
  | { type: 'enable'; keyIndex: number }
  | { type: 'disable'; keyIndex: number }
  | { type: 'delete'; keyIndex: number }
  | { type: 'enable-all' }
  | { type: 'disable-all' }
  | { type: 'delete-disabled' }

// ============================================================================
// API 请求参数
// ============================================================================

// 注：分页参数 pageNum/pageSize 为 camelCase；sort_by/sort_order/status/group/tag/id_sort/tag_mode
// 为 URL query param 保留 snake_case（仅 JSON body 字段为 camelCase）
export type ChannelSortBy =
  | 'id'
  | 'name'
  | 'priority'
  | 'balance'
  | 'response_time'
  | 'test_time'
  | 'status'
  | 'weight'
  | 'used_quota'

export type ChannelSortOrder = 'asc' | 'desc'

export interface GetChannelsParams extends PageParams {
  status?: string
  type?: number
  group?: string
  tag?: string
  id_sort?: boolean
  tag_mode?: boolean
  sort_by?: ChannelSortBy
  sort_order?: ChannelSortOrder
}

export interface SearchChannelsParams extends PageParams {
  keyword?: string
  group?: string
  model?: string
  status?: string
  type?: number
  tag?: string
  id_sort?: boolean
  tag_mode?: boolean
  sort_by?: ChannelSortBy
  sort_order?: ChannelSortOrder
}

export interface CopyChannelParams {
  suffix?: string
  reset_balance?: boolean
}

/** 多密钥管理参数（POST JSON body，字段为 camelCase） */
export interface MultiKeyManageParams {
  channelId: number
  action:
    | 'get_key_status'
    | 'disable_key'
    | 'enable_key'
    | 'enable_all_keys'
    | 'disable_all_keys'
    | 'delete_key'
    | 'delete_disabled_keys'
  keyIndex?: number
  page?: number
  pageSize?: number
  status?: number
}

export interface BatchDeleteParams {
  ids: number[]
}

export interface BatchSetTagParams {
  ids: number[]
  tag: string | null
}

// ============================================================================
// 上游模型更新类型（T-CH-07）
// ============================================================================

// 经拦截器解包后直接返回检测结果（业务错误由拦截器 reject，进 catch）
export interface UpstreamUpdateDetectResponse {
  channelId?: number
  channelName?: string
  addModels?: string[]
  removeModels?: string[]
  lastCheckTime?: number
  autoAddedModels?: number
}

export interface UpstreamUpdateApplyParams {
  id: number
  addModels: string[]
  ignoreModels: string[]
  removeModels: string[]
}

// 经拦截器解包后直接返回应用结果（业务错误由拦截器 reject，进 catch）
export interface UpstreamUpdateApplyResponse {
  id?: number
  addedModels?: string[]
  removedModels?: string[]
  ignoredModels?: string[]
  remainingModels?: string[]
  remainingRemoveModels?: string[]
  models?: string
  settings?: string
}

// 经拦截器解包后直接返回批量结果（apply_all + detect_all 共用）
// NOTE: 后端此接口返回 snake_case（processed_channels/detected_add_models/failed_channel_ids），
// 与其他接口 camelCase 不一致，已发工单 [协作请求→yaoshu-token-后端] 要求统一；前端类型保留 camelCase（规范方向）
export interface UpstreamUpdateBatchResponse {
  processedChannels?: number
  addedModels?: number
  removedModels?: number
  detectedAddModels?: number
  detectedRemoveModels?: number
  failedChannelIds?: number[]
}

// ============================================================================
// 表单数据类型
// ============================================================================

export interface ChannelFormData {
  name: string
  type: number
  baseUrl: string
  key: string
  openaiOrganization?: string
  models: string
  group: string
  modelMapping?: string
  priority?: number
  weight?: number
  testModel?: string
  autoBan?: number
  status: number
  statusCodeMapping?: string
  tag?: string
  remark?: string
  setting?: string
  paramOverride?: string
  headerOverride?: string
  settings?: string
  other?: string
  multiKeyMode?: 'single' | 'batch' | 'multi_to_single'
  multiKeyType?: 'random' | 'polling'
  batchAddSetKeyPrefix2Name?: boolean
}

export interface AddChannelRequest {
  mode: 'single' | 'batch' | 'multi_to_single'
  multiKeyMode?: 'random' | 'polling'
  batchAddSetKeyPrefix2Name?: boolean
  channel: Partial<Channel>
}

// ============================================================================
// 模型可用性诊断类型
// ============================================================================

/** 诊断候选渠道信息 */
export interface DiagnoseChannel {
  channelId: number
  channelName: string
  status: number // 1=启用 2=手动禁用 3=自动禁用
  priority: number
  weight: number
  excluded: boolean
  excludeReason: string
}

/** 模型可用性诊断响应 */
export interface ModelRoutingDiagnoseResponse {
  model: string
  group: string
  totalAbilities: number
  available: boolean
  reason?: string
  suggestion?: string
  channels?: DiagnoseChannel[]
}
