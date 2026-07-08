/**
 * 名称查重 composable。
 *
 * 设计：防抖 400ms（复用 PRICE_ESTIMATE_DEBOUNCE）+ 状态机 + 重叠请求防护（lastRequestId）。
 */
import { ref, watch, type Ref } from 'vue'
import { checkDeploymentName } from '@/api/deployment'
import { PRICE_ESTIMATE_DEBOUNCE } from '@/api/deployment/constants'

export type NameCheckStatus = 'idle' | 'checking' | 'available' | 'taken'

export function useNameCheck(name: Ref<string>) {
  const status = ref<NameCheckStatus>('idle')
  let timer: ReturnType<typeof setTimeout> | null = null
  let lastRequestId = 0

  async function run(nameValue: string): Promise<void> {
    if (!nameValue.trim()) {
      status.value = 'idle'
      return
    }
    status.value = 'checking'
    const currentRequestId = ++lastRequestId
    try {
      const res = await checkDeploymentName(nameValue)
      if (currentRequestId !== lastRequestId) return
      status.value = res.available ? 'available' : 'taken'
    } catch {
      if (currentRequestId !== lastRequestId) return
      status.value = 'idle'
    }
  }

  watch(
    name,
    (newName) => {
      if (timer) clearTimeout(timer)
      timer = setTimeout(() => {
        void run(newName)
      }, PRICE_ESTIMATE_DEBOUNCE)
    }
  )

  return {
    status
  }
}
