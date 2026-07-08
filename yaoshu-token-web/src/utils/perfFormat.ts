import {
  SUCCESS_RATE_HEALTHY,
  SUCCESS_RATE_WARN
} from '@/api/perf-metrics/constants'

/** 成功率语义等级（结构化判定，禁止模糊匹配） */
export type SuccessRateLevel = 'success' | 'warning' | 'danger' | 'unknown'

/** 吞吐格式化：≥1000→"X.XK t/s"，<10→两位小数，≤0/非有限→"—" */
export function formatThroughput(tps: number): string {
  if (!Number.isFinite(tps) || tps <= 0) return '—'
  if (tps >= 1_000) return `${(tps / 1_000).toFixed(1)}K t/s`
  return `${tps.toFixed(tps < 10 ? 2 : 1)} t/s`
}

/** 延迟格式化：≥1000→"X.XXs"，否则"Xms"，≤0/非有限→"—" */
export function formatLatency(ms: number): string {
  if (!Number.isFinite(ms) || ms <= 0) return '—'
  if (ms >= 1_000) return `${(ms / 1_000).toFixed(2)}s`
  return `${Math.round(ms)}ms`
}

/** 成功率格式化："XX.XX%"，非有限→"—" */
export function formatUptimePct(pct: number): string {
  if (!Number.isFinite(pct)) return '—'
  return `${pct.toFixed(2)}%`
}

/**
 * 成功率语义等级判定（对齐）。
 * 返回结构化枚举供组件映射配色，避免散落的条件字符串拼接。
 */
export function getSuccessRateLevel(rate: number): SuccessRateLevel {
  if (!Number.isFinite(rate)) return 'unknown'
  if (rate >= SUCCESS_RATE_HEALTHY) return 'success'
  if (rate >= SUCCESS_RATE_WARN) return 'warning'
  return 'danger'
}
