<script setup lang="ts">
/**
 * 部署列表表格行（status badge + 3 操作按钮）。
 */
import { useI18n } from 'vue-i18n'
import { DEPLOYMENT_STATUS_CONFIG } from '@/api/deployment/constants'
import type { DeploymentListItem } from '@/api/deployment/types'
import StatusBadge from '@/components/StatusBadge.vue'

const props = defineProps<{ deployment: DeploymentListItem }>()

const emit = defineEmits<{
  (e: 'viewDetails'): void
  (e: 'viewLogs'): void
  (e: 'extend'): void
  (e: 'syncToChannel'): void
  (e: 'rename'): void
}>()

const { t } = useI18n()

function statusConfig() {
  const key = String(props.deployment.status ?? '').toLowerCase()
  return DEPLOYMENT_STATUS_CONFIG[key] ?? { i18nKey: `deployment.status.${key}`, variant: 'neutral' as const }
}

function formatTimestamp(value: number | string | undefined): string {
  if (!value) return '--'
  if (typeof value === 'string') return value
  const date = new Date(value * (value < 1e12 ? 1000 : 1))
  return date.toLocaleString()
}
</script>

<template>
  <div class="deployment-row">
    <div class="deployment-row__main">
      <div class="deployment-row__name">
        {{ deployment.container_name || deployment.deployment_name || deployment.id }}
      </div>
      <div class="deployment-row__meta">
        <span>{{ t('deployment.list.id') }}: {{ deployment.id }}</span>
        <span v-if="deployment.hardware_name">· {{ deployment.hardware_name }}{{ deployment.total_gpus ? ` x${deployment.total_gpus}` : '' }}</span>
        <span v-if="deployment.time_remaining">· {{ t('deployment.list.remaining') }}: {{ deployment.time_remaining }}</span>
      </div>
      <div class="deployment-row__meta">
        {{ t('deployment.list.createdAt') }}: {{ formatTimestamp(deployment.created_at) }}
      </div>
    </div>
    <div class="deployment-row__side">
      <StatusBadge
        :label="t(statusConfig().i18nKey)"
        :variant="statusConfig().variant"
        :show-dot="true"
      />
      <div class="deployment-row__actions">
        <el-button
          size="small"
          link
          type="primary"
          @click="emit('viewDetails')"
        >
          <i class="i-ep-info-filled" />
          {{ t('deployment.list.actions.details') }}
        </el-button>
        <el-button
          size="small"
          link
          type="primary"
          @click="emit('viewLogs')"
        >
          <i class="i-ep-monitor" />
          {{ t('deployment.list.actions.logs') }}
        </el-button>
        <el-button
          size="small"
          link
          type="primary"
          @click="emit('extend')"
        >
          <i class="i-ep-time" />
          {{ t('deployment.list.actions.extend') }}
        </el-button>
        <el-button
          size="small"
          link
          type="success"
          @click="emit('syncToChannel')"
        >
          <i class="i-ep-share" />
          {{ t('deployment.list.actions.syncToChannel') }}
        </el-button>
        <el-button
          size="small"
          link
          type="primary"
          @click="emit('rename')"
        >
          <i class="i-ep-edit" />
          {{ t('deployment.list.actions.rename') }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.deployment-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: var(--ys-spacing-3);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);

  &__main {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  &__name {
    font-weight: 500;
  }

  &__meta {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-1);
    font-size: var(--el-font-size-small);
    color: var(--el-text-color-secondary);
  }

  &__side {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-2);
    align-items: center;
    justify-content: space-between;
  }

  &__actions {
    display: flex;
    gap: var(--ys-spacing-1);
    align-items: center;
  }
}
</style>
