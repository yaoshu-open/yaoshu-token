// Pricing Mock 数据生成。对齐 rankings/perf-metrics mock 模式。
import type { PricingData, PricingModel, PricingVendor } from './types'

const MOCK_VENDORS: PricingVendor[] = [
  { id: 1, name: 'OpenAI', icon: 'openai' },
  { id: 2, name: 'Anthropic', icon: 'anthropic' },
  { id: 3, name: 'Google', icon: 'google' },
  { id: 4, name: 'DeepSeek', icon: 'deepseek' },
  { id: 5, name: 'Qwen', icon: 'qwen' },
  { id: 6, name: 'Meta', icon: 'meta' },
  { id: 7, name: 'Mistral', icon: 'mistral' }
]

const MOCK_GROUPS = ['default', 'vip', 'auto']

interface MockModelTemplate {
  name: string
  vendorId: number
  vendorName: string
  quotaType: number
  modelRatio: number
  completionRatio: number
  model_price?: number
  tags?: string
  endpoints?: string[]
  cacheRatio?: number | null
  billingMode?: string
  billingExpr?: string
}

const MOCK_TEMPLATES: MockModelTemplate[] = [
  { name: 'gpt-4o', vendorId: 1, vendorName: 'OpenAI', quotaType: 0, modelRatio: 2.5, completionRatio: 4, tags: 'vision,function-calling,json', endpoints: ['openai'], cacheRatio: 0.5 },
  { name: 'gpt-4o-mini', vendorId: 1, vendorName: 'OpenAI', quotaType: 0, modelRatio: 0.15, completionRatio: 0.6, tags: 'vision,function-calling', endpoints: ['openai'], cacheRatio: 0.5 },
  { name: 'o1', vendorId: 1, vendorName: 'OpenAI', quotaType: 0, modelRatio: 15, completionRatio: 60, tags: 'reasoning', endpoints: ['openai'] },
  { name: 'o3-mini', vendorId: 1, vendorName: 'OpenAI', quotaType: 0, modelRatio: 1.1, completionRatio: 4.4, tags: 'reasoning', endpoints: ['openai'] },
  { name: 'claude-3.5-sonnet', vendorId: 2, vendorName: 'Anthropic', quotaType: 0, modelRatio: 3, completionRatio: 15, tags: 'vision,function-calling', endpoints: ['anthropic'], cacheRatio: 0.5, billingMode: 'tiered_expr', billingExpr: 'tier("Standard", p*3 * c*15) * (hour("Asia/Shanghai") >= 22 || hour("Asia/Shanghai") < 8 ? 0.8 : 1)' },
  { name: 'claude-3-haiku', vendorId: 2, vendorName: 'Anthropic', quotaType: 0, modelRatio: 0.25, completionRatio: 1.25, tags: 'vision', endpoints: ['anthropic'] },
  { name: 'gemini-2.0-flash', vendorId: 3, vendorName: 'Google', quotaType: 0, modelRatio: 0.1, completionRatio: 0.4, tags: 'vision,function-calling', endpoints: ['gemini'] },
  { name: 'gemini-1.5-pro', vendorId: 3, vendorName: 'Google', quotaType: 0, modelRatio: 1.25, completionRatio: 5, tags: 'vision,function-calling', endpoints: ['gemini'] },
  { name: 'deepseek-chat', vendorId: 4, vendorName: 'DeepSeek', quotaType: 0, modelRatio: 0.14, completionRatio: 0.28, tags: 'function-calling', endpoints: ['openai'], cacheRatio: 0.1 },
  { name: 'deepseek-reasoner', vendorId: 4, vendorName: 'DeepSeek', quotaType: 0, modelRatio: 0.55, completionRatio: 2.19, tags: 'reasoning', endpoints: ['openai'] },
  { name: 'qwen-max', vendorId: 5, vendorName: 'Qwen', quotaType: 0, modelRatio: 0.8, completionRatio: 2, tags: 'function-calling', endpoints: ['openai'] },
  { name: 'qwen-turbo', vendorId: 5, vendorName: 'Qwen', quotaType: 0, modelRatio: 0.05, completionRatio: 0.2, tags: 'function-calling', endpoints: ['openai'] },
  { name: 'llama-3.3-70b', vendorId: 6, vendorName: 'Meta', quotaType: 0, modelRatio: 0.2, completionRatio: 0.4, tags: 'function-calling', endpoints: ['openai'] },
  { name: 'mistral-large', vendorId: 7, vendorName: 'Mistral', quotaType: 0, modelRatio: 0.4, completionRatio: 1.2, tags: 'function-calling', endpoints: ['openai'] },
  { name: 'dall-e-3', vendorId: 1, vendorName: 'OpenAI', quotaType: 1, modelRatio: 0, completionRatio: 0, model_price: 0.04, tags: 'image', endpoints: ['image-generation'] },
  { name: 'text-embedding-3-small', vendorId: 1, vendorName: 'OpenAI', quotaType: 0, modelRatio: 0.02, completionRatio: 0, tags: 'embedding', endpoints: ['embeddings'] },
  { name: 'text-embedding-3-large', vendorId: 1, vendorName: 'OpenAI', quotaType: 0, modelRatio: 0.13, completionRatio: 0, tags: 'embedding', endpoints: ['embeddings'] }
]

function buildModel(template: MockModelTemplate, index: number): PricingModel {
  return {
    id: index + 1,
    modelName: template.name,
    vendorId: template.vendorId,
    vendorName: template.vendorName,
    quotaType: template.quotaType,
    modelRatio: template.modelRatio,
    completionRatio: template.completionRatio,
    modelPrice: 'model_price' in template ? (template as { model_price?: number }).model_price : undefined,
    cacheRatio: template.cacheRatio ?? null,
    enableGroup: MOCK_GROUPS,
    tags: template.tags,
    supportedEndpointTypes: template.endpoints,
    billingMode: template.billingMode,
    billingExpr: template.billingExpr,
    groupRatio: { default: 1, vip: 0.8, auto: 1 }
  }
}

export function mockGetPricing(): Promise<PricingData> {
  const models = MOCK_TEMPLATES.map(buildModel)
  const data: PricingData = {
    pricing: models,
    vendors: MOCK_VENDORS,
    group_ratio: { default: 1, vip: 0.8, auto: 1 },
    usable_group: {
      default: { desc: 'Default Group', ratio: 1 },
      vip: { desc: 'VIP Group (20% off)', ratio: 0.8 }
    },
    supported_endpoint: {
      openai: { path: '/v1/chat/completions', method: 'POST' },
      'openai-response': { path: '/v1/responses', method: 'POST' },
      anthropic: { path: '/v1/messages', method: 'POST' },
      gemini: { path: '/v1beta/models/{model}:generateContent', method: 'POST' },
      'image-generation': { path: '/v1/images/generations', method: 'POST' },
      embeddings: { path: '/v1/embeddings', method: 'POST' },
      'openai-video': { path: '/v1/videos/generations', method: 'POST' },
      'jina-rerank': { path: '/v1/rerank', method: 'POST' }
    },
    auto_groups: ['auto']
  }
  return Promise.resolve(data)
}
