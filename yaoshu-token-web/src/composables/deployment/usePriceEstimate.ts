/**
 * 价格估算 composable。
 *
 * 设计：防抖 400ms（复用 PRICE_ESTIMATE_DEBOUNCE）+ 重叠请求防护（lastRequestId）。
 * 依赖：hardware_id + gpus_per_container + replica_count + location_ids + duration_hours。
 */
import { ref, watch, type Ref } from 'vue'
import { estimatePrice } from '@/api/deployment'
import { PRICE_ESTIMATE_DEBOUNCE } from '@/api/deployment/constants'
import type { PriceEstimation } from '@/api/deployment/types'

interface PriceEstimateDeps {
  hardware_id: number | null
  gpus_per_container: number
  replica_count: number
  location_ids: number[]
  duration_hours: number
}

export function usePriceEstimate(deps: Ref<PriceEstimateDeps>, enabled: Ref<boolean>) {
  const estimate = ref<PriceEstimation | null>(null)
  const loading = ref(false)
  let timer: ReturnType<typeof setTimeout> | null = null
  let lastRequestId = 0

  async function run(): Promise<void> {
    const d = deps.value
    if (!enabled.value || !d.hardware_id || d.location_ids.length === 0) {
      estimate.value = null
      return
    }
    loading.value = true
    const currentRequestId = ++lastRequestId
    try {
      const res = await estimatePrice({
        hardware_id: d.hardware_id,
        gpus_per_container: d.gpus_per_container,
        replica_count: d.replica_count,
        location_ids: d.location_ids,
        duration_hours: d.duration_hours
      })
      if (currentRequestId !== lastRequestId) return
      estimate.value = res
    } catch {
      if (currentRequestId !== lastRequestId) return
      estimate.value = null
    } finally {
      if (currentRequestId === lastRequestId) {
        loading.value = false
      }
    }
  }

  watch(
    deps,
    () => {
      if (timer) clearTimeout(timer)
      timer = setTimeout(() => {
        void run()
      }, PRICE_ESTIMATE_DEBOUNCE)
    },
    { deep: true }
  )

  return {
    estimate,
    loading
  }
}
