import type {
  ModelHistorySeries,
  ModelRanking,
  RankingMover,
  RankingPeriod,
  RankingsSnapshot,
  VendorRanking,
  VendorShareSeries
} from './types'

// Mock 排行榜数据。后端 /api/rankings 就绪后由 USE_MOCK=false 切换为真实接口。
// 数据结构严格对齐 RankingsSnapshot，量级足以驱动图表与列表渲染验证。

const MOCK_MODELS: ModelRanking[] = [
  { rank: 1, previousRank: 2, modelName: 'gpt-4o', vendor: 'OpenAI', vendorIcon: 'openai', category: 'multimodal', totalTokens: 1_842_000_000, share: 0.28, growthPct: 42.3 },
  { rank: 2, previousRank: 1, modelName: 'claude-3-5-sonnet', vendor: 'Anthropic', vendorIcon: 'anthropic', category: 'programming', totalTokens: 1_536_000_000, share: 0.23, growthPct: 18.7 },
  { rank: 3, previousRank: 3, modelName: 'gemini-2.0-flash', vendor: 'Google', vendorIcon: 'google', category: 'multimodal', totalTokens: 980_000_000, share: 0.15, growthPct: 65.1 },
  { rank: 4, previousRank: 6, modelName: 'deepseek-v3', vendor: 'DeepSeek', vendorIcon: 'deepseek', category: 'programming', totalTokens: 720_000_000, share: 0.11, growthPct: 128.4 },
  { rank: 5, previousRank: 4, modelName: 'gpt-4o-mini', vendor: 'OpenAI', vendorIcon: 'openai', category: 'productivity', totalTokens: 540_000_000, share: 0.08, growthPct: -12.5 },
  { rank: 6, previousRank: 5, modelName: 'claude-3-opus', vendor: 'Anthropic', vendorIcon: 'anthropic', category: 'roleplay', totalTokens: 420_000_000, share: 0.06, growthPct: -8.2 },
  { rank: 7, previousRank: 8, modelName: 'qwen-max', vendor: 'Alibaba', vendorIcon: 'alibaba', category: 'multimodal', totalTokens: 310_000_000, share: 0.05, growthPct: 34.6 },
  { rank: 8, previousRank: 7, modelName: 'mistral-large', vendor: 'Mistral', vendorIcon: 'mistral', category: 'science', totalTokens: 180_000_000, share: 0.03, growthPct: 5.4 },
  { rank: 9, modelName: 'glm-4-plus', vendor: 'Zhipu', vendorIcon: 'zhipu', category: 'programming', totalTokens: 95_000_000, share: 0.014, growthPct: 22.1 },
  { rank: 10, previousRank: 9, modelName: 'llama-3.3-70b', vendor: 'Meta', vendorIcon: 'meta', category: 'productivity', totalTokens: 67_000_000, share: 0.01, growthPct: -3.8 }
]

const MOCK_VENDORS: VendorRanking[] = [
  { rank: 1, vendor: 'OpenAI', vendorIcon: 'openai', totalTokens: 2_382_000_000, share: 0.36, growthPct: 28.4, modelsCount: 2, topModel: 'gpt-4o' },
  { rank: 2, vendor: 'Anthropic', vendorIcon: 'anthropic', totalTokens: 1_956_000_000, share: 0.29, growthPct: 15.2, modelsCount: 2, topModel: 'claude-3-5-sonnet' },
  { rank: 3, vendor: 'Google', vendorIcon: 'google', totalTokens: 980_000_000, share: 0.15, growthPct: 65.1, modelsCount: 1, topModel: 'gemini-2.0-flash' },
  { rank: 4, vendor: 'DeepSeek', vendorIcon: 'deepseek', totalTokens: 720_000_000, share: 0.11, growthPct: 128.4, modelsCount: 1, topModel: 'deepseek-v3' },
  { rank: 5, vendor: 'Alibaba', vendorIcon: 'alibaba', totalTokens: 310_000_000, share: 0.047, growthPct: 34.6, modelsCount: 1, topModel: 'qwen-max' },
  { rank: 6, vendor: 'Mistral', vendorIcon: 'mistral', totalTokens: 180_000_000, share: 0.027, growthPct: 5.4, modelsCount: 1, topModel: 'mistral-large' },
  { rank: 7, vendor: 'Zhipu', vendorIcon: 'zhipu', totalTokens: 95_000_000, share: 0.014, growthPct: 22.1, modelsCount: 1, topModel: 'glm-4-plus' },
  { rank: 8, vendor: 'Meta', vendorIcon: 'meta', totalTokens: 67_000_000, share: 0.01, growthPct: -3.8, modelsCount: 1, topModel: 'llama-3.3-70b' }
]

const MOCK_MOVERS: RankingMover[] = [
  { modelName: 'deepseek-v3', vendor: 'DeepSeek', vendorIcon: 'deepseek', rankDelta: 2, currentRank: 4, growthPct: 128.4 },
  { modelName: 'gemini-2.0-flash', vendor: 'Google', vendorIcon: 'google', rankDelta: 1, currentRank: 3, growthPct: 65.1 },
  { modelName: 'qwen-max', vendor: 'Alibaba', vendorIcon: 'alibaba', rankDelta: 1, currentRank: 7, growthPct: 34.6 }
]

const MOCK_DROPPERS: RankingMover[] = [
  { modelName: 'gpt-4o-mini', vendor: 'OpenAI', vendorIcon: 'openai', rankDelta: -1, currentRank: 5, growthPct: -12.5 },
  { modelName: 'claude-3-opus', vendor: 'Anthropic', vendorIcon: 'anthropic', rankDelta: -1, currentRank: 6, growthPct: -8.2 },
  { modelName: 'llama-3.3-70b', vendor: 'Meta', vendorIcon: 'meta', rankDelta: -1, currentRank: 10, growthPct: -3.8 }
]

// 模型用量历史（堆叠柱状图）：8 个时间桶 × 5 个头部模型
const BUCKET_LABELS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun', 'Mon+']
const HISTORY_MODELS = ['gpt-4o', 'claude-3-5-sonnet', 'gemini-2.0-flash', 'deepseek-v3', 'gpt-4o-mini']
const HISTORY_VENDORS = ['OpenAI', 'Anthropic', 'Google', 'DeepSeek', 'OpenAI']
// 每个模型在 8 个桶的 Token 量（百万级，模拟波动趋势）
const HISTORY_TOKENS: Record<string, number[]> = {
  'gpt-4o': [210, 235, 248, 260, 272, 258, 280, 295],
  'claude-3-5-sonnet': [180, 192, 205, 198, 215, 220, 228, 235],
  'gemini-2.0-flash': [85, 102, 118, 130, 142, 150, 165, 178],
  'deepseek-v3': [45, 62, 78, 95, 108, 120, 138, 152],
  'gpt-4o-mini': [82, 78, 75, 72, 70, 68, 66, 65]
}

function buildModelsHistory(): ModelHistorySeries {
  const points = HISTORY_MODELS.flatMap((model, mi) =>
    BUCKET_LABELS.map((label, bi) => ({
      ts: `2025-W${bi + 1}`,
      label,
      model,
      vendor: HISTORY_VENDORS[mi],
      tokens: HISTORY_TOKENS[model][bi] * 1_000_000
    }))
  )
  const models = HISTORY_MODELS.map((name, mi) => ({
    name,
    vendor: HISTORY_VENDORS[mi],
    total: HISTORY_TOKENS[name].reduce((s, v) => s + v, 0) * 1_000_000
  })).sort((a, b) => b.total - a.total)
  return { points, models, buckets: BUCKET_LABELS.length }
}

// 供应商市场份额历史（100% 堆叠图）：8 个时间桶 × 5 个供应商，share 归一化
const SHARE_VENDORS = ['OpenAI', 'Anthropic', 'Google', 'DeepSeek', 'Others']
// 每个供应商在 8 个桶的原始份额（未归一化，构建时归一化）
const SHARE_RAW: Record<string, number[]> = {
  OpenAI: [0.42, 0.40, 0.38, 0.37, 0.36, 0.35, 0.34, 0.33],
  Anthropic: [0.30, 0.30, 0.29, 0.29, 0.28, 0.28, 0.27, 0.27],
  Google: [0.10, 0.11, 0.12, 0.13, 0.14, 0.15, 0.16, 0.17],
  DeepSeek: [0.05, 0.06, 0.08, 0.09, 0.11, 0.12, 0.14, 0.15],
  Others: [0.13, 0.13, 0.13, 0.12, 0.11, 0.10, 0.09, 0.08]
}

function buildVendorShareHistory(): VendorShareSeries {
  const points = SHARE_VENDORS.flatMap((vendor) =>
    BUCKET_LABELS.map((label, bi) => ({
      ts: `2025-W${bi + 1}`,
      label,
      vendor,
      share: SHARE_RAW[vendor][bi],
      tokens: Math.round(SHARE_RAW[vendor][bi] * 6_600_000_000)
    }))
  )
  const vendors = SHARE_VENDORS.map((name) => {
    const total = SHARE_RAW[name].reduce((s, v) => s + v * 6_600_000_000, 0)
    return {
      name,
      total,
      share: total / (8 * 6_600_000_000)
    }
  }).sort((a, b) => b.total - a.total)
  return { points, vendors, buckets: BUCKET_LABELS.length }
}

export function mockGetRankings(_period: RankingPeriod): Promise<RankingsSnapshot> {
  const snapshot: RankingsSnapshot = {
    models: MOCK_MODELS,
    vendors: MOCK_VENDORS,
    topMovers: MOCK_MOVERS,
    topDroppers: MOCK_DROPPERS,
    modelsHistory: buildModelsHistory(),
    vendorShareHistory: buildVendorShareHistory()
  }
  // 模拟网络延迟
  return new Promise((resolve) => setTimeout(() => resolve(snapshot), 300))
}
