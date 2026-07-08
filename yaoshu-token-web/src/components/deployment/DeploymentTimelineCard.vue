<script setup lang="ts">
/**
 * 部署详情 - 时间信息 Card。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DeploymentDetails } from '@/api/deployment/types'

const props = defineProps<{ details: DeploymentDetails }>()

const { t } = useI18n()

const servedHours = computed(() => Math.floor((props.details.compute_minutes_served ?? 0) / 60))
const servedMinutes = computed(() => (props.details.compute_minutes_served ?? 0) % 60)
const remainingHours = computed(() => Math.floor((props.details.compute_minutes_remaining ?? 0) / 60))
const remainingMinutes = computed(() => (props.details.compute_minutes_remaining ?? 0) % 60)

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
        <i class="i-ep-time" />
        <span>{{ t('deployment.details.timeline') }}</span>
      </div>
    </template>

    <div class="deployment-card__grid">
      <div class="deployment-card__row">
        <span class="deployment-card__label">{{ t('deployment.details.elapsed') }}:</span>
        <span class="deployment-card__value">{{ servedHours }}h {{ servedMinutes }}m</span>
      </div>
      <div class="deployment-card__row">
        <span class="deployment-card__label">{{ t('deployment.details.remaining') }}:</span>
        <span class="deployment-card__value deployment-card__value--warning">
          {{ remainingHours }}h {{ remainingMinutes }}m
        </span>
      </div>
      <div class="deployment-card__row">
        <span class="deployment-card__label">{{ t('deployment.details.createdAt') }}:</span>
        <span class="deployment-card__value">{{ formatTimestamp(details.created_at) }}</span>
      </div>
      <div class="deployment-card__row">
        <span class="deployment-card__label">{{ t('deployment.details.updatedAt') }}:</span>
        <span class="deployment-card__value">{{ formatTimestamp(details.updated_at) }}</span>
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

  &__grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: var(--ys-spacing-3);
  }

  &__row {
    display: flex;
    justify-content: space-between;
    font-size: var(--el-font-size-small);
  }

  &__label {
    color: var(--el-text-color-secondary);
  }

  &__value {
    font-weight: 500;

    &--warning {
      color: var(--el-color-warning);
    }
  }
}
</style>
