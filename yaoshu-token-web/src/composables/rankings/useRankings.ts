import { ref, watch, type Ref } from 'vue'
import { getRankings } from '@/api/rankings'
import type { RankingPeriod, RankingsSnapshot } from '@/api/rankings/types'

const STALE_TIME = 5 * 60 * 1000

/**
 * 排行榜数据获取。
 * 接收 period 响应式引用，period 变更时自动重新拉取。
 * 5 分钟 staleTime 缓存窗口（对齐 default ），
 * 同 period 短时间内不重复请求。
 *
 */
export function useRankings(period: Ref<RankingPeriod>) {
  const snapshot = ref<RankingsSnapshot | null>(null)
  const loading = ref(true)
  const error = ref<Error | null>(null)

  let lastPeriod: RankingPeriod | null = null
  let lastFetchAt = 0

  async function load(force = false) {
    const now = Date.now()
    // 同 period 在 staleTime 内直接复用，避免重复请求
    if (
      !force &&
      lastPeriod === period.value &&
      snapshot.value &&
      now - lastFetchAt < STALE_TIME
    ) {
      return
    }
    loading.value = true
    error.value = null
    try {
      snapshot.value = await getRankings(period.value)
      lastPeriod = period.value
      lastFetchAt = Date.now()
    } catch (e) {
      error.value = e as Error
    } finally {
      loading.value = false
    }
  }

  // period 变更触发强制刷新（切换时间窗口必须拉新数据）
  watch(period, () => load(true), { immediate: true })

  return { snapshot, loading, error, reload: () => load(true) }
}
