import type {
  PerfMetricsPayload,
  PerfModelSummary,
  PerfSummaryPayload,
  PerformanceGroup,
  PerformanceSeriesPoint
} from './types'

// Mock 性能指标数据。后端 /api/perf-metrics 就绪后由 USE_MOCK=false 切换为真实接口。
// 数据结构严格对齐 PerfMetricsPayload / PerfSummaryPayload，量级足以驱动图表与表格渲染验证。

// 单模型分组指标：3 个分组 × 8 个时间桶（覆盖 24h），含波动与个别非满分桶触发事故计数
const GROUP_NAMES = ['default', 'vip', 'cache']

// 每分组的基准指标（avg 值），series 在此基础上叠加波动
const GROUP_BASE: Record<
  string,
  { ttft: number; latency: number; rate: number; tps: number }
> = {
  default: { ttft: 420, latency: 1850, rate: 99.95, tps: 48.5 },
  vip: { ttft: 310, latency: 1420, rate: 99.98, tps: 62.3 },
  cache: { ttft: 180, latency: 680, rate: 99.5, tps: 95.8 }
}

function buildSeries(
  base: { ttft: number; latency: number; rate: number; tps: number },
  bucketCount: number
): PerformanceSeriesPoint[] {
  const now = Math.floor(Date.now() / 1000)
  const step = Math.floor((24 * 3600) / bucketCount)
  const points: PerformanceSeriesPoint[] = []
  for (let i = 0; i < bucketCount; i++) {
    // 波动因子：±15% 的正弦波动 + 轻微随机
    const wave = 1 + 0.15 * Math.sin((i / bucketCount) * Math.PI * 2)
    // 第 3 个桶人为压低成功率，触发事故计数（验证可用性趋势图降点）
    const rateDip = i === 3 ? -0.6 : 0
    points.push({
      ts: now - (bucketCount - 1 - i) * step,
      avgTtftMs: Math.round(base.ttft * wave),
      avgLatencyMs: Math.round(base.latency * wave),
      successRate: Math.round((base.rate + rateDip) * 100) / 100,
      avgTps: Math.round(base.tps * wave * 10) / 10
    })
  }
  return points
}

function buildGroups(): PerformanceGroup[] {
  return GROUP_NAMES.map((name) => {
    const base = GROUP_BASE[name]
    const series = buildSeries(base, 8)
    const avg = (field: keyof PerformanceSeriesPoint) => {
      const values = (series as PerformanceSeriesPoint[])
        .map((p) => Number(p[field]))
        .filter((v) => Number.isFinite(v) && v > 0)
      return values.length > 0
        ? Math.round(
            (values.reduce((s, v) => s + v, 0) / values.length) * 100
          ) / 100
        : 0
    }
    return {
      group: name,
      avgTtftMs: avg('avgTtftMs'),
      avgLatencyMs: avg('avgLatencyMs'),
      successRate: avg('successRate'),
      avgTps: avg('avgTps'),
      series
    }
  })
}

export function mockGetPerfMetrics(
  model: string,
  _hours = 24
): Promise<PerfMetricsPayload> {
  const payload: PerfMetricsPayload = {
    modelName: model,
    seriesSchema: '1h_bucket',
    groups: buildGroups()
  }
  return new Promise((resolve) => setTimeout(() => resolve(payload), 300))
}

// 全局汇总：6 个模型按 request_count 降序，成功率覆盖 success/warning/danger 三档
const MOCK_SUMMARY_MODELS: PerfModelSummary[] = [
  { modelName: 'gpt-4o', avgLatencyMs: 1850, successRate: 99.96, avgTps: 48.5, requestCount: 184200 },
  { modelName: 'claude-3-5-sonnet', avgLatencyMs: 1620, successRate: 99.92, avgTps: 52.1, requestCount: 153600 },
  { modelName: 'gemini-2.0-flash', avgLatencyMs: 980, successRate: 99.45, avgTps: 78.3, requestCount: 98000 },
  { modelName: 'deepseek-v3', avgLatencyMs: 1340, successRate: 98.7, avgTps: 65.2, requestCount: 72000 },
  { modelName: 'gpt-4o-mini', avgLatencyMs: 720, successRate: 99.88, avgTps: 92.4, requestCount: 54000 },
  { modelName: 'qwen-max', avgLatencyMs: 1100, successRate: 97.5, avgTps: 58.7, requestCount: 31000 }
]

export function mockGetPerfMetricsSummary(_hours = 24): Promise<PerfSummaryPayload> {
  const payload: PerfSummaryPayload = { models: MOCK_SUMMARY_MODELS }
  return new Promise((resolve) => setTimeout(() => resolve(payload), 300))
}
