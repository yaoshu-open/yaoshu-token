<script setup lang="ts">
/**
 * 容器详情展示。
 */
import { useI18n } from 'vue-i18n'
import type { DeploymentContainer, DeploymentStatus } from '@/api/deployment/types'
import LoadingState from '@/components/LoadingState.vue'
import StatusBadge from '@/components/StatusBadge.vue'

defineProps<{
  container: DeploymentContainer | null
  loading: boolean
  statusConfig: (status: DeploymentStatus | string | undefined) => { color: string; label: string }
}>()

const emit = defineEmits<{
  (e: 'refresh'): void
  (e: 'openUrl', url: string): void
}>()

const { t } = useI18n()
</script>

<template>
  <div class="deployment-container-details">
    <div
      v-if="loading"
      class="deployment-container-details__loading"
    >
      <LoadingState
        size="sm"
        :message="t('deployment.logs.loadingContainerDetails')"
        inline
      />
    </div>
    <template v-else-if="container">
      <div class="deployment-container-details__row">
        <el-tag
          size="small"
          type="info"
        >
          {{ t('deployment.logs.container') }}
        </el-tag>
        <span class="deployment-container-details__id">{{ container.container_id }}</span>
        <StatusBadge
          :label="statusConfig(container.status).label"
          :variant="statusConfig(container.status).color === 'green' ? 'success' :
            statusConfig(container.status).color === 'red' ? 'danger' :
            statusConfig(container.status).color === 'orange' ? 'warning' :
            statusConfig(container.status).color === 'blue' ? 'primary' : 'neutral'"
        />
        <el-button
          v-if="container.public_url"
          size="small"
          link
          type="primary"
          @click="emit('openUrl', container.public_url!)"
        >
          <i class="i-ep-link" />
          {{ t('deployment.logs.visitContainer') }}
        </el-button>
        <el-tooltip
          :content="t('deployment.logs.refreshContainer')"
          placement="top"
        >
          <el-button
            size="small"
            link
            :loading="loading"
            @click="emit('refresh')"
          >
            <i class="i-ep-refresh" />
          </el-button>
        </el-tooltip>
      </div>
      <div class="deployment-container-details__meta">
        <span>
          <span class="deployment-container-details__meta-label">{{ t('deployment.logs.brand') }}:</span>
          {{ container.brand_name || '--' }}
        </span>
        <span>
          <span class="deployment-container-details__meta-label">{{ t('deployment.logs.hardware') }}:</span>
          {{ container.hardware || '--' }}
        </span>
        <span>
          <span class="deployment-container-details__meta-label">{{ t('deployment.logs.gpuPerContainer') }}:</span>
          {{ container.gpus_per_container ?? '--' }}
        </span>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.deployment-container-details {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-top: 8px;
  border-top: 1px dashed var(--el-border-color-lighter);

  &__loading {
    display: flex;
  }

  &__row {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    align-items: center;
  }

  &__id {
    font-family: var(--el-font-family-monospace, monospace);
    font-size: var(--el-font-size-small);
  }

  &__meta {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-3);
    font-size: var(--el-font-size-small);
  }

  &__meta-label {
    margin-right: 4px;
    color: var(--el-text-color-secondary);
  }
}
</style>
