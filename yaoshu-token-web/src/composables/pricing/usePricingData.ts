/**
 * Pricing 数据获取 composable。
 * 消费 useStatus() 获取 priceRate / usdExchangeRate。
 */
import { ref, computed, onMounted, type Ref } from 'vue'
import { getPricing } from '@/api/pricing'
import { useStatus } from '@/composables/useStatus'
import type { PricingModel, PricingVendor, EndpointInfo } from '@/api/pricing/types'

const STALE_TIME = 5 * 60 * 1000

interface UsePricingDataReturn {
  models: Ref<PricingModel[]>
  vendors: Ref<PricingVendor[]>
  groupRatio: Ref<Record<string, number>>
  usableGroup: Ref<Record<string, { desc: string; ratio: number }>>
  endpointMap: Ref<Record<string, EndpointInfo>>
  autoGroups: Ref<string[]>
  isLoading: Ref<boolean>
  error: Ref<Error | null>
  priceRate: Ref<number>
  usdExchangeRate: Ref<number>
  refetch: () => Promise<void>
}

export function usePricingData(): UsePricingDataReturn {
  const { status } = useStatus()

  const rawData = ref<Awaited<ReturnType<typeof getPricing>> | null>(null)
  // 初值 true：避免首屏先渲染空内容（v-else），再 onMounted 触发 load 后切到 skeleton 的"白板→闪"两跳
  // load() 内部会在缓存命中时同步重置为 false，让"已有数据"场景无骨架闪烁
  const isLoading = ref(true)
  const error = ref<Error | null>(null)
  let lastFetchAt = 0

  // 汇率防零除
  const priceRate = computed(() =>
    Math.max((status.value?.price as number) ?? 1, 0.001)
  )
  const usdExchangeRate = computed(() =>
    Math.max((status.value?.usdExchangeRate as number) ?? priceRate.value, 0.001)
  )

  const models = computed<PricingModel[]>(() => {
    if (!rawData.value?.pricing || !rawData.value?.vendors) return []
    const vendorMap = new Map(rawData.value.vendors.map((v) => [v.id, v]))
    return rawData.value.pricing.map((model) => {
      const vendor = model.vendorId ? vendorMap.get(model.vendorId) : undefined
      return {
        ...model,
        key: model.modelName,
        vendorName: vendor?.name,
        vendorIcon: vendor?.icon,
        vendorDescription: vendor?.description,
        groupRatio: rawData.value!.group_ratio
      }
    })
  })

  const vendors = computed(() => rawData.value?.vendors ?? [])
  const groupRatio = computed(() => rawData.value?.group_ratio ?? {})
  const usableGroup = computed(() => rawData.value?.usable_group ?? {})
  const endpointMap = computed(() => rawData.value?.supported_endpoint ?? {})
  const autoGroups = computed(() => rawData.value?.auto_groups ?? [])

  async function load(force = false) {
    const now = Date.now()
    const isCacheHit = !force && rawData.value && now - lastFetchAt < STALE_TIME
    if (isCacheHit) {
      // 命中缓存：跳过 loading 闪烁，同步切到内容态
      isLoading.value = false
      return
    }

    isLoading.value = true
    error.value = null
    try {
      rawData.value = await getPricing()
      lastFetchAt = Date.now()
    } catch (e) {
      error.value = e as Error
    } finally {
      isLoading.value = false
    }
  }

  onMounted(() => load())

  return {
    models,
    vendors,
    groupRatio,
    usableGroup,
    endpointMap,
    autoGroups,
    isLoading,
    error,
    priceRate,
    usdExchangeRate,
    refetch: () => load(true)
  }
}
