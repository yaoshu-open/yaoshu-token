import { ref, computed, onMounted } from 'vue'
import { getAdminPlans, patchPlanStatus, getPaymentCompliance } from '@/api/subscription'
import type { SubscriptionPlan, PlanRecord } from '@/api/subscription/types'

export function useSubscriptionsData() {
  const plans = ref<SubscriptionPlan[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const searchKeyword = ref('')
  const complianceConfirmed = ref(true) // default true, updated on fetch

  const filteredPlans = computed(() => {
    if (!searchKeyword.value) return plans.value
    const kw = searchKeyword.value.toLowerCase()
    return plans.value.filter(
      (p) =>
        p.title.toLowerCase().includes(kw) ||
        (p.subtitle && p.subtitle.toLowerCase().includes(kw))
    )
  })

  const total = computed(() => filteredPlans.value.length)

  async function fetchPlans() {
    loading.value = true
    error.value = null
    try {
      const [data] = await Promise.all([
        getAdminPlans(),
        // 并行获取合规状态，失败不阻塞列表加载
        getPaymentCompliance()
          .then((resp: any) => {
            const raw = resp?.data ?? resp
            if (raw && typeof raw === 'object') {
              const paymentSetting = raw['payment_setting']
              if (paymentSetting) {
                try {
                  const parsed = JSON.parse(paymentSetting)
                  complianceConfirmed.value = parsed.compliance_confirmed !== false
                } catch {
                  // JSON 解析失败，保持默认值
                }
              }
            }
          })
          .catch(() => {
            // 合规状态获取失败（如权限不足），保持默认值 true
          }),
      ])
      plans.value = (Array.isArray(data) ? data : []).map(
        (item: PlanRecord) => item.plan
      )
    } catch (e: any) {
      error.value = e?.message || 'Failed to load subscription plans'
    } finally {
      loading.value = false
    }
  }

  async function togglePlanStatus(id: number, enabled: boolean) {
    await patchPlanStatus(id, enabled)
    const plan = plans.value.find((p) => p.id === id)
    if (plan) plan.enabled = enabled
  }

  onMounted(() => {
    fetchPlans()
  })

  return {
    plans,
    filteredPlans,
    loading,
    error,
    total,
    searchKeyword,
    complianceConfirmed,
    fetchPlans,
    togglePlanStatus,
  }
}
