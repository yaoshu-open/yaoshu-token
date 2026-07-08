/**
 * 可用副本查询 composable。
 *
 * 设计：依赖 hardware_id + gpus_per_container，watch 触发查询 + 重叠请求防护。
 */
import { computed, ref, watch, type Ref } from 'vue'
import { getAvailableReplicas } from '@/api/deployment'
import type { AvailableReplica, AvailableReplicasResponse } from '@/api/deployment/types'

interface AvailableReplicasDeps {
  hardware_id: number | null
  gpus_per_container: number
}

export function useAvailableReplicas(deps: Ref<AvailableReplicasDeps>) {
  const response = ref<AvailableReplicasResponse | null>(null)
  const loading = ref(false)
  let lastRequestId = 0

  async function run(): Promise<void> {
    const d = deps.value
    if (!d.hardware_id) {
      response.value = null
      return
    }
    loading.value = true
    const currentRequestId = ++lastRequestId
    try {
      const res = await getAvailableReplicas(d.hardware_id, d.gpus_per_container)
      if (currentRequestId !== lastRequestId) return
      response.value = res
    } catch {
      if (currentRequestId !== lastRequestId) return
      response.value = null
    } finally {
      if (currentRequestId === lastRequestId) {
        loading.value = false
      }
    }
  }

  watch(
    deps,
    () => {
      void run()
    },
    { deep: true }
  )

  const replicas = computed<AvailableReplica[]>(() => response.value?.replicas || [])
  const totalAvailable = computed<number>(() => response.value?.total_available || 0)

  /** 根据 location_id 查询可用副本数 */
  function getAvailableByLocation(locationId: number): number {
    const r = replicas.value.find((x) => x.location_id === locationId)
    return r?.available ?? 0
  }

  return {
    response,
    loading,
    replicas,
    totalAvailable,
    getAvailableByLocation
  }
}
