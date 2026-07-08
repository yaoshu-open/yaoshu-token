/**
 * 性能指标 API Service。
 * 后端契约：ai-docs/后端设计/API_Contract/契约_公共与系统.md §二
 * GET /api/perf-metrics + GET /api/perf-metrics/summary。
 *
 * Mock 闭环：DEV + VITE_PERF_METRICS_MOCK=true 时切换 mock 实现（动态 import 避免污染
 * production build），与 rankings 模式一致。
 */
import { request } from '@/utils/request'
import { DEFAULT_HOURS, ENDPOINTS, USE_MOCK } from './constants'
import type { PerfMetricsPayload, PerfSummaryPayload } from './types'

/** 单模型性能指标（model 必填，默认 24h 窗口） */
export function getPerfMetrics(
  model: string,
  hours = DEFAULT_HOURS
): Promise<PerfMetricsPayload> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGetPerfMetrics(model, hours))
  }
  return request.get<PerfMetricsPayload>(ENDPOINTS.METRICS, {
    params: { model, hours }
  })
}

/** 全局性能汇总（默认 24h 窗口） */
export function getPerfMetricsSummary(
  hours = DEFAULT_HOURS
): Promise<PerfSummaryPayload> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGetPerfMetricsSummary(hours))
  }
  return request.get<PerfSummaryPayload>(ENDPOINTS.SUMMARY, {
    params: { hours }
  })
}
