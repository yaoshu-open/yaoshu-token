/**
 * 充值配置信息 composable。
 */
import { computed, ref, onMounted } from 'vue'
import { getTopupInfo } from '@/api/wallet'
import type { PresetAmount, TopupInfo } from '@/api/wallet/types'

export function useTopupInfo() {
  const topupInfo = ref<TopupInfo | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  const presetAmounts = computed<PresetAmount[]>(() => {
    if (!topupInfo.value?.amountOptions) return []
    return topupInfo.value.amountOptions.map((value) => ({
      value,
      discount: topupInfo.value?.discount?.[value],
    }))
  })

  async function fetchTopupInfo(): Promise<void> {
    loading.value = true
    error.value = null
    try {
      topupInfo.value = await getTopupInfo()
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load topup info'
      topupInfo.value = null
    } finally {
      loading.value = false
    }
  }

  onMounted(fetchTopupInfo)

  return {
    topupInfo,
    presetAmounts,
    loading,
    error,
    fetchTopupInfo,
  }
}
