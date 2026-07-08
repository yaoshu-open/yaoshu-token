// 获取当前用户激活的 API Key
import { ref, onMounted } from 'vue'
import { getTokens } from '@/api/token'
import type { Token } from '@/api/token/types'

export function useActiveChatKey() {
  const activeKey = ref<string | null>(null)
  const loading = ref(true)
  const error = ref<Error | null>(null)

  onMounted(async () => {
    try {
      loading.value = true
      // 获取用户的 Token 列表，取第一个 enabled (status=1) 的 key
      const res = await getTokens({ pageNum: 1, pageSize: 100 })
      const tokens: Token[] = res?.list ?? []
      const enabled = tokens.find((t) => t.status === 1)
      activeKey.value = enabled?.key ?? null
    } catch (err) {
      error.value = err instanceof Error ? err : new Error(String(err))
    } finally {
      loading.value = false
    }
  })

  return { activeKey, loading, error }
}
