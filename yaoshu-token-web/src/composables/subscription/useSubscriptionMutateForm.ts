import { ref, reactive } from 'vue'
import { createPlan, updatePlan } from '@/api/subscription'
import type { PlanPayload, SubscriptionPlan } from '@/api/subscription/types'

export function useSubscriptionMutateForm(onSuccess: () => void) {
  const mode = ref<'create' | 'update'>('create')
  const loading = ref(false)

  const formData = reactive<PlanPayload>({
    title: '',
    subtitle: '',
    priceAmount: 0,
    currency: 'USD',
    durationUnit: 'month',
    durationValue: 1,
    quotaResetPeriod: 'monthly',
    enabled: true,
    sortOrder: 0,
    allowBalancePay: true,
    maxPurchasePerUser: 1,
    totalAmount: 0,
  })

  function initCreate() {
    mode.value = 'create'
    Object.assign(formData, {
      title: '',
      subtitle: '',
      priceAmount: 0,
      currency: 'USD',
      durationUnit: 'month',
      durationValue: 1,
      quotaResetPeriod: 'monthly',
      enabled: true,
      sortOrder: 0,
      allowBalancePay: true,
      maxPurchasePerUser: 1,
      totalAmount: 0,
      upgradeGroup: undefined,
      stripePriceId: undefined,
      creemProductId: undefined,
      waffoPancakeProductId: undefined,
    })
  }

  function initUpdate(plan: SubscriptionPlan) {
    mode.value = 'update'
    Object.assign(formData, {
      title: plan.title,
      subtitle: plan.subtitle || '',
      priceAmount: plan.priceAmount,
      currency: plan.currency,
      durationUnit: plan.durationUnit,
      durationValue: plan.durationValue,
      customSeconds: plan.customSeconds,
      quotaResetPeriod: plan.quotaResetPeriod,
      quotaResetCustomSeconds: plan.quotaResetCustomSeconds,
      enabled: plan.enabled,
      sortOrder: plan.sortOrder,
      allowBalancePay: plan.allowBalancePay,
      maxPurchasePerUser: plan.maxPurchasePerUser,
      totalAmount: plan.totalAmount,
      upgradeGroup: plan.upgradeGroup || undefined,
      stripePriceId: plan.stripePriceId || undefined,
      creemProductId: plan.creemProductId || undefined,
      waffoPancakeProductId: plan.waffoPancakeProductId || undefined,
    })
  }

  async function submit(id?: number) {
    loading.value = true
    try {
      if (mode.value === 'create') {
        await createPlan({ ...formData })
      } else if (id) {
        await updatePlan(id, { ...formData })
      }
      onSuccess()
    } catch {
      // 错误由 request 拦截器处理，阻止 onSuccess 执行（对话框保持打开）
    } finally {
      loading.value = false
    }
  }

  return {
    mode,
    loading,
    formData,
    initCreate,
    initUpdate,
    submit,
  }
}
