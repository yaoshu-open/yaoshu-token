<script setup lang="ts">
/**
 * 部署延长增强 Dialog (T-MD-03)。
 * 集成：ExtendBanner + ExtendQuickPicker + ExtendPriceBreakdown + ElInputNumber + ElDescriptions。
 *
 * 价格竞态：useDeploymentExtend 内部 useDebounce(400ms) + requestId 校验（已人类拍板 2026-06-22）。
 */
import { computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useDeploymentExtend } from '@/composables/deployment/useDeploymentExtend'
import type { DeploymentListItem } from '@/api/deployment/types'
import LoadingState from '@/components/LoadingState.vue'
import DeploymentExtendBanner from './DeploymentExtendBanner.vue'
import DeploymentExtendQuickPicker from './DeploymentExtendQuickPicker.vue'
import DeploymentExtendPriceBreakdown from './DeploymentExtendPriceBreakdown.vue'

const props = defineProps<{
  modelValue: boolean
  deployment: DeploymentListItem | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'extended', details: unknown): void
}>()

const { t } = useI18n()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const idRef = computed(() => (props.deployment?.id ?? null) as string | number | null)

const {
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
  submit
} = useDeploymentExtend(() => idRef.value)

watch(visible, (v) => {
  if (v) void fetchDetails()
}, { immediate: true })

const resolved = computed(() => resolvedPrice())
const canSubmit = computed(() => Boolean(idRef.value) && hours.value >= 1 && !isSubmitting.value)

async function handleSubmit(): Promise<void> {
  const res = await submit()
  if (res) {
    emit('extended', res)
    visible.value = false
  }
}

const QUICK_OPTIONS = [1, 6, 12, 24, 72, 168] as const
</script>

<template>
  <ElDialog
    v-model="visible"
    :title="t('deployment.extend.title')"
    width="640"
    :close-on-click-modal="false"
    align-center
    destroy-on-close
  >
    <template #header>
      <div class="deployment-extend-header">
        <i class="i-ep-time" />
        <span class="deployment-extend-header__title">{{ t('deployment.extend.title') }}</span>
      </div>
    </template>

    <div class="deployment-extend">
      <div
        v-if="detailsLoading && !details"
        class="deployment-extend__loading"
      >
        <LoadingState :message="t('deployment.extend.loadingDetails')" />
      </div>

      <template v-else-if="details">
        <div class="deployment-extend__summary">
          <div class="deployment-extend__summary-left">
            <div class="deployment-extend__name">
              {{ deployment?.container_name || deployment?.deployment_name || details.id }}
            </div>
            <div class="deployment-extend__id">
              ID: {{ details.id }}
            </div>
          </div>
          <div class="deployment-extend__summary-right">
            <el-tag
              v-if="details.hardware_name"
              size="small"
              type="info"
            >
              {{ details.hardware_name }}{{ details.total_gpus ? ` x${details.total_gpus}` : '' }}
            </el-tag>
            <div class="deployment-extend__remaining">
              <span class="deployment-extend__remaining-label">{{ t('deployment.extend.currentRemaining') }}:</span>
              <span class="deployment-extend__remaining-value">{{ details.time_remaining || t('common.unknown') }}</span>
            </div>
          </div>
        </div>

        <DeploymentExtendBanner />

        <div class="deployment-extend__hours">
          <label class="deployment-extend__label">{{ t('deployment.extend.hoursLabel') }}</label>
          <el-input-number
            :model-value="hours"
            :min="1"
            :max="720"
            :step="1"
            style="width: 100%"
            @update:model-value="(v) => setHours(Number(v) || 1)"
          />
        </div>

        <DeploymentExtendQuickPicker
          :model-value="hours"
          :options="QUICK_OPTIONS"
          :disabled="isSubmitting"
          @update:model-value="setQuickDuration"
        />

        <DeploymentExtendPriceBreakdown
          :loading="priceLoading"
          :estimation="priceEstimation"
          :error="priceError"
          :resolved="resolved"
        />
      </template>
    </div>

    <template #footer>
      <el-button
        :disabled="isSubmitting"
        @click="visible = false"
      >
        {{ t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        :loading="isSubmitting"
        :disabled="!canSubmit"
        @click="handleSubmit"
      >
        {{ t('deployment.extend.confirm') }}
      </el-button>
    </template>
  </ElDialog>
</template>

<style scoped lang="scss">
.deployment-extend {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-4);

  &-header {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;

    &__title {
      font-size: var(--el-font-size-large);
      font-weight: 500;
    }
  }

  &__loading {
    min-height: 200px;
  }

  &__summary {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    padding: var(--ys-spacing-3);
    background: var(--el-fill-color-light);
    border-radius: var(--el-border-radius-base);
  }

  &__name {
    font-size: var(--el-font-size-medium);
    font-weight: 500;
  }

  &__id {
    margin-top: 4px;
    font-family: var(--el-font-family-monospace, monospace);
    font-size: var(--el-font-size-extra-small);
    color: var(--el-text-color-secondary);
  }

  &__summary-right {
    display: flex;
    flex-direction: column;
    gap: 6px;
    align-items: flex-end;
  }

  &__remaining {
    display: flex;
    gap: var(--ys-spacing-1);
    font-size: var(--el-font-size-small);

    &-label {
      color: var(--el-text-color-secondary);
    }

    &-value {
      font-weight: 500;
      color: var(--el-color-warning);
    }
  }

  &__hours {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  &__label {
    font-size: var(--el-font-size-small);
    font-weight: 500;
  }
}
</style>
