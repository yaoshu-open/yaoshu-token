/**
 * 模型管理 API Mock 数据（DEV 环境 + VITE_MODEL_MOCK=true 时启用）。
 * 返回结构对齐真实 API 经 request.ts 拦截器解包后的业务类型。
 */
import type {
  GetModelsParams,
  Model,
  ModelsListData,
  PrefillGroup,
  PrefillGroupFormData,
  SearchModelsParams,
  SyncDiffData,
  SyncUpstreamData,
  Vendor,
  VendorsListData,
} from './types'

// ============================================================================
// Mock 数据
// ============================================================================

const MOCK_VENDORS: Vendor[] = [
  { id: 1, name: 'OpenAI', description: 'GPT series', icon: 'openai', status: 1, createdTime: 1700000000, updatedTime: 1700000000 },
  { id: 2, name: 'Anthropic', description: 'Claude series', icon: 'anthropic', status: 1, createdTime: 1700000000, updatedTime: 1700000000 },
  { id: 3, name: 'Google', description: 'Gemini series', icon: 'gemini', status: 1, createdTime: 1700000000, updatedTime: 1700000000 },
  { id: 4, name: 'DeepSeek', description: 'DeepSeek series', icon: 'deepseek', status: 1, createdTime: 1700000000, updatedTime: 1700000000 },
]

function createMockModel(overrides: Partial<Model> = {}): Model {
  return {
    id: 1,
    modelName: 'gpt-4o',
    description: 'Most advanced multimodal model',
    icon: 'openai',
    tags: 'chat,vision',
    vendorId: 1,
    endpoints: '["openai"]',
    status: 1,
    syncOfficial: 1,
    createdTime: 1700000000,
    updatedTime: 1700000000,
    nameRule: 0,
    enableGroups: ['default', 'premium'],
    quotaTypes: [0],
    ...overrides,
  }
}

const MOCK_MODELS: Model[] = Array.from({ length: 25 }, (_, i) =>
  createMockModel({
    id: i + 1,
    modelName: ['gpt-4o', 'gpt-4o-mini', 'claude-3-5-sonnet', 'gemini-1.5-pro', 'deepseek-chat'][i % 5] + (i > 4 ? `-${i}` : ''),
    description: `Model ${i + 1} description`,
    tags: ['chat,vision', 'chat', 'chat,reasoning', 'embeddings', 'image'][i % 5],
    vendorId: (i % 4) + 1,
    status: i % 3 === 0 ? 0 : 1,
    syncOfficial: i % 2,
    nameRule: (i % 4) as 0 | 1 | 2 | 3,
    matchedCount: i % 4 === 0 ? 0 : (i % 3) + 1,
    matchedModels: i % 4 === 0 ? [] : [`matched-${i}-1`, `matched-${i}-2`],
    createdTime: 1700000000 + i * 86400,
    updatedTime: 1700000000 + i * 86400 + 3600,
  })
)

const MOCK_PREFILL_GROUPS: PrefillGroup[] = [
  { id: 1, name: 'Common Chat Models', type: 'model', items: ['gpt-4o', 'claude-3-5-sonnet'], description: 'Frequently used chat models' },
  { id: 2, name: 'Vision Models', type: 'tag', items: 'vision', description: 'All vision-capable models' },
  { id: 3, name: 'Embedding Endpoints', type: 'endpoint', items: ['embeddings'], description: 'Embedding API endpoints' },
]

// ============================================================================
// Mock API 函数
// ============================================================================

function delay(ms = 200): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export async function mockGetModels(params: GetModelsParams = {}): Promise<ModelsListData> {
  await delay()
  const pageNum = params.pageNum ?? 1
  const pageSize = params.pageSize ?? 20
  let items = [...MOCK_MODELS]

  if (params.status && params.status !== 'all') {
    items = items.filter((m) => params.status === 'enabled' ? m.status === 1 : m.status !== 1)
  }
  if (params.sync_official && params.sync_official !== 'all') {
    items = items.filter((m) => params.sync_official === 'yes' ? m.syncOfficial === 1 : m.syncOfficial !== 1)
  }
  if (params.vendor && params.vendor !== 'all') {
    items = items.filter((m) => String(m.vendorId) === params.vendor)
  }

  const vendorCounts: Record<string, number> = { all: MOCK_MODELS.length }
  for (const v of MOCK_VENDORS) {
    vendorCounts[String(v.id)] = MOCK_MODELS.filter((m) => m.vendorId === v.id).length
  }

  const total = items.length
  const start = (pageNum - 1) * pageSize
  items = items.slice(start, start + pageSize)

  return { list: items, total, pageNum, pageSize, pages: Math.ceil(total / pageSize), hasNextPage: pageNum * pageSize < total, vendorCounts }
}

export async function mockSearchModels(params: SearchModelsParams): Promise<ModelsListData> {
  await delay()
  const pageNum = params.pageNum ?? 1
  const pageSize = params.pageSize ?? 20
  let items = [...MOCK_MODELS]

  if (params.keyword) {
    const kw = params.keyword.toLowerCase()
    items = items.filter((m) => m.modelName.toLowerCase().includes(kw))
  }
  if (params.status && params.status !== 'all') {
    items = items.filter((m) => params.status === 'enabled' ? m.status === 1 : m.status !== 1)
  }
  if (params.sync_official && params.sync_official !== 'all') {
    items = items.filter((m) => params.sync_official === 'yes' ? m.syncOfficial === 1 : m.syncOfficial !== 1)
  }
  if (params.vendor && params.vendor !== 'all') {
    items = items.filter((m) => String(m.vendorId) === params.vendor)
  }

  const total = items.length
  const start = (pageNum - 1) * pageSize
  items = items.slice(start, start + pageSize)

  return { list: items, total, pageNum, pageSize, pages: Math.ceil(total / pageSize), hasNextPage: pageNum * pageSize < total }
}

export async function mockGetModel(id: number): Promise<Model> {
  await delay()
  const model = MOCK_MODELS.find((m) => m.id === id)
  if (!model) throw new Error('Model not found')
  return model
}

export async function mockCreateModel(): Promise<void> {
  await delay(300)
}

export async function mockUpdateModel(): Promise<void> {
  await delay(300)
}

export async function mockDeleteModel(): Promise<void> {
  await delay(300)
}

export async function mockGetMissingModels(): Promise<string[]> {
  await delay()
  return ['gpt-5-preview', 'claude-4-opus', 'gemini-2.0-ultra']
}

export async function mockGetVendors(): Promise<VendorsListData> {
  await delay()
  const total = MOCK_VENDORS.length
  return { list: MOCK_VENDORS, total, pageNum: 1, pageSize: 1000, pages: 1, hasNextPage: false }
}

export async function mockSearchVendors(keyword: string): Promise<VendorsListData> {
  await delay()
  const list = MOCK_VENDORS.filter((v) => v.name.toLowerCase().includes(keyword.toLowerCase()))
  const total = list.length
  return { list, total, pageNum: 1, pageSize: 1000, pages: 1, hasNextPage: false }
}

export async function mockCreateVendor(): Promise<void> {
  await delay(300)
}

export async function mockUpdateVendor(): Promise<void> {
  await delay(300)
}

export async function mockDeleteVendor(): Promise<void> {
  await delay(300)
}

export async function mockPreviewUpstreamDiff(): Promise<SyncDiffData> {
  await delay(400)
  return {
    missing: [
      { modelName: 'gpt-5-preview', vendor: 'OpenAI' },
      { modelName: 'claude-4-opus', vendor: 'Anthropic' },
    ],
    conflicts: [
      {
        modelName: 'gpt-4o',
        local: { description: 'Local desc' },
        upstream: { description: 'Upstream desc' },
        fields: [{ field: 'description', local: 'Local desc', upstream: 'Upstream desc' }],
      },
    ],
  }
}

export async function mockSyncUpstream(): Promise<SyncUpstreamData> {
  await delay(600)
  return { createdModels: 2, updatedModels: 1, createdVendors: 0, skippedModels: [] }
}

export async function mockGetPrefillGroups(): Promise<PrefillGroup[]> {
  await delay()
  return MOCK_PREFILL_GROUPS
}

export async function mockCreatePrefillGroup(_data: PrefillGroupFormData): Promise<void> {
  await delay(300)
}

export async function mockUpdatePrefillGroup(_data: PrefillGroupFormData): Promise<void> {
  await delay(300)
}

export async function mockDeletePrefillGroup(_id: number): Promise<void> {
  await delay(300)
}
