
/** 配额趋势数据点（按时间桶聚合） */
export interface QuotaDate {
  /** 时间桶起始时间（秒级时间戳） */
  timestamp: number
  /** 该时段内 quota 消耗 */
  quota: number
  /** 该时段内 token 用量（prompt + completion） */
  tokens?: number
  /** 该时段内请求数 */
  requests?: number
}

/** 时间桶粒度（对齐 default ） */
export type TimeGranularity = 'hour' | 'day' | 'week'

/** 获取配额趋势参数 */
export interface GetQuotaDatesParams {
  /** 起始时间（秒级时间戳） */
  start_timestamp: number
  /** 结束时间（秒级时间戳） */
  end_timestamp: number
  /** 时间桶粒度：hour（近 24h）/ day（近 30d）/ week（周聚合） */
  default_time: TimeGranularity
}

/** 用户可用模型列表项 */
export interface UserModel {
  model: string
  /** 模型显示名（若有映射） */
  modelName?: string
}

/**
 * 后端 /api/data* 返回的原始聚合行。
 * 字段为 camelCase，与后端 Jackson 默认序列化对齐（实测 /api/data/self 返回 modelName/userId/tokenUsed）。
 */
export interface QuotaDataItem {
  id?: number
  userId?: number
  username?: string
  modelName?: string
  /** 秒级时间戳 */
  createdAt: number
  tokenUsed?: number
  /** 请求数 */
  count?: number
  /** 费用（配额分） */
  quota?: number
}

/** 分析板块查询参数 */
export interface AnalyticsParams {
  /** 起始时间（秒级时间戳） */
  start_timestamp: number
  /** 结束时间（秒级时间戳） */
  end_timestamp: number
  /** 时间桶粒度 */
  default_time?: TimeGranularity
  /** 按用户名过滤（admin 端） */
  username?: string
}

// ============================================================================
// 调用统计看板偏好与筛选（对齐 default DashboardChartPreferences / DashboardFilters）
// ============================================================================

/** 消费分布图默认类型 */
export type ConsumptionDistributionChartType = 'bar' | 'area'

/** 模型分析图默认 tab */
export type ModelAnalyticsChartTab = 'trend' | 'proportion' | 'top'

/** 调用统计看板偏好（localStorage 持久化） */
export interface DashboardChartPreferences {
  /** 默认消费分布图类型 */
  consumptionDistributionChart: ConsumptionDistributionChartType
  /** 默认模型分析图 tab */
  modelAnalyticsChart: ModelAnalyticsChartTab
  /** 默认时间范围（天）：1 / 7 / 14 / 29 */
  defaultTimeRangeDays: number
  /** 默认时间粒度 */
  defaultTimeGranularity: TimeGranularity
}

/** 调用统计看板筛选条件 */
export interface DashboardFilters {
  /** 起始时间（秒级时间戳） */
  start_timestamp?: number
  /** 结束时间（秒级时间戳） */
  end_timestamp?: number
  /** 时间粒度（单次筛选覆盖偏好） */
  time_granularity?: TimeGranularity
  /** 按用户名过滤（admin 端） */
  username?: string
}
