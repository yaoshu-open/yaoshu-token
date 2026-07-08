<script setup lang="ts">
/**
 * 部署详情 - 硬件与性能 Card。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DeploymentDetails } from '@/api/deployment/types'

const props = defineProps<{ details: DeploymentDetails }>()

const { t } = useI18n()

const percent = computed(() => Math.max(0, Math.min(100, props.details.completed_percent ?? 0)))
const servedHours = computed(() => Math.floor((props.details.compute_minutes_served ?? 0) / 60))
const servedMinutes = computed(() => (props.details.compute_minutes_served ?? 0) % 60)
const remainingHours = computed(() => Math.floor((props.details.compute_minutes_remaining ?? 0) / 60))
const remainingMinutes = computed(() => (props.details.compute_minutes_remaining ?? 0) % 60)
</script>

<template>
  <ElCard
    shadow="never"
    class="deployment-card"
  >
    <template #header>
      <div class="deployment-card__header">
        <i class="i-lucide-chart-line" />
        <span>{{ t('deployment.details.hardware') }}</span>
      </div>
    </template>

    <ElDescriptions
      :column="1"
      size="default"
      border
    >
      <ElDescriptionsItem :label="t('deployment.details.hardwareType')">
        <div class="deployment-card__hw">
          <el-tag
            v-if="details.brand_name"
            size="small"
            type="primary"
          >
            {{ details.brand_name }}
          </el-tag>
          <span class="deployment-card__hw-name">{{ details.hardware_name || '--' }}</span>
        </div>
      </ElDescriptionsItem>
      <ElDescriptionsItem :label="t('deployment.details.gpuCount')">
        <div class="deployment-card__gpu">
          <el-tag size="small">
            {{ details.total_gpus ?? 0 }} {{ t('deployment.details.gpus') }}
          </el-tag>
        </div>
      </ElDescriptionsItem>
      <ElDescriptionsItem :label="t('deployment.details.containerConfig')">
        <div class="deployment-card__config">
          <div>{{ t('deployment.details.gpusPerContainer') }}: {{ details.gpus_per_container ?? '--' }}</div>
          <div>{{ t('deployment.details.totalContainers') }}: {{ details.total_containers ?? '--' }}</div>
        </div>
      </ElDescriptionsItem>
    </ElDescriptions>

    <div class="deployment-card__progress">
      <div class="deployment-card__progress-header">
        <span class="deployment-card__progress-title">{{ t('deployment.details.completion') }}</span>
        <span>{{ percent }}%</span>
      </div>
      <el-progress
        :percentage="percent"
        :stroke-width="8"
        :show-text="false"
      />
      <div class="deployment-card__progress-meta">
        <span>{{ t('deployment.details.served') }}: {{ servedHours }}h {{ servedMinutes }}m</span>
        <span>{{ t('deployment.details.remaining') }}: {{ remainingHours }}h {{ remainingMinutes }}m</span>
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

  &__hw {
    display: flex;
    gap: 6px;
    align-items: center;
  }

  &__hw-name {
    font-weight: 500;
  }

  &__gpu {
    display: flex;
    gap: var(--ys-spacing-1);
    align-items: center;
  }

  &__config {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  &__progress {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin-top: 16px;
  }

  &__progress-header {
    display: flex;
    justify-content: space-between;
    font-size: var(--el-font-size-small);
    font-weight: 500;
  }

  &__progress-meta {
    display: flex;
    justify-content: space-between;
    font-size: var(--el-font-size-extra-small);
    color: var(--el-text-color-secondary);
  }
}
</style>
