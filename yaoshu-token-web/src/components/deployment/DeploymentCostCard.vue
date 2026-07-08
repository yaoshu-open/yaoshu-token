<script setup lang="ts">
/**
 * 部署详情 - 费用信息 Card。
 */
import { useI18n } from 'vue-i18n'
import type { DeploymentDetails } from '@/api/deployment/types'

defineProps<{ details: DeploymentDetails }>()

const { t } = useI18n()

function formatAmount(value: number | undefined): string {
  return value ? Number(value).toFixed(2) : '0.00'
}

function formatTimestamp(value: number | string | undefined): string {
  if (!value) return 'N/A'
  if (typeof value === 'string') return value
  const date = new Date(value * (value < 1e12 ? 1000 : 1))
  return date.toLocaleString()
}
</script>

<template>
  <ElCard
    shadow="never"
    class="deployment-card"
  >
    <template #header>
      <div class="deployment-card__header">
        <i class="i-ep-coin" />
        <span>{{ t('deployment.details.cost') }}</span>
      </div>
    </template>

    <div class="deployment-card__paid">
      <span class="deployment-card__paid-label">{{ t('deployment.details.amountPaid') }}</span>
      <span class="deployment-card__paid-value">${{ formatAmount(details.amount_paid) }} USDC</span>
    </div>

    <div class="deployment-card__meta">
      <div>
        <span class="deployment-card__meta-label">{{ t('deployment.details.billingStart') }}:</span>
        <span>{{ formatTimestamp(details.started_at) }}</span>
      </div>
      <div>
        <span class="deployment-card__meta-label">{{ t('deployment.details.billingEnd') }}:</span>
        <span>{{ formatTimestamp(details.finished_at) }}</span>
      </div>
    </div>
  </ElCard>
</template>

<style scoped lang="scss">
.deployment-card {
  border: 1px solid var(--el-border-color-lighter);

  &__header {
    display: flex;
    gap: 6px;
    align-items: center;
    font-weight: 500;
  }

  &__paid {
    display: flex;
    justify-content: space-between;
    padding: var(--ys-spacing-3);
    background: var(--el-color-success-light-9);
    border-radius: var(--el-border-radius-base);
  }

  &__paid-label {
    color: var(--el-text-color-regular);
  }

  &__paid-value {
    font-size: var(--el-font-size-large);
    font-weight: 500;
    color: var(--el-color-success);
  }

  &__meta {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: var(--ys-spacing-2);
    margin-top: 12px;
    font-size: var(--el-font-size-small);
  }

  &__meta-label {
    margin-right: 4px;
    color: var(--el-text-color-secondary);
  }
}
</style>
