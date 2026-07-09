/**
 *
 * 职责：拉取 /api/data（admin）或 /api/data/self（user），前端聚合多维度统计。
 * 维度分工（对齐原版）：
 *   - quota 维度 → ConsumptionDistributionChart
 *   - count 维度 → ModelCharts 三 tab（trend/proportion/top）
 *   - tokens 维度 → StatCards 总览数字
 */
import { computed, ref } from 'vue'
import dayjs from 'dayjs'
import { getAdminQuotaDates, getSelfAnalytics } from '@/api/dashboard'
import { useUserPermissions } from '@/composables/useUserPermissions'
import type {
  DashboardFilters,
  QuotaDataItem,
  TimeGranularity,
} from '@/api/dashboard/types'

/** 统计卡片聚合结果 */
export interface ModelStatCards {
  totalQuota: number
  totalCount: number
  totalTokens: number
  /** 每分钟请求数 */
  rpm: number
  /** 每分钟 Token */
  tpm: number
}

/** 图表通用数据点 */
export interface ChartDataPoint {
  name: string
  value: number
}

/** 趋势数据：按时间 × 维度的矩阵 */
export interface TrendData {
  times: string[]
  series: Array<{ name: string; data: number[] }>
}

/** 时间粒度对应的桶内时间格式 */
function bucketFormat(granularity: TimeGranularity): string {
  if (granularity === 'hour') return 'MM-DD HH:mm'
  if (granularity === 'week') return 'YYYY-[W]w'
  return 'MM-DD'
}

/** 时间粒度对应的桶起始计算 */
function bucketStart(ts: number, granularity: TimeGranularity): number {
  const d = dayjs.unix(ts)
  if (granularity === 'hour') return d.startOf('hour').unix()
  if (granularity === 'week') return d.startOf('day').unix() // 周内按天聚合，桶标签按周
  return d.startOf('day').unix()
}

/** 时间粒度对应的桶步长（秒） */
function bucketStep(granularity: TimeGranularity): number {
  if (granularity === 'hour') return 3600
  if (granularity === 'week') return 86400
  return 86400
}

export function useModelAnalytics() {
  const { isAdmin } = useUserPermissions()
  const loading = ref(false)
  const error = ref<string | null>(null)
  const rawData = ref<QuotaDataItem[]>([])

  async function fetch(filters: DashboardFilters) {
    loading.value = true
    error.value = null
    try {
      const granularity: TimeGranularity = filters.time_granularity ?? 'day'
      const params = {
        start_timestamp: filters.start_timestamp ?? 0,
        end_timestamp: filters.end_timestamp ?? Math.floor(Date.now() / 1000),
        default_time: granularity,
        ...(filters.username ? { username: filters.username } : {}),
      }
      rawData.value = isAdmin.value
        ? await getAdminQuotaDates(params)
        : await getSelfAnalytics(params)
    } catch (e) {
      rawData.value = []
      error.value = e instanceof Error ? e.message : '加载失败'
    } finally {
      loading.value = false
    }
  }

  /** 时间跨度（秒），用于计算 RPM/TPM */
  function timeSpanSeconds(filters: DashboardFilters): number {
    const start = filters.start_timestamp ?? 0
    const end = filters.end_timestamp ?? Math.floor(Date.now() / 1000)
    return Math.max(60, end - start)
  }

  /** StatCards 聚合（quota/count/tokens + RPM/TPM） */
  function buildStatCards(filters: DashboardFilters): ModelStatCards {
    const totalQuota = sumBy(rawData.value, (i) => i.quota)
    const totalCount = sumBy(rawData.value, (i) => i.count)
    const totalTokens = sumBy(rawData.value, (i) => i.tokenUsed)
    const minutes = Math.max(1, timeSpanSeconds(filters) / 60)
    return {
      totalQuota,
      totalCount,
      totalTokens,
      rpm: Math.round((totalCount / minutes) * 10) / 10,
      tpm: Math.round((totalTokens / minutes) * 10) / 10,
    }
  }

  /** 模型消费占比（quota 维度，饼图/堆叠柱图数据源） */
  function buildQuotaDistribution(): ChartDataPoint[] {
    return groupAndSum(rawData.value, (i) => i.modelName ?? 'Unknown', (i) => i.quota).sort(
      (a, b) => b.value - a.value,
    )
  }

  /** 模型调用次数占比（count 维度，饼图数据源，对齐原版 spec_pie） */
  function buildCountProportion(): ChartDataPoint[] {
    return groupAndSum(rawData.value, (i) => i.modelName ?? 'Unknown', (i) => i.count).sort(
      (a, b) => b.value - a.value,
    )
  }

  /** 模型消费趋势（quota 维度 × 时间，折线/面积/堆叠柱数据源） */
  function buildQuotaTrend(filters: DashboardFilters, topN = 10): TrendData {
    return buildTrend(
      rawData.value,
      filters,
      (i) => i.modelName ?? 'Unknown',
      (i) => i.quota,
      topN,
    )
  }

  /** 模型调用次数趋势（count 维度 × 时间，对齐原版 spec_model_line） */
  function buildCountTrend(filters: DashboardFilters, topN = 10): TrendData {
    return buildTrend(
      rawData.value,
      filters,
      (i) => i.modelName ?? 'Unknown',
      (i) => i.count,
      topN,
    )
  }

  /** 模型调用次数排行（count 维度，对齐原版 spec_rank_bar） */
  function buildCountRank(topN = 20): ChartDataPoint[] {
    return buildCountProportion().slice(0, topN)
  }

  return {
    loading,
    error,
    rawData: computed(() => rawData.value),
    fetch,
    buildStatCards,
    buildQuotaDistribution,
    buildCountProportion,
    buildQuotaTrend,
    buildCountTrend,
    buildCountRank,
  }
}

// ============================================================================
// 聚合工具（也供 useUserAnalytics 复用）
// ============================================================================

export function sumBy<T>(arr: T[], selector: (item: T) => number | undefined | null): number {
  return arr.reduce((sum, item) => sum + (Number(selector(item)) || 0), 0)
}

export function groupAndSum<T>(
  arr: T[],
  keyFn: (item: T) => string,
  valueFn: (item: T) => number | undefined | null,
): ChartDataPoint[] {
  const map = new Map<string, number>()
  for (const item of arr) {
    const key = keyFn(item)
    map.set(key, (map.get(key) ?? 0) + (Number(valueFn(item)) || 0))
  }
  return Array.from(map, ([name, value]) => ({ name, value }))
}

/**
 * 构建趋势数据：按时间粒度分桶 × 维度分组，取 Top N 维度。
 */
export function buildTrend(
  arr: QuotaDataItem[],
  filters: DashboardFilters,
  keyFn: (item: QuotaDataItem) => string,
  valueFn: (item: QuotaDataItem) => number | undefined | null,
  topN: number,
): TrendData {
  const granularity: TimeGranularity = filters.time_granularity ?? 'day'
  const start = filters.start_timestamp
  const end = filters.end_timestamp ?? Math.floor(Date.now() / 1000)
  if (!start) return { times: [], series: [] }

  const step = bucketStep(granularity)
  const fmt = bucketFormat(granularity)
  const times: string[] = []
  const buckets: number[] = []
  for (let ts = start; ts <= end; ts += step) {
    const bStart = bucketStart(ts, granularity)
    times.push(dayjs.unix(bStart).format(fmt))
    buckets.push(bStart)
  }

  // 桶去重（同一桶起始只保留一个）
  const uniqueTimes: string[] = []
  const uniqueBuckets: number[] = []
  const seen = new Set<number>()
  buckets.forEach((b, i) => {
    if (!seen.has(b)) {
      seen.add(b)
      uniqueTimes.push(times[i])
      uniqueBuckets.push(b)
    }
  })

  // 按维度 × 桶聚合
  const dimensionTotals = new Map<string, number>()
  const dimensionBuckets = new Map<string, Map<number, number>>()

  for (const item of arr) {
    const key = keyFn(item)
    const value = Number(valueFn(item)) || 0
    if (!item.createdAt) continue
    const bStart = bucketStart(item.createdAt, granularity)
    dimensionTotals.set(key, (dimensionTotals.get(key) ?? 0) + value)
    if (!dimensionBuckets.has(key)) dimensionBuckets.set(key, new Map())
    dimensionBuckets
      .get(key)!
      .set(bStart, (dimensionBuckets.get(key)!.get(bStart) ?? 0) + value)
  }

  // 取 Top N 维度
  const topDimensions = Array.from(dimensionTotals.entries())
    .sort((a, b) => b[1] - a[1])
    .slice(0, topN)
    .map(([name]) => name)

  const series = topDimensions.map((name) => ({
    name,
    data: uniqueBuckets.map(
      (bucket) => dimensionBuckets.get(name)?.get(bucket) ?? 0,
    ),
  }))

  return { times: uniqueTimes, series }
}
