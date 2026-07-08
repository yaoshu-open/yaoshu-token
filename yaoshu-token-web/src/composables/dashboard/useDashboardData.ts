/**
 * Dashboard 数据汇聚 composable。
 *
 * 职责：并行拉取 quotaDates + tokens + userModels，暴露 loading/refetch/数据/降级兜底。
 */
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/store/modules/auth'
import { getUserQuotaDates } from '@/api/dashboard'
import { getTokens, getToken } from '@/api/token'
import { getUserModels } from '@/api/user'
import { DEFAULT_SPARKLINE_HOURS, SPARKLINE_BUCKETS } from '@/api/dashboard/constants'
import type { QuotaDate } from '@/api/dashboard/types'
import type { Token, TokensListData } from '@/api/token/types'
import type { UserAvailableModel } from '@/api/user/types'

/** 兜底模型名（userModels 拉取失败时 curl 预览使用） */
const FALLBACK_MODEL = 'gpt-4o-mini'

export function useDashboardData() {
  const authStore = useAuthStore()
  const { userInfo } = storeToRefs(authStore)

  const quotaDates = ref<QuotaDate[]>([])
  const tokens = ref<Token[]>([])
  const userModels = ref<UserAvailableModel[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  /** 首条 status===1 的 token（SetupGuide 完成判断 + curl 预览） */
  const preferredKey = computed<Token | null>(() => {
    return tokens.value.find((t) => t.status === 1) ?? tokens.value[0] ?? null
  })

  /** 近 24h 用量求和（sparkline 数据源） */
  const recentUsage = computed<number>(() => {
    return quotaDates.value.reduce((sum, item) => sum + (item.quota || 0), 0)
  })

  /** sparkline 12 桶聚合（balance/usage/requests 三条趋势线） */
  const sparklineData = computed(() => {
    if (quotaDates.value.length === 0) return { usage: [], requests: [] }
    const bucketSize = Math.max(1, Math.ceil(quotaDates.value.length / SPARKLINE_BUCKETS))
    const usageBuckets: number[] = []
    const requestBuckets: number[] = []
    for (let i = 0; i < quotaDates.value.length; i += bucketSize) {
      const slice = quotaDates.value.slice(i, i + bucketSize)
      usageBuckets.push(slice.reduce((s, x) => s + (x.quota || 0), 0))
      requestBuckets.push(slice.reduce((s, x) => s + (x.requests || 0), 0))
    }
    return { usage: usageBuckets, requests: requestBuckets }
  })

  /** 用户可用模型列表（失败兜底 FALLBACK_MODEL） */
  const availableModels = computed<string[]>(() => {
    if (userModels.value.length > 0) {
      return userModels.value.map((m) => m.id)
    }
    return [FALLBACK_MODEL]
  })

  /** 构建 sparkline 查询参数（近 24h，hour 粒度） */
  function buildSparklineParams() {
    const now = Math.floor(Date.now() / 1000)
    const start = now - DEFAULT_SPARKLINE_HOURS * 3600
    return { start_timestamp: start, end_timestamp: now, default_time: 'hour' as const }
  }

  /** 并行拉取三数据源，任一失败不阻塞其他 */
  async function fetchAll() {
    loading.value = true
    error.value = null
    const results = await Promise.allSettled([
      getUserQuotaDates(buildSparklineParams()),
      getTokens({ pageNum: 1, pageSize: 10 }),
      getUserModels(),
    ])

    // quotaDates：失败时降级为空数组（统计卡片无 sparkline）
    if (results[0].status === 'fulfilled') {
      quotaDates.value = results[0].value ?? []
    } else {
      quotaDates.value = []
    }

    // tokens：失败时降级为空数组（SetupGuide 显示"创建 API Key"引导）
    if (results[1].status === 'fulfilled') {
      const data = results[1].value as TokensListData
      tokens.value = data.list ?? []
    } else {
      tokens.value = []
    }

    // userModels：失败时降级为空数组（availableModels 兜底 FALLBACK_MODEL）
    if (results[2].status === 'fulfilled') {
      userModels.value = results[2].value ?? []
    } else {
      userModels.value = []
    }

    // 三源全失败时设置 error
    if (results.every((r) => r.status === 'rejected')) {
      error.value = 'Dashboard 数据加载失败'
    }
    loading.value = false
  }

  /** 拉取完整 token key（复制 curl 时调用） */
  async function fetchTokenKey(tokenId: number): Promise<string | null> {
    try {
      const token = await getToken(tokenId)
      return token.key ?? null
    } catch {
      return null
    }
  }

  onMounted(fetchAll)

  return {
    // 用户信息（来自 auth store）
    userInfo,
    // 数据
    quotaDates,
    tokens,
    userModels,
    availableModels,
    preferredKey,
    recentUsage,
    sparklineData,
    // 三态
    loading,
    error,
    // 操作
    refetch: fetchAll,
    fetchTokenKey,
  }
}
