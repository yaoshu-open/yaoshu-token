/**
 * 渠道 API Mock 数据（DEV 环境 + VITE_CHANNEL_MOCK=true 时启用）。
 *
 * 设计考量：Mock 数据覆盖渠道全链路 — 列表/搜索/CRUD/操作/多密钥/标签/Codex。
 * 返回结构对齐真实 API 经 request.ts 拦截器解包后的业务类型。
 */
import { CHANNEL_STATUS } from './constants'
import type {
  AddChannelRequest,
  BatchDeleteParams,
  BatchSetTagParams,
  Channel,
  ChannelBalanceResponse,
  ChannelModel,
  ChannelModelsResponse,
  ChannelTestResponse,
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
  UpstreamUpdateApplyParams,
  UpstreamUpdateApplyResponse,
  UpstreamUpdateBatchResponse,
  UpstreamUpdateDetectResponse
} from './types'

// ============================================================================
// Mock 数据
// ============================================================================

const MOCK_MODELS: ChannelModel[] = [
  { id: 'gpt-4o' },
  { id: 'gpt-4o-mini' },
  { id: 'gpt-4-turbo' },
  { id: 'claude-3-5-sonnet' },
  { id: 'claude-3-opus' },
  { id: 'gemini-1.5-pro' },
  { id: 'gemini-1.5-flash' },
  { id: 'deepseek-chat' },
  { id: 'deepseek-reasoner' },
  { id: 'qwen-max' },
  { id: 'qwen-plus' },
  { id: 'llama-3.3-70b' }
]

const MOCK_GROUPS = ['default', 'premium', 'internal', 'testing']

function createMockChannel(overrides: Partial<Channel> = {}): Channel {
  const now = Math.floor(Date.now() / 1000)
  return {
    id: 1,
    type: 1,
    key: 'sk-mock-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx',
    openaiOrganization: null,
    testModel: 'gpt-4o-mini',
    status: CHANNEL_STATUS.ENABLED,
    name: 'OpenAI Production',
    weight: 10,
    createdTime: now - 86400 * 30,
    testTime: now - 3600,
    responseTime: 450,
    baseUrl: '',
    other: '',
    balance: 125.5,
    balanceUpdatedTime: now - 3600,
    models: 'gpt-4o,gpt-4o-mini,gpt-4-turbo',
    group: 'default',
    usedQuota: 50000,
    modelMapping: '',
    statusCodeMapping: '',
    priority: 10,
    autoBan: 1,
    otherInfo: '',
    tag: 'production',
    setting: '',
    paramOverride: '',
    headerOverride: '',
    remark: '主力 OpenAI 渠道',
    maxInputTokens: 128000,
    channelInfo: {
      isMultiKey: false,
      multiKeySize: 0,
      multiKeyPollingIndex: 0,
      multiKeyMode: 'random'
    },
    settings: '{}',
    ...overrides
  }
}

const MOCK_CHANNELS: Channel[] = [
  createMockChannel({ id: 1, name: 'OpenAI Production', type: 1, balance: 125.5, responseTime: 450, tag: 'production', priority: 10 }),
  createMockChannel({ id: 2, name: 'Claude Sonnet', type: 14, balance: 89.2, responseTime: 620, tag: 'production', priority: 8, models: 'claude-3-5-sonnet,claude-3-opus' }),
  createMockChannel({ id: 3, name: 'Gemini Pro', type: 24, balance: 50.0, responseTime: 380, tag: 'testing', priority: 5, models: 'gemini-1.5-pro,gemini-1.5-flash' }),
  createMockChannel({ id: 4, name: 'DeepSeek Chat', type: 43, balance: 15.8, responseTime: 290, status: CHANNEL_STATUS.MANUAL_DISABLED, tag: 'backup', priority: 3, models: 'deepseek-chat,deepseek-reasoner' }),
  createMockChannel({ id: 5, name: 'Ollama Local', type: 4, balance: 0, responseTime: 120, status: CHANNEL_STATUS.AUTO_DISABLED, tag: 'internal', priority: 1, models: 'llama-3.3-70b,qwen-max', baseUrl: 'http://localhost:11434' }),
  createMockChannel({ id: 6, name: 'Azure OpenAI', type: 3, balance: 200.0, responseTime: 510, tag: 'production', priority: 9, models: 'gpt-4o,gpt-4o-mini' }),
  createMockChannel({ id: 7, name: 'Multi-Key Pool', type: 1, balance: 0, responseTime: 0, tag: 'production', priority: 7, channelInfo: { isMultiKey: true, multiKeySize: 5, multiKeyPollingIndex: 2, multiKeyMode: 'polling' } })
]

// ============================================================================
// 标签编辑 Mock（T-CH-03，保留）
// ============================================================================

/** 模拟编辑标签（对齐拦截器解包后返回 void） */
export function mockEditTagChannels(): Promise<TagEditResponse> {
  return Promise.resolve(undefined)
}

/** 模拟获取标签当前模型列表（逗号分隔字符串，对齐拦截器解包后返回 string） */
export function mockGetTagModels(tag: string): Promise<TagModelsResponse> {
  const data = ['gpt-4o', 'gpt-4o-mini', 'claude-3-5-sonnet'].join(',')
  void tag
  return Promise.resolve(data)
}

/** 模拟获取所有渠道可用模型（对齐拦截器解包后返回 ChannelModel[]） */
export function mockGetAllModels(): Promise<ChannelModelsResponse> {
  return Promise.resolve(MOCK_MODELS)
}

/** 模拟获取用户分组列表（对齐拦截器解包后返回 string[]） */
export function mockGetGroups(): Promise<GroupsResponse> {
  return Promise.resolve(MOCK_GROUPS)
}

// ============================================================================
// 基础 CRUD Mock
// ============================================================================

/** 模拟获取渠道列表 */
export function mockGetChannels(
  params: GetChannelsParams = {}
): Promise<GetChannelsResponse> {
  let items = [...MOCK_CHANNELS]

  // 状态筛选
  if (params.status && params.status !== 'all') {
    if (params.status === 'enabled') {
      items = items.filter((c) => c.status === CHANNEL_STATUS.ENABLED)
    } else if (params.status === 'disabled') {
      items = items.filter((c) => c.status !== CHANNEL_STATUS.ENABLED)
    }
  }

  // 类型筛选
  if (params.type) {
    items = items.filter((c) => c.type === params.type)
  }

  // 分组筛选
  if (params.group) {
    items = items.filter((c) => c.group === params.group)
  }

  const total = items.length
  const pageNum = params.pageNum ?? 1
  const pageSize = params.pageSize ?? 20
  const start = (pageNum - 1) * pageSize
  const paged = items.slice(start, start + pageSize)

  return Promise.resolve({
    list: paged,
    total,
    pageNum,
    pageSize,
    pages: Math.ceil(total / pageSize),
    hasNextPage: pageNum * pageSize < total,
    typeCounts: { '1': 3, '14': 1, '24': 1, '43': 1, '4': 1, '3': 1 }
  })
}

/** 模拟搜索渠道 */
export function mockSearchChannels(
  params: SearchChannelsParams
): Promise<SearchChannelsResponse> {
  let items = [...MOCK_CHANNELS]

  if (params.keyword) {
    const kw = params.keyword.toLowerCase()
    items = items.filter(
      (c) =>
        c.name.toLowerCase().includes(kw) ||
        c.models.toLowerCase().includes(kw) ||
        (c.tag ?? '').toLowerCase().includes(kw)
    )
  }

  return Promise.resolve({
    list: items,
    total: items.length,
    pageNum: 1,
    pageSize: items.length,
    pages: 1,
    hasNextPage: false,
    typeCounts: { '1': 3, '14': 1, '24': 1, '43': 1, '4': 1, '3': 1 }
  })
}

/** 模拟获取单个渠道 */
export function mockGetChannel(id: number): Promise<GetChannelResponse> {
  const channel = MOCK_CHANNELS.find((c) => c.id === id) ?? MOCK_CHANNELS[0]
  return Promise.resolve(channel)
}

/** 模拟创建渠道 */
export function mockCreateChannel(): Promise<void> {
  return Promise.resolve()
}

/** 模拟更新渠道 */
export function mockUpdateChannel(id: number): Promise<void> {
  void id
  return Promise.resolve()
}

/** 模拟删除渠道 */
export function mockDeleteChannel(id: number): Promise<void> {
  void id
  return Promise.resolve()
}

/** 模拟批量删除 */
export function mockBatchDeleteChannels(data: BatchDeleteParams): Promise<void> {
  void data
  return Promise.resolve()
}

/** 模拟批量设置标签 */
export function mockBatchSetChannelTag(data: BatchSetTagParams): Promise<void> {
  void data
  return Promise.resolve()
}

// ============================================================================
// 渠道操作 Mock
// ============================================================================

/** 模拟测试渠道 */
export function mockTestChannel(id: number): Promise<ChannelTestResponse> {
  void id
  return Promise.resolve({
    time: Math.floor(Math.random() * 800 + 200)
  })
}

/** 模拟更新余额 */
export function mockUpdateChannelBalance(
  id: number
): Promise<ChannelBalanceResponse> {
  void id
  return Promise.resolve({
    balance: Math.random() * 200
  })
}

/** 模拟获取上游模型 */
export function mockFetchUpstreamModels(id: number): Promise<FetchModelsResponse> {
  void id
  return Promise.resolve(['gpt-4o', 'gpt-4o-mini', 'gpt-4-turbo', 'o1-preview', 'o1-mini'])
}

/** 模拟复制渠道 */
export function mockCopyChannel(id: number): Promise<CopyChannelResponse> {
  void id
  return Promise.resolve({ id: Math.floor(Math.random() * 1000 + 100) })
}

/** 模拟修复渠道能力 */
export function mockFixChannelAbilities(): Promise<void> {
  return Promise.resolve()
}

/** 模拟删除禁用渠道 */
export function mockDeleteDisabledChannels(): Promise<number> {
  return Promise.resolve(2)
}

/** 模拟获取渠道密钥 */
export function mockGetChannelKey(
  id: number
): Promise<{ key: string }> {
  void id
  return Promise.resolve({ key: 'sk-mock-revealed-key' })
}

// ============================================================================
// Codex Mock
// ============================================================================

export function mockStartCodexOAuth(): Promise<{
  authorizeUrl?: string
}> {
  return Promise.resolve({
    authorizeUrl: 'https://mock-oauth.example.com/authorize'
  })
}

export function mockCompleteCodexOAuth(
  input: string
): Promise<{
  key?: string; accountId?: string; email?: string
}> {
  void input
  return Promise.resolve({
    key: 'sk-codex-mock', accountId: 'acc_mock', email: 'mock@codex.ai'
  })
}

export function mockRefreshCodexCredential(
  channelId: number
): Promise<{
  expiresAt?: string; channelId?: number
}> {
  void channelId
  return Promise.resolve({
    expiresAt: new Date(Date.now() + 86400000).toISOString(), channelId
  })
}

export function mockGetCodexUsage(
  channelId: number
): Promise<{
  upstreamStatus?: number
  data?: Record<string, unknown>
}> {
  void channelId
  return Promise.resolve({
    upstreamStatus: 200,
    data: { total_requests: 1000, total_tokens: 500000 }
  })
}

// ============================================================================
// 多密钥 Mock
// ============================================================================

export function mockManageMultiKeys(
  params: MultiKeyManageParams
): Promise<MultiKeyStatusResponse | void> {
  if (params.action === 'get_key_status') {
    const keys = Array.from({ length: 5 }, (_, i) => ({
      index: i,
      status: i === 2 ? 2 : 1,
      keyPreview: `sk-mock-****${i}`
    }))
    return Promise.resolve({
      keys,
      total: 5,
      page: params.page ?? 1,
      pageSize: params.pageSize ?? 50,
      totalPages: 1,
      enabledCount: 4,
      manualDisabledCount: 1,
      autoDisabledCount: 0
    })
  }
  return Promise.resolve()
}

// ============================================================================
// 标签操作 Mock
// ============================================================================

export function mockEnableTagChannels(tag: string): Promise<void> {
  void tag
  return Promise.resolve()
}

export function mockDisableTagChannels(tag: string): Promise<void> {
  void tag
  return Promise.resolve()
}

// ============================================================================
// 工具函数 Mock
// ============================================================================

export function mockFetchModels(
  data: { baseUrl: string; type: number; key: string }
): Promise<FetchModelsResponse> {
  void data
  return Promise.resolve(['gpt-4o', 'gpt-4o-mini'])
}

export function mockDeleteOllamaModel(params: {
  channelId: number
  modelName: string
}): Promise<void> {
  void params
  return Promise.resolve()
}

export function mockTestAllChannels(): Promise<void> {
  return Promise.resolve()
}

export function mockUpdateAllChannelsBalance(): Promise<void> {
  return Promise.resolve()
}

export function mockGetEnabledModels(): Promise<string[]> {
  return Promise.resolve(MOCK_MODELS.map((m) => m.id))
}

export function mockGetOllamaVersion(
  channelId: number
): Promise<{ version: string }> {
  void channelId
  return Promise.resolve({ version: '0.5.0' })
}

export function mockGetPrefillGroups(
  type: 'model' | 'group'
): Promise<Array<{ id: number; name: string; items: string | string[] }>> {
  void type
  return Promise.resolve([
    { id: 1, name: 'GPT Family', items: 'gpt-4o,gpt-4o-mini,gpt-4-turbo' },
    { id: 2, name: 'Claude Family', items: 'claude-3-5-sonnet,claude-3-opus' }
  ])
}

// ============================================================================
// 上游模型更新 Mock（T-CH-07）
// ============================================================================

export function mockDetectUpstreamUpdates(
  channelId: number
): Promise<UpstreamUpdateDetectResponse> {
  void channelId
  return Promise.resolve({
    addModels: ['gpt-4o-new', 'o1-preview'],
    removeModels: ['gpt-3.5-turbo-deprecated']
  })
}

export function mockDetectAllUpstreamUpdates(): Promise<UpstreamUpdateBatchResponse> {
  return Promise.resolve({
    processedChannels: 5,
    detectedAddModels: 12,
    detectedRemoveModels: 3,
    failedChannelIds: []
  })
}

export function mockApplyUpstreamUpdates(
  params: UpstreamUpdateApplyParams
): Promise<UpstreamUpdateApplyResponse> {
  return Promise.resolve({
    addedModels: params.addModels,
    removedModels: params.removeModels
  })
}

export function mockApplyAllUpstreamUpdates(): Promise<UpstreamUpdateBatchResponse> {
  return Promise.resolve({
    processedChannels: 5,
    addedModels: 12,
    removedModels: 3,
    failedChannelIds: []
  })
}

// 保持 AddChannelRequest 类型引用（避免未使用警告）
export type { AddChannelRequest }
