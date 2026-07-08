import { storeToRefs } from 'pinia'
import { onMounted, ref, type Ref } from 'vue'
import { useSystemConfigStore } from '@/store/modules/system-config'
import type { SystemStatus } from '@/api/system/types'

const FIVE_MINUTES = 5 * 60 * 1000

interface UseStatusReturn {
  status: Ref<SystemStatus | null>
  loading: Ref<boolean>
  error: Ref<Error | null>
  refetch: () => Promise<SystemStatus | null>
}

/**
 *
 * 行为：
 * - 初次挂载时若无缓存（rawStatus===null）或缓存超过 5min，触发拉取
 * - 拉取失败时 error 置位，不静默放行
 * - 暴露 refetch() 供组件主动触发重拉
 */
export function useStatus(): UseStatusReturn {
  const store = useSystemConfigStore()
  const { rawStatus, loading, lastError } = storeToRefs(store)
  const lastFetchAt = ref<number>(0)

  async function refetch(): Promise<SystemStatus | null> {
    const status = await store.fetch()
    lastFetchAt.value = Date.now()
    return status
  }

  function isStale(): boolean {
    if (rawStatus.value === null) return true
    return Date.now() - lastFetchAt.value > FIVE_MINUTES
  }

  onMounted(() => {
    if (isStale()) {
      // 失败由 store.fetch 内部 throw + lastError 暴露，此处吞掉避免未捕获 promise
      refetch().catch(() => {
        /* 错误已通过 store.lastError 暴露 + ElMessage 提示 */
      })
    }
  })

  return {
    status: rawStatus,
    loading,
    error: lastError,
    refetch
  }
}
