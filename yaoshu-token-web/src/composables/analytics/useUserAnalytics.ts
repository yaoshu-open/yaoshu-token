/**
 *
 * 职责：拉取 /api/data/users，前端聚合用户维度消费排行 + 趋势。
 */
import { computed, ref } from 'vue'
import { getQuotaDatesByUsers } from '@/api/dashboard'
import type { DashboardFilters, QuotaDataItem } from '@/api/dashboard/types'
import {
  buildTrend,
  groupAndSum,
  type ChartDataPoint,
  type TrendData,
} from './useModelAnalytics'

export function useUserAnalytics() {
  const loading = ref(false)
  const error = ref<string | null>(null)
  const rawData = ref<QuotaDataItem[]>([])

  async function fetch(filters: DashboardFilters) {
    loading.value = true
    error.value = null
    try {
      const params = {
        start_timestamp: filters.start_timestamp ?? 0,
        end_timestamp: filters.end_timestamp ?? Math.floor(Date.now() / 1000),
        default_time: filters.time_granularity ?? 'day',
        ...(filters.username ? { username: filters.username } : {}),
      }
      rawData.value = await getQuotaDatesByUsers(params)
    } catch (e) {
      rawData.value = []
      error.value = e instanceof Error ? e.message : '加载失败'
    } finally {
      loading.value = false
    }
  }

  /** 用户消费排行 Top 10（quota 维度，柱状图） */
  const userRank = computed<ChartDataPoint[]>(() => {
    return groupAndSum(
      rawData.value,
      (i) => i.username ?? 'Unknown',
      (i) => i.quota,
    )
      .sort((a, b) => b.value - a.value)
      .slice(0, 10)
  })

  /** 用户消费趋势（quota 维度 × 时间，折线/面积图） */
  function buildUserTrend(filters: DashboardFilters, topN = 5): TrendData {
    return buildTrend(
      rawData.value,
      filters,
      (i) => i.username ?? 'Unknown',
      (i) => i.quota,
      topN,
    )
  }

  return {
    loading,
    error,
    rawData: computed(() => rawData.value),
    fetch,
    userRank,
    buildUserTrend,
  }
}
