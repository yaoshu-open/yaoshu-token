// 性能指标类型定义。对齐后端契约 GET /api/perf-metrics / GET /api/perf-metrics/summary
// 注意：request 拦截器已解包 {success,message,data}，此处为解包后的内层载荷。

/** 单时间桶的性能样本（Unix 秒时间戳） */
export interface PerformanceSeriesPoint {
  /** 聚合时间桶（Unix 秒） */
  ts: number
  /** 首 Token 平均耗时（毫秒） */
  avgTtftMs: number
  /** 平均延迟（毫秒） */
  avgLatencyMs: number
  /** 成功率（0..100） */
  successRate: number
  /** 平均吞吐（tokens/秒） */
  avgTps: number
}

/** 单个计费分组的聚合指标 + 时序 */
export interface PerformanceGroup {
  /** 计费分组名 */
  group: string
  avgTtftMs: number
  avgLatencyMs: number
  successRate: number
  avgTps: number
  /** 分组内的时间桶序列 */
  series: PerformanceSeriesPoint[]
}

/** GET /api/perf-metrics 解包载荷（单模型） */
export interface PerfMetricsPayload {
  modelName: string
  seriesSchema?: string
  groups: PerformanceGroup[]
}

/** 全局汇总中的单模型摘要行 */
export interface PerfModelSummary {
  modelName: string
  avgLatencyMs: number
  successRate: number
  avgTps: number
  requestCount?: number
}

/** GET /api/perf-metrics/summary 解包载荷（全局汇总） */
export interface PerfSummaryPayload {
  /** 按 request_count 降序排列 */
  models: PerfModelSummary[]
}
