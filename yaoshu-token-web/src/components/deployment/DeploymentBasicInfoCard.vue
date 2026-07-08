<script setup lang="ts">
/**
 * 部署详情 - 基本信息 Card。
 */
import { useI18n } from 'vue-i18n'
import { useCopyToClipboard } from '@/composables/useCopyToClipboard'
import { DEPLOYMENT_STATUS_CONFIG } from '@/api/deployment/constants'
import type { DeploymentDetails } from '@/api/deployment/types'
import StatusBadge from '@/components/StatusBadge.vue'

const props = defineProps<{ details: DeploymentDetails }>()

const { t } = useI18n()
const { copy } = useCopyToClipboard({
  successMessage: t('deployment.details.copyIdSuccess'),
  errorMessage: t('deployment.details.copyIdFailed')
})

const statusConfig = () => {
  const key = String(props.details.status ?? '').toLowerCase()
  return DEPLOYMENT_STATUS_CONFIG[key] ?? { i18nKey: `deployment.status.${key}`, variant: 'neutral' as const }
}

function formatTimestamp(value: number | string | undefined): string {
  if (!value) return 'N/A'
  if (typeof value === 'string') return value
  const date = new Date(value * (value < 1e12 ? 1000 : 1))
  return date.toLocaleString()
}

async function copyId(): Promise<void> {
  await copy(String(props.details.id))
}
</script>

<template>
  <ElCard
    shadow="never"
    class="deployment-card"
  >
    <template #header>
      <div class="deployment-card__header">
        <i class="i-ep-monitor" />
        <span>{{ t('deployment.details.basicInfo') }}</span>
      </div>
    </template>
    <ElDescriptions
      :column="1"
      size="default"
      border
    >
      <ElDescriptionsItem :label="t('deployment.details.containerName')">
        <div class="deployment-card__name">
          <span class="deployment-card__name-text">{{ details.deployment_name || details.container_name || details.id }}</span>
          <el-button
            size="small"
            link
            @click="copyId"
          >
            <i class="i-ep-copy-document" />
          </el-button>
        </div>
      </ElDescriptionsItem>
      <ElDescriptionsItem :label="t('deployment.details.containerId')">
        <span class="deployment-card__mono">{{ details.id }}</span>
      </ElDescriptionsItem>
      <ElDescriptionsItem :label="t('deployment.details.status')">
        <StatusBadge
          :label="t(statusConfig().i18nKey)"
          :variant="statusConfig().variant"
          :show-dot="true"
        />
      </ElDescriptionsItem>
      <ElDescriptionsItem :label="t('deployment.details.createdAt')">
        {{ formatTimestamp(details.created_at) }}
      </ElDescriptionsItem>
    </ElDescriptions>
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

  &__name {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
  }

  &__name-text {
    font-weight: 500;
  }

  &__mono {
    font-family: var(--el-font-family-monospace, monospace);
    color: var(--el-text-color-secondary);
  }
}
</style>
