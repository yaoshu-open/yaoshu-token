/** 后端端点（契约_公共与系统 §二） */
export const ENDPOINTS = {
  METRICS: '/api/perf-metrics',
  SUMMARY: '/api/perf-metrics/summary'
} as const

/** Mock 闭环开关：后端未就绪时 DEV 环境启用 */
export const USE_MOCK = import.meta.env.VITE_PERF_METRICS_MOCK === 'true'

/** 默认查询时间窗口（小时） */
export const DEFAULT_HOURS = 24
/** 排行榜 Top 模型数量上限 */
export const TOP_MODEL_LIMIT = 5
/** 成功率健康阈值（%） */
export const SUCCESS_RATE_HEALTHY = 99.9
/** 成功率警告阈值（%） */
export const SUCCESS_RATE_WARN = 99
