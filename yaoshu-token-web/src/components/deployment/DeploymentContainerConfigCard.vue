<script setup lang="ts">
/**
 * 部署详情 - 容器配置 Card。
 */
import { useI18n } from 'vue-i18n'
import type { ContainerConfig } from '@/api/deployment/types'

defineProps<{ config: ContainerConfig }>()

const { t } = useI18n()
</script>

<template>
  <ElCard
    shadow="never"
    class="deployment-card"
  >
    <template #header>
      <div class="deployment-card__header">
        <i class="i-lucide-container" />
        <span>{{ t('deployment.details.containerConfigTitle') }}</span>
      </div>
    </template>

    <ElDescriptions
      :column="1"
      size="default"
      border
    >
      <ElDescriptionsItem :label="t('deployment.details.imageUrl')">
        <span class="deployment-card__mono">{{ config.image_url || 'N/A' }}</span>
      </ElDescriptionsItem>
      <ElDescriptionsItem :label="t('deployment.details.trafficPort')">
        {{ config.traffic_port ?? 'N/A' }}
      </ElDescriptionsItem>
      <ElDescriptionsItem :label="t('deployment.details.entrypoint')">
        <span class="deployment-card__mono">
          {{ config.entrypoint && config.entrypoint.length > 0 ? config.entrypoint.join(' ') : 'N/A' }}
        </span>
      </ElDescriptionsItem>
    </ElDescriptions>

    <div
      v-if="config.env_variables && Object.keys(config.env_variables).length > 0"
      class="deployment-card__env"
    >
      <div class="deployment-card__env-title">
        {{ t('deployment.details.envVariables') }}:
      </div>
      <div class="deployment-card__env-list">
        <div
          v-for="(value, key) in config.env_variables"
          :key="key"
          class="deployment-card__env-item"
        >
          <span class="deployment-card__env-key">{{ key }}=</span>
          <span class="deployment-card__env-value">{{ String(value) }}</span>
        </div>
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

  &__mono {
    font-family: var(--el-font-family-monospace, monospace);
    color: var(--el-text-color-secondary);
    word-break: break-all;
  }

  &__env {
    margin-top: 16px;
  }

  &__env-title {
    margin-bottom: 6px;
    font-weight: 500;
  }

  &__env-list {
    max-height: 160px;
    padding: var(--ys-spacing-2) var(--ys-spacing-3);
    overflow-y: auto;
    background: var(--el-fill-color-light);
    border-radius: var(--el-border-radius-base);
  }

  &__env-item {
    display: flex;
    gap: var(--ys-spacing-1);
    margin-bottom: 2px;
    font-family: var(--el-font-family-monospace, monospace);
    font-size: var(--el-font-size-small);
  }

  &__env-key {
    font-weight: 500;
    color: var(--el-color-primary);
  }

  &__env-value {
    color: var(--el-text-color-regular);
    word-break: break-all;
  }
}
</style>
