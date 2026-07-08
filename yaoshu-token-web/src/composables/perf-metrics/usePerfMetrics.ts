import { ref, watch, type Ref } from 'vue'
import { getPerfMetrics } from '@/api/perf-metrics'
import { DEFAULT_HOURS } from '@/api/perf-metrics/constants'
import type { PerfMetricsPayload } from '@/api/perf-metrics/types'

const STALE_TIME = 60 * 1000

/**
 * 单模型性能指标数据获取。
 * 接收 model 响应式引用，model 变更时自动重新拉取。
 * 60s staleTime 缓存窗口（对齐 default ）。
 *
 */
export function usePerfMetrics(model: Ref<string>, hours = DEFAULT_HOURS) {
  const data = ref<PerfMetricsPayload | null>(null)
  const loading = ref(false)
  const error = ref<Error | null>(null)

  let lastModel: string | null = null
  let lastFetchAt = 0

  async function load(force = false) {
    // 空模型名不发起请求（等待用户/pricing 传入有效值）
    if (!model.value) {
      data.value = null
      loading.value = false
      return
    }
    const now = Date.now()
    if (
      !force &&
      lastModel === model.value &&
      data.value &&
      now - lastFetchAt < STALE_TIME
    ) {
      return
    }
    loading.value = true
    error.value = null
    try {
      data.value = await getPerfMetrics(model.value, hours)
      lastModel = model.value
      lastFetchAt = Date.now()
    } catch (e) {
      error.value = e as Error
    } finally {
      loading.value = false
    }
  }

  // model 变更触发强制刷新（切换模型必须拉新数据）
  watch(model, () => load(true), { immediate: true })

  return { data, loading, error, reload: () => load(true) }
}
