/**
 * 部署延长 composable：延长提交 + 价格估算（debounce + requestId 竞态保护）。
 *
 * 关键设计（已人类拍板 2026-06-22）：
 * - 价格估算：watch(hours) → useDebounce(400ms) → fetchPrice
 * - 竞态保护：fetchPrice 内部用 requestId 校验，过期响应丢弃
 * - 快速选择：setQuickDuration(h) → setHours(h)
 * - 价格字段：snake_case + PascalCase 双探（后端契约未明确）
 */
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { estimatePrice, extendDeployment, getDeployment } from '@/api/deployment'
import { PRICE_ESTIMATE_DEBOUNCE } from '@/api/deployment/constants'
import type { DeploymentDetails, PriceEstimation } from '@/api/deployment/types'

function toInt(value: unknown, fallback: number): number {
  const n = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(n) ? Math.max(0, Math.round(n)) : fallback
}

function resolvePriceFields(est: PriceEstimation | null | undefined): {
  total: number | null
  hourly: number | null
  compute: number | null
  currency: string
} {
  if (!est || typeof est !== 'object') return { total: null, hourly: null, compute: null, currency: 'USDC' }
  const breakdown = est.price_breakdown ?? est.PriceBreakdown
  const total =
    typeof est.estimated_cost === 'number'
      ? est.estimated_cost
      : typeof est.EstimatedCost === 'number'
        ? est.EstimatedCost
        : breakdown && typeof breakdown === 'object'
          ? ((breakdown as Record<string, unknown>).total_cost as number | undefined) ??
            ((breakdown as Record<string, unknown>).TotalCost as number | undefined) ??
            null
          : null
  const hourly =
    breakdown && typeof breakdown === 'object'
      ? ((breakdown as Record<string, unknown>).hourly_rate as number | undefined) ??
        ((breakdown as Record<string, unknown>).HourlyRate as number | undefined) ??
        null
      : null
  const compute =
    breakdown && typeof breakdown === 'object'
      ? ((breakdown as Record<string, unknown>).compute_cost as number | undefined) ??
        ((breakdown as Record<string, unknown>).ComputeCost as number | undefined) ??
        null
      : null
  const currency = (est.currency ?? est.Currency ?? 'usdc').toString().toUpperCase()
  return { total, hourly, compute, currency }
}

export function useDeploymentExtend(deploymentIdRef: () => string | number | null) {
  const { t } = useI18n()

  const hours = ref(1)
  const priceEstimation = ref<PriceEstimation | null>(null)
  const priceLoading = ref(false)
  const priceError = ref<string | null>(null)
  const details = ref<DeploymentDetails | null>(null)
  const detailsLoading = ref(false)
  const isSubmitting = ref(false)
  let priceRequestId = 0
  let detailsRequestId = 0
  let debounceTimer: ReturnType<typeof setTimeout> | null = null

  function buildPriceParams(detail: DeploymentDetails) {
    const hardwareId = toInt(detail.hardware_id, 0)
    const gpusPerContainer = toInt(detail.gpus_per_container, 0)
    const replicaCount = toInt(detail.total_containers, 0)
    const locations = Array.isArray(detail.locations) ? detail.locations : []
    const locationIds = locations
      .map((x) => toInt(x?.id, 0))
      .filter((x) => x > 0)
    if (hardwareId <= 0 || gpusPerContainer <= 0 || replicaCount <= 0 || locationIds.length === 0) {
      return null
    }
    return { location_ids: locationIds, hardware_id: hardwareId, gpus_per_container: gpusPerContainer, replica_count: replicaCount }
  }

  async function fetchDetails(): Promise<void> {
    const id = deploymentIdRef()
    if (id === null || id === undefined) return
    const rid = ++detailsRequestId
    detailsLoading.value = true
    try {
      const d = await getDeployment(id)
      if (rid !== detailsRequestId) return
      details.value = d
    } catch {
      if (rid !== detailsRequestId) return
      details.value = null
    } finally {
      if (rid === detailsRequestId) detailsLoading.value = false
    }
  }

  async function fetchPrice(): Promise<void> {
    if (debounceTimer) {
      clearTimeout(debounceTimer)
      debounceTimer = null
    }
    debounceTimer = setTimeout(async () => {
      const detail = details.value
      if (!detail) return
      const params = buildPriceParams(detail)
      if (!params) {
        priceEstimation.value = null
        priceError.value = t('deployment.extend.priceUnavailable')
        return
      }
      const rid = ++priceRequestId
      priceLoading.value = true
      priceError.value = null
      try {
        const est = await estimatePrice({
          ...params,
          duration_hours: hours.value,
          currency: 'usdc'
        })
        if (rid !== priceRequestId) return
        priceEstimation.value = est
      } catch (e) {
        if (rid !== priceRequestId) return
        priceEstimation.value = null
        priceError.value = t('deployment.extend.priceFailed') + ': ' + ((e as Error)?.message ?? '')
      } finally {
        if (rid === priceRequestId) priceLoading.value = false
      }
    }, PRICE_ESTIMATE_DEBOUNCE)
  }

  function setHours(value: number): void {
    hours.value = Math.max(1, Math.min(720, Math.round(Number(value) || 1)))
  }

  function setQuickDuration(value: number): void {
    setHours(value)
  }

  async function submit(): Promise<DeploymentDetails | null> {
    const id = deploymentIdRef()
    if (id === null || id === undefined) return null
    if (hours.value < 1) {
      ElMessage.error(t('deployment.extend.hoursInvalid'))
      return null
    }
    isSubmitting.value = true
    try {
      const res = await extendDeployment(id, { duration_hours: hours.value })
      ElMessage.success(t('deployment.extend.success'))
      return res
    } catch (e) {
      ElMessage.error(t('deployment.extend.failed') + ': ' + ((e as Error)?.message ?? ''))
      return null
    } finally {
      isSubmitting.value = false
    }
  }

  const resolvedPrice = () => resolvePriceFields(priceEstimation.value)

  function reset(): void {
    hours.value = 1
    priceEstimation.value = null
    priceLoading.value = false
    priceError.value = null
    details.value = null
    detailsLoading.value = false
    isSubmitting.value = false
    if (debounceTimer) {
      clearTimeout(debounceTimer)
      debounceTimer = null
    }
  }

  // 详情变化后重新估算
  watch(details, () => {
    void fetchPrice()
  })

  // hours 变化（debounce 在 fetchPrice 内部）
  watch(hours, () => {
    void fetchPrice()
  })

  // 部署 ID 变化 → 重置
  watch(deploymentIdRef, () => {
    reset()
    void fetchDetails()
  })

  return {
    hours,
    setHours,
    setQuickDuration,
    priceEstimation,
    priceLoading,
    priceError,
    details,
    detailsLoading,
    isSubmitting,
    resolvedPrice,
    fetchDetails,
    fetchPrice,
    submit,
    reset
  }
}
