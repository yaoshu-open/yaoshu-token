/**
 * 渠道 API Service（全量 40+ API）。
 * 后端契约：ai-docs/后端设计/API_Contract/契约_渠道管理.md。
 *
 * Mock 闭环：DEV + VITE_CHANNEL_MOCK=true 时切换 mock 实现（动态 import 避免污染
 * production build），与 deployment/playground 模式一致。
 *
 * request 工具经拦截器解包后直接返回 data，类型由调用方泛型标注。
 */
import { request } from '@/utils/request'
import { CHANNEL_ENDPOINTS, USE_MOCK } from './constants'
import type {
  AddChannelRequest,
  BatchDeleteParams,
  BatchSetTagParams,
  Channel,
  ChannelBalanceResponse,
  ChannelBatchTestResponse,
  ChannelModel,
  ChannelModelsResponse,
  ChannelTestResponse,
  CopyChannelParams,
  CopyChannelResponse,
  FetchModelsResponse,
  GetChannelResponse,
  GetChannelsParams,
  GetChannelsResponse,
  GroupsResponse,
  MultiKeyManageParams,
  MultiKeyStatusResponse,
  SearchChannelsParams,
  SearchChannelsResponse,
  TagEditResponse,
  TagModelsResponse,
  TagOperationParams,
  UpstreamUpdateApplyParams,
  UpstreamUpdateApplyResponse,
  UpstreamUpdateBatchResponse,
  UpstreamUpdateDetectResponse
} from './types'

// ============================================================================
// 基础 CRUD
// ============================================================================

/** 获取渠道列表（分页） */
export function getChannels(
  params: GetChannelsParams = {}
): Promise<GetChannelsResponse> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGetChannels(params))
  }
  return request.get<GetChannelsResponse>(CHANNEL_ENDPOINTS.LIST, { params })
}

/** 搜索渠道 */
export function searchChannels(
  params: SearchChannelsParams
): Promise<SearchChannelsResponse> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockSearchChannels(params))
  }
  return request.get<SearchChannelsResponse>(CHANNEL_ENDPOINTS.SEARCH, { params })
}

/** 获取单个渠道详情 */
export function getChannel(id: number): Promise<GetChannelResponse> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGetChannel(id))
  }
  return request.get<GetChannelResponse>(`${CHANNEL_ENDPOINTS.DETAIL}/${id}`)
}

/** 创建渠道（支持单/批量/多密钥模式） */
export function createChannel(
  data: AddChannelRequest
): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockCreateChannel())
  }
  return request.post<void>(CHANNEL_ENDPOINTS.CREATE, data)
}

/** 更新渠道 */
export function updateChannel(
  id: number,
  data: Partial<Channel>
): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockUpdateChannel(id))
  }
  return request.put<void>(CHANNEL_ENDPOINTS.UPDATE, { id, ...data })
}

/** 删除单个渠道 */
export function deleteChannel(id: number): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockDeleteChannel(id))
  }
  return request.delete<void>(`${CHANNEL_ENDPOINTS.DETAIL}/${id}`)
}

/** 批量删除渠道 */
export function batchDeleteChannels(
  data: BatchDeleteParams
): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockBatchDeleteChannels(data))
  }
  return request.post<void>(CHANNEL_ENDPOINTS.BATCH_DELETE, data)
}

/** 批量设置标签 */
export function batchSetChannelTag(
  data: BatchSetTagParams
): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockBatchSetChannelTag(data))
  }
  return request.post<void>(CHANNEL_ENDPOINTS.BATCH_TAG, data)
}

// ============================================================================
// 渠道操作
// ============================================================================

/** 测试渠道连通性 */
export function testChannel(
  id: number,
  params?: { model?: string; endpoint_type?: string; stream?: boolean }
): Promise<ChannelTestResponse> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockTestChannel(id))
  }
  return request.get<ChannelTestResponse>(
    `${CHANNEL_ENDPOINTS.TEST}/${id}`,
    { params }
  )
}

/** 更新渠道余额 */
export function updateChannelBalance(
  id: number
): Promise<ChannelBalanceResponse> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockUpdateChannelBalance(id))
  }
  return request.get<ChannelBalanceResponse>(
    `${CHANNEL_ENDPOINTS.UPDATE_BALANCE}/${id}`
  )
}

/** 获取上游可用模型 */
export function fetchUpstreamModels(
  id: number
): Promise<FetchModelsResponse> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockFetchUpstreamModels(id))
  }
  return request.get<FetchModelsResponse>(
    `${CHANNEL_ENDPOINTS.FETCH_MODELS}/${id}`
  )
}

/** 复制渠道 */
export function copyChannel(
  id: number,
  params: CopyChannelParams = {}
): Promise<CopyChannelResponse> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockCopyChannel(id))
  }
  return request.post<CopyChannelResponse>(
    `${CHANNEL_ENDPOINTS.COPY}/${id}`,
    null,
    { params }
  )
}

/** 修复渠道能力 */
export function fixChannelAbilities(): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockFixChannelAbilities())
  }
  return request.post<void>(CHANNEL_ENDPOINTS.FIX)
}

/** 删除所有禁用渠道 */
export function deleteDisabledChannels(): Promise<number> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockDeleteDisabledChannels())
  }
  return request.delete<number>(CHANNEL_ENDPOINTS.DELETE_DISABLED)
}

/** 获取渠道密钥（需2FA验证） */
export function getChannelKey(
  id: number,
  code?: string
): Promise<{ key: string }> {
  const payload = code ? { code } : undefined
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGetChannelKey(id))
  }
  return request.post<{ key: string }>(
    `${CHANNEL_ENDPOINTS.KEY}/${id}/key`,
    payload
  )
}

// ============================================================================
// Codex 渠道操作
// ============================================================================

export type CodexOAuthStartResponse = {
  authorizeUrl?: string
}

export type CodexOAuthCompleteResponse = {
  key?: string
  accountId?: string
  email?: string
  expiresAt?: string
  lastRefresh?: string
}

export type CodexCredentialRefreshResponse = {
  expiresAt?: string
  lastRefresh?: string
  accountId?: string
  email?: string
  channelId?: number
  channelType?: number
  channelName?: string
}

export type CodexUsageResponse = {
  upstreamStatus?: number
  data?: Record<string, unknown>
}

/** 启动 Codex OAuth */
export function startCodexOAuth(): Promise<CodexOAuthStartResponse> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockStartCodexOAuth())
  }
  return request.post<CodexOAuthStartResponse>(
    CHANNEL_ENDPOINTS.CODEX_OAUTH_START,
    {}
  )
}

/** 完成 Codex OAuth */
export function completeCodexOAuth(
  input: string
): Promise<CodexOAuthCompleteResponse> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockCompleteCodexOAuth(input))
  }
  return request.post<CodexOAuthCompleteResponse>(
    CHANNEL_ENDPOINTS.CODEX_OAUTH_COMPLETE,
    { input }
  )
}

/** 刷新 Codex 凭证 */
export function refreshCodexCredential(
  channelId: number
): Promise<CodexCredentialRefreshResponse> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockRefreshCodexCredential(channelId))
  }
  return request.post<CodexCredentialRefreshResponse>(
    `${CHANNEL_ENDPOINTS.CODEX_REFRESH}/${channelId}`,
    {}
  )
}

/** 获取 Codex 用量 */
export function getCodexUsage(
  channelId: number
): Promise<CodexUsageResponse> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGetCodexUsage(channelId))
  }
  return request.get<CodexUsageResponse>(
    `${CHANNEL_ENDPOINTS.CODEX_USAGE}/${channelId}`
  )
}

// ============================================================================
// 多密钥管理
// ============================================================================

/** 多密钥管理操作（get_key_status 返回状态结构，其他 action 成功返回 void） */
export function manageMultiKeys(
  params: MultiKeyManageParams
): Promise<MultiKeyStatusResponse | void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockManageMultiKeys(params))
  }
  return request.post<MultiKeyStatusResponse | void>(CHANNEL_ENDPOINTS.MULTI_KEY, params)
}

/** 获取多密钥状态 */
export function getMultiKeyStatus(
  channelId: number,
  page = 1,
  pageSize = 50,
  status?: number
): Promise<MultiKeyStatusResponse> {
  return manageMultiKeys({
    channelId,
    action: 'get_key_status',
    page,
    pageSize,
    status
  }) as Promise<MultiKeyStatusResponse>
}

/** 启用指定密钥 */
export function enableMultiKey(
  channelId: number,
  keyIndex: number
): Promise<void> {
  return manageMultiKeys({
    channelId,
    action: 'enable_key',
    keyIndex
  }) as Promise<void>
}

/** 禁用指定密钥 */
export function disableMultiKey(
  channelId: number,
  keyIndex: number
): Promise<void> {
  return manageMultiKeys({
    channelId,
    action: 'disable_key',
    keyIndex
  }) as Promise<void>
}

/** 删除指定密钥 */
export function deleteMultiKey(
  channelId: number,
  keyIndex: number
): Promise<void> {
  return manageMultiKeys({
    channelId,
    action: 'delete_key',
    keyIndex
  }) as Promise<void>
}

/** 启用所有密钥 */
export function enableAllMultiKeys(channelId: number): Promise<void> {
  return manageMultiKeys({
    channelId,
    action: 'enable_all_keys'
  }) as Promise<void>
}

/** 禁用所有密钥 */
export function disableAllMultiKeys(channelId: number): Promise<void> {
  return manageMultiKeys({
    channelId,
    action: 'disable_all_keys'
  }) as Promise<void>
}

/** 删除所有禁用密钥 */
export function deleteDisabledMultiKeys(channelId: number): Promise<void> {
  return manageMultiKeys({
    channelId,
    action: 'delete_disabled_keys'
  }) as Promise<void>
}

// ============================================================================
// 标签操作
// ============================================================================

/** 启用标签下所有渠道 */
export function enableTagChannels(tag: string): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockEnableTagChannels(tag))
  }
  return request.post<void>(CHANNEL_ENDPOINTS.TAG_ENABLED, { tag })
}

/** 禁用标签下所有渠道 */
export function disableTagChannels(tag: string): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockDisableTagChannels(tag))
  }
  return request.post<void>(CHANNEL_ENDPOINTS.TAG_DISABLED, { tag })
}

/** 编辑标签（覆盖操作） */
export function editTagChannels(params: TagOperationParams): Promise<TagEditResponse> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockEditTagChannels())
  }
  return request.put<TagEditResponse>(CHANNEL_ENDPOINTS.EDIT_TAG, params)
}

/** 获取标签当前模型列表（逗号分隔字符串） */
export function getTagModels(tag: string): Promise<TagModelsResponse> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGetTagModels(tag))
  }
  return request.get<TagModelsResponse>(CHANNEL_ENDPOINTS.TAG_MODELS, {
    params: { tag }
  })
}

// ============================================================================
// 工具函数
// ============================================================================

/** 从自定义端点获取模型（创建渠道前测试） */
export function fetchModels(data: {
  baseUrl: string
  type: number
  key: string
}): Promise<FetchModelsResponse> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockFetchModels(data))
  }
  return request.post<FetchModelsResponse>(CHANNEL_ENDPOINTS.FETCH_MODELS, data)
}

/** 删除 Ollama 模型 */
export function deleteOllamaModel(params: {
  channelId: number
  modelName: string
}): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockDeleteOllamaModel(params))
  }
  return request.delete<void>(CHANNEL_ENDPOINTS.OLLAMA_DELETE, {
    data: params
  })
}

/** 测试所有启用渠道（同步等待全部完成，可能耗时 1-5 分钟） */
export function testAllChannels(): Promise<ChannelBatchTestResponse> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockTestAllChannels())
  }
  return request.get<ChannelBatchTestResponse>(CHANNEL_ENDPOINTS.TEST_ALL)
}

/** 按 ID 列表批量测试渠道（仅测试传入的渠道，与全量测试互斥） */
export function testChannelsByIds(ids: number[]): Promise<ChannelBatchTestResponse> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockTestChannelsByIds(ids))
  }
  return request.post<ChannelBatchTestResponse>(CHANNEL_ENDPOINTS.TEST_BATCH, { ids })
}

/** 更新所有启用渠道余额 */
export function updateAllChannelsBalance(): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockUpdateAllChannelsBalance())
  }
  return request.get<void>(CHANNEL_ENDPOINTS.UPDATE_BALANCE_ALL)
}

/** 获取所有可用模型 */
export function getAllModels(): Promise<ChannelModelsResponse> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGetAllModels())
  }
  return request.get<ChannelModelsResponse>(CHANNEL_ENDPOINTS.MODELS)
}

/** 获取所有启用模型 */
export function getEnabledModels(): Promise<string[]> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGetEnabledModels())
  }
  return request.get<string[]>(CHANNEL_ENDPOINTS.MODELS_ENABLED)
}

/** 检查 Ollama 版本 */
export function getOllamaVersion(
  channelId: number
): Promise<{ version: string }> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGetOllamaVersion(channelId))
  }
  return request.get<{ version: string }>(
    `${CHANNEL_ENDPOINTS.OLLAMA_VERSION}/${channelId}`
  )
}

/** 获取用户分组列表 */
export function getGroups(): Promise<GroupsResponse> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGetGroups())
  }
  return request.get<GroupsResponse>(CHANNEL_ENDPOINTS.GROUPS)
}

/** 获取预填组（模型快捷选择） */
export function getPrefillGroups(
  type: 'model' | 'group' = 'model'
): Promise<Array<{ id: number; name: string; items: string | string[] }>> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGetPrefillGroups(type))
  }
  return request.get<Array<{ id: number; name: string; items: string | string[] }>>(
    CHANNEL_ENDPOINTS.PREFILL_GROUP, { params: { type } }
  )
}

// ============================================================================
// 上游模型更新（T-CH-07）
// ============================================================================

/** 检测单渠道上游模型变更 */
export function detectUpstreamUpdates(
  channelId: number
): Promise<UpstreamUpdateDetectResponse> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockDetectUpstreamUpdates(channelId))
  }
  return request.post<UpstreamUpdateDetectResponse>(
    CHANNEL_ENDPOINTS.UPSTREAM_UPDATE_DETECT,
    { id: channelId }
  )
}

/** 批量检测所有渠道上游模型变更 */
export function detectAllUpstreamUpdates(): Promise<UpstreamUpdateBatchResponse> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockDetectAllUpstreamUpdates())
  }
  return request.post<UpstreamUpdateBatchResponse>(
    CHANNEL_ENDPOINTS.UPSTREAM_UPDATE_DETECT_ALL,
    {}
  )
}

/** 应用单渠道上游模型更新 */
export function applyUpstreamUpdates(
  params: UpstreamUpdateApplyParams
): Promise<UpstreamUpdateApplyResponse> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockApplyUpstreamUpdates(params))
  }
  return request.post<UpstreamUpdateApplyResponse>(
    CHANNEL_ENDPOINTS.UPSTREAM_UPDATE_APPLY,
    params
  )
}

/** 批量应用所有渠道上游模型更新 */
export function applyAllUpstreamUpdates(): Promise<UpstreamUpdateBatchResponse> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockApplyAllUpstreamUpdates())
  }
  return request.post<UpstreamUpdateBatchResponse>(
    CHANNEL_ENDPOINTS.UPSTREAM_UPDATE_APPLY_ALL,
    {}
  )
}

export type { ChannelModel, TagOperationParams }
