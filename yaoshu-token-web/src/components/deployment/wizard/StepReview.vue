<script setup lang="ts">
/**
 * Step 5: 确认提交（表单摘要 + 价格估算 + 提交）。
 * 价格估算由 Wizard 容器的 usePriceEstimate composable 计算，透传到此处显示。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DeploymentFormState } from '@/composables/deployment/useDeploymentForm'
import type { PriceEstimation } from '@/api/deployment/types'

interface Props {
  form: DeploymentFormState
  estimate: PriceEstimation | null
  loading: boolean
  submitting: boolean
}

const props = defineProps<Props>()
const { t } = useI18n()

const estimatedCost = computed(() => {
  const v = props.estimate?.estimated_cost ?? props.estimate?.EstimatedCost
  return v != null ? Number(v).toFixed(2) : '--'
})

const hourlyRate = computed(() => {
  const v = props.estimate?.price_breakdown?.hourly_rate ?? props.estimate?.PriceBreakdown?.hourly_rate
  return v != null ? Number(v).toFixed(2) : '--'
})

const currency = computed(() => props.estimate?.currency ?? props.estimate?.Currency ?? 'usdc')
</script>

<template>
  <div class="step-review">
    <h4 class="review-section-title">
      {{ t('deployment.create.review.title') }}
    </h4>

    <el-descriptions
      :column="2"
      border
    >
      <el-descriptions-item :label="t('deployment.create.basicInfo.name')">
        {{ props.form.resource_private_name }}
      </el-descriptions-item>
      <el-descriptions-item :label="t('deployment.create.basicInfo.imageUrl')">
        {{ props.form.image_url }}
      </el-descriptions-item>
      <el-descriptions-item :label="t('deployment.create.basicInfo.duration')">
        {{ props.form.duration_hours }}
      </el-descriptions-item>
      <el-descriptions-item :label="t('deployment.create.hardware.replicaCount')">
        {{ props.form.replica_count }}
      </el-descriptions-item>
      <el-descriptions-item :label="t('deployment.create.hardware.gpuCount')">
        {{ props.form.gpus_per_container }}
      </el-descriptions-item>
      <el-descriptions-item :label="t('deployment.create.location.title')">
        {{ props.form.location_ids.join(', ') }}
      </el-descriptions-item>
    </el-descriptions>

    <div class="price-estimate">
      <h4 class="review-section-title">
        {{ t('deployment.create.review.priceEstimate') }}
      </h4>
      <div
        v-if="props.loading"
        class="price-loading"
      >
        <i class="i-ep-loading" />
        {{ t('deployment.create.review.priceLoading') }}
      </div>
      <div
        v-else-if="props.estimate"
        class="price-grid"
      >
        <div class="price-item">
          <span class="price-label">{{ t('deployment.create.review.estimatedCost') }}</span>
          <span class="price-value">{{ estimatedCost }} {{ currency }}</span>
        </div>
        <div class="price-item">
          <span class="price-label">{{ t('deployment.create.review.hourlyRate') }}</span>
          <span class="price-value">{{ hourlyRate }} {{ currency }}</span>
        </div>
      </div>
      <el-alert
        v-else
        type="info"
        :title="t('deployment.create.review.priceLoading')"
        :closable="false"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.step-review {
  .review-section-title {
    margin: 0 0 var(--ys-spacing-3);
    font-size: var(--el-font-size-medium);
    font-weight: 500;
  }

  .price-estimate {
    margin-top: 24px;
  }

  .price-loading {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
    color: var(--el-text-color-secondary);
  }

  .price-grid {
    display: flex;
    gap: var(--ys-spacing-8);
  }

  .price-item {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-1);
  }

  .price-label {
    font-size: var(--el-font-size-small);
    color: var(--el-text-color-secondary);
  }

  .price-value {
    font-size: var(--el-font-size-large);
    font-weight: 500;
    color: var(--el-color-primary);
  }
}
</style>
