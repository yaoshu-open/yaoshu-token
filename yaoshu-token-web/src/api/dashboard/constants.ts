import type { DashboardChartPreferences, TimeGranularity } from './types'

/** Dashboard 模块 API 端点常量。 */
export const DASHBOARD_ENDPOINTS = {
  /** 用户配额趋势（近 24h/30d） */
  USER_QUOTA_DATES: '/api/data/self',
  /** admin 全量配额数据（按 model_name + created_at 聚合） */
  ADMIN_QUOTA_DATES: '/api/data',
  /** admin 按用户聚合的配额数据 */
  USERS_QUOTA_DATES: '/api/data/users',
} as const

/** 默认 sparkline 时间窗口：近 24 小时 */
export const DEFAULT_SPARKLINE_HOURS = 24

/** 默认 sparkline 桶数量（12 桶聚合） */
export const SPARKLINE_BUCKETS = 12

/** 分析板块默认时间范围（天） */
export const DEFAULT_ANALYTICS_DAYS = 7

// ============================================================================
// ============================================================================

/** 偏好持久化 localStorage key */
export const DASHBOARD_CHART_PREFERENCES_STORAGE_KEY = 'analytics_chart_preferences'

/** 默认时间粒度（Vue3 默认 day，对齐 B 端周/月统计高频场景） */
export const DEFAULT_TIME_GRANULARITY: TimeGranularity = 'day'

/** 各时间粒度对应的默认时间范围（天） */
export const TIME_RANGE_BY_GRANULARITY: Record<TimeGranularity, number> = {
  hour: 1,
  day: 7,
  week: 30,
} as const

/** 时间粒度选项 */
export const TIME_GRANULARITY_OPTIONS: ReadonlyArray<{ label: string; value: TimeGranularity }> = [
  { label: 'analytics.granularity.hour', value: 'hour' },
  { label: 'analytics.granularity.day', value: 'day' },
  { label: 'analytics.granularity.week', value: 'week' },
]

/** 时间范围预设（天） */
export const TIME_RANGE_PRESETS: ReadonlyArray<{ label: string; days: number }> = [
  { label: 'analytics.range.days1', days: 1 },
  { label: 'analytics.range.days7', days: 7 },
  { label: 'analytics.range.days14', days: 14 },
  { label: 'analytics.range.days29', days: 29 },
]

/** 消费分布图类型选项 */
export const CONSUMPTION_DISTRIBUTION_CHART_OPTIONS: ReadonlyArray<{
  value: 'bar' | 'area'
  labelKey: string
}> = [
  { value: 'bar', labelKey: 'analytics.preferences.bar' },
  { value: 'area', labelKey: 'analytics.preferences.area' },
]

/** 模型分析图 tab 选项 */
export const MODEL_ANALYTICS_CHART_OPTIONS: ReadonlyArray<{
  value: 'trend' | 'proportion' | 'top'
  labelKey: string
}> = [
  { value: 'trend', labelKey: 'analytics.preferences.trend' },
  { value: 'proportion', labelKey: 'analytics.preferences.proportion' },
  { value: 'top', labelKey: 'analytics.preferences.top' },
]

/** 默认图表偏好 */
export const DEFAULT_DASHBOARD_CHART_PREFERENCES: DashboardChartPreferences = {
  consumptionDistributionChart: 'bar',
  modelAnalyticsChart: 'trend',
  defaultTimeRangeDays: 7,
  defaultTimeGranularity: DEFAULT_TIME_GRANULARITY,
}

/** 空筛选条件 */
export const EMPTY_DASHBOARD_FILTERS = {
  start_timestamp: undefined,
  end_timestamp: undefined,
  time_granularity: DEFAULT_TIME_GRANULARITY,
  username: '',
} as const
