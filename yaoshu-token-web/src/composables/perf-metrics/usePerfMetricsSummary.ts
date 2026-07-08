import { ref } from 'vue'
import { getPerfMetricsSummary } from '@/api/perf-metrics'
import { DEFAULT_HOURS } from '@/api/perf-metrics/constants'
import type { PerfSummaryPayload } from '@/api/perf-metrics/types'

const STALE_TIME = 60 * 1000

/**
 * 全局性能汇总数据获取。
 * 挂载时自动拉取，60s staleTime 缓存窗口（对齐 default ）。
 *
 */
export function usePerfMetricsSummary(hours = DEFAULT_HOURS) {
  const data = ref<PerfSummaryPayload | null>(null)
  const loading = ref(true)
  const error = ref<Error | null>(null)

  let lastFetchAt = 0

  async function load(force = false) {
    const now = Date.now()
    if (!force && data.value && now - lastFetchAt < STALE_TIME) {
      return
    }
    loading.value = true
    error.value = null
    try {
      data.value = await getPerfMetricsSummary(hours)
      lastFetchAt = Date.now()
    } catch (e) {
      error.value = e as Error
    } finally {
      loading.value = false
    }
  }

  load()

  return { data, loading, error, reload: () => load(true) }
}
