<script setup lang="ts">
/**
 * 延长费用明细分解（hourly / compute / total）。
 */
import { useI18n } from 'vue-i18n'
import type { PriceEstimation } from '@/api/deployment/types'

defineProps<{
  loading: boolean
  estimation: PriceEstimation | null
  error: string | null
  resolved: {
    total: number | null
    hourly: number | null
    compute: number | null
    currency: string
  }
}>()

const { t } = useI18n()

function formatAmount(value: number | null | undefined): string {
  if (value === null || value === undefined) return '--'
  return Number(value).toFixed(4)
}
</script>

<template>
  <div class="deployment-price-breakdown">
    <div class="deployment-price-breakdown__title">
      {{ t('deployment.extend.priceBreakdown') }}
    </div>

    <div
      v-if="loading"
      class="deployment-price-breakdown__loading"
    >
      <i class="i-ep-loading" />
      <span>{{ t('deployment.extend.calculating') }}</span>
    </div>

    <div
      v-else-if="error"
      class="deployment-price-breakdown__error"
    >
      <i class="i-ep-warning" />
      <span>{{ error }}</span>
    </div>

    <div
      v-else-if="resolved.total !== null"
      class="deployment-price-breakdown__rows"
    >
      <div class="deployment-price-breakdown__row">
        <span>{{ t('deployment.extend.hourlyRate') }}</span>
        <span class="deployment-price-breakdown__value">
          {{ formatAmount(resolved.hourly) }} {{ resolved.currency }}
        </span>
      </div>
      <div class="deployment-price-breakdown__row">
        <span>{{ t('deployment.extend.computeCost') }}</span>
        <span class="deployment-price-breakdown__value">
          {{ formatAmount(resolved.compute) }} {{ resolved.currency }}
        </span>
      </div>
      <div class="deployment-price-breakdown__total">
        <span>{{ t('deployment.extend.estimatedTotal') }}</span>
        <span class="deployment-price-breakdown__total-value">
          {{ formatAmount(resolved.total) }} {{ resolved.currency }}
        </span>
      </div>
    </div>

    <div
      v-else
      class="deployment-price-breakdown__empty"
    >
      {{ t('deployment.extend.priceUnavailable') }}
    </div>
  </div>
</template>

<style scoped lang="scss">
.deployment-price-breakdown {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-2);
  padding: var(--ys-spacing-3);
  background: var(--el-fill-color-light);
  border-radius: var(--el-border-radius-base);

  &__title {
    font-size: var(--el-font-size-small);
    font-weight: 500;
  }

  &__loading,
  &__error {
    display: flex;
    gap: 6px;
    align-items: center;
    font-size: var(--el-font-size-small);
  }

  &__error {
    color: var(--el-color-warning);
  }

  &__empty {
    font-size: var(--el-font-size-small);
    color: var(--el-text-color-secondary);
  }

  &__rows {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-1);
  }

  &__row {
    display: flex;
    justify-content: space-between;
    font-size: var(--el-font-size-small);
  }

  &__value {
    font-family: var(--el-font-family-monospace, monospace);
  }

  &__total {
    display: flex;
    justify-content: space-between;
    padding-top: 8px;
    font-weight: 500;
    border-top: 1px dashed var(--el-border-color);
  }

  &__total-value {
    font-family: var(--el-font-family-monospace, monospace);
    color: var(--el-color-primary);
  }
}
</style>
