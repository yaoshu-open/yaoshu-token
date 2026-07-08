// 后端契约：GET /api/pricing 响应结构。

export interface PricingVendor {
  id: number
  name: string
  icon?: string
  description?: string
}

export interface PricingModel {
  id: number
  modelName: string
  description?: string
  icon?: string
  vendorId?: number
  vendorName?: string
  vendorIcon?: string
  vendorDescription?: string
  quotaType: number
  modelRatio: number
  completionRatio: number
  modelPrice?: number
  cacheRatio?: number | null
  createCacheRatio?: number | null
  imageRatio?: number | null
  audioRatio?: number | null
  audioCompletionRatio?: number | null
  enableGroup: string[]
  tags?: string
  supportedEndpointTypes?: string[]
  key?: string
  groupRatio?: Record<string, number>
  billingMode?: string
  billingExpr?: string
  pricingVersion?: string
  // 元数据字段（后端未返回时由 inferModelMetadata 推断）
  contextLength?: number
  maxOutputTokens?: number
  knowledgeCutoff?: string
  releaseDate?: string
  parameterCount?: string
  inputModalities?: Modality[]
  outputModalities?: Modality[]
  capabilities?: ModelCapability[]
}

export type Modality = 'text' | 'image' | 'audio' | 'video' | 'file'

export type ModelCapability =
  | 'function_calling'
  | 'streaming'
  | 'vision'
  | 'json_mode'
  | 'structured_output'
  | 'reasoning'
  | 'tools'
  | 'system_prompt'
  | 'web_search'
  | 'code_interpreter'
  | 'caching'
  | 'embeddings'

export interface EndpointInfo {
  path?: string
  method?: string
}

export interface PricingData {
  pricing: PricingModel[]
  vendors: PricingVendor[]
  group_ratio: Record<string, number>
  usable_group: Record<string, { desc: string; ratio: number }>
  supported_endpoint: Record<string, EndpointInfo>
  auto_groups: string[]
  pricing_version?: string
}

// S5: 移除 K 单位，仅保留 M（百万 tokens）
export type TokenUnit = 'M'

export type PriceType =
  | 'input'
  | 'output'
  | 'cache'
  | 'create_cache'
  | 'image'
  | 'audio_input'
  | 'audio_output'

export type QuotaType = 0 | 1
