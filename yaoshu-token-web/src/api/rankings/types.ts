// 排行榜类型定义。对齐后端契约 GET /api/rankings 响应结构（契约_公共与系统 §二）。

/** 排行榜时间窗口 */
export type RankingPeriod = 'today' | 'week' | 'month' | 'year' | 'all'

/** 模型分类标识（后端 pricing metadata 关联） */
export type RankingCategoryId =
  | 'all'
  | 'programming'
  | 'roleplay'
  | 'marketing'
  | 'translation'
  | 'science'
  | 'finance'
  | 'health'
  | 'legal'
  | 'education'
  | 'productivity'
  | 'multimodal'

/** 单个模型的排名行 */
export interface ModelRanking {
  rank: number
  /** 上期同窗口排名；缺省表示新上榜 */
  previousRank?: number
  modelName: string
  vendor: string
  vendorIcon?: string
  category: RankingCategoryId
  /** 周期内经此模型路由的 Token 总量 */
  totalTokens: number
  /** 占所有 Token 的份额（0..1） */
  share: number
  /** 周期环比 Token 增长率（%） */
  growthPct: number
}

/** 供应商维度的排名行 */
export interface VendorRanking {
  rank: number
  vendor: string
  vendorIcon?: string
  totalTokens: number
  share: number
  growthPct: number
  /** 该供应商有流量的不同模型数 */
  modelsCount: number
  /** 周期内该供应商流量最高的模型 */
  topModel: string
}

/** 排名涨跌行 */
export interface RankingMover {
  modelName: string
  vendor: string
  vendorIcon?: string
  /** 正=上升，负=下降 */
  rankDelta: number
  currentRank: number
  /** Token 量变化百分比 */
  growthPct: number
}

/** 模型 Token 用量的单时间桶样本（扁平结构，喂给堆叠柱状图） */
export interface ModelHistoryPoint {
  ts: string
  /** 预格式化 x 轴标签（如 "May 5"、"12:00"） */
  label: string
  model: string
  vendor: string
  tokens: number
}

/** 模型用量历史序列 */
export interface ModelHistorySeries {
  /** 扁平样本，按时间从旧到新排序 */
  points: ModelHistoryPoint[]
  /** 出现在序列中的模型，按 Token 总量降序 */
  models: Array<{ name: string; vendor: string; total: number }>
  /** 时间桶数量（用于轴刻度密度） */
  buckets: number
}

/** 供应商市场份额的单时间桶样本（share 在同一 ts 内归一化为 1.0） */
export interface VendorSharePoint {
  ts: string
  label: string
  vendor: string
  share: number
  tokens: number
}

/** 供应商市场份额序列 */
export interface VendorShareSeries {
  points: VendorSharePoint[]
  vendors: Array<{ name: string; total: number; share: number }>
  buckets: number
}

/** GET /api/rankings 完整响应快照 */
export interface RankingsSnapshot {
  models: ModelRanking[]
  vendors: VendorRanking[]
  /** 周期内排名上升最大的模型 */
  topMovers: RankingMover[]
  /** 周期内排名下降最大的模型 */
  topDroppers: RankingMover[]
  /** 模型 Token 用量堆叠柱状图历史 */
  modelsHistory?: ModelHistorySeries
  /** 供应商市场份额 100% 堆叠图历史 */
  vendorShareHistory?: VendorShareSeries
}
