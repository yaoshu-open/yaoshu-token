<script setup lang="ts">
/**
 * 部署详情 - 容器实例列表 Card。
 */
import { useI18n } from 'vue-i18n'
import { DEPLOYMENT_STATUS_CONFIG } from '@/api/deployment/constants'
import type { DeploymentContainer } from '@/api/deployment/types'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import StatusBadge from '@/components/StatusBadge.vue'

defineProps<{
  containers: DeploymentContainer[]
  loading: boolean
  selected: string | null
}>()

const emit = defineEmits<{
  (e: 'select', containerId: string): void
  (e: 'open-url', url: string): void
}>()

const { t } = useI18n()

function formatTimestamp(value: number | string | undefined): string {
  if (!value) return '--'
  if (typeof value === 'string') return value
  const date = new Date(value * (value < 1e12 ? 1000 : 1))
  return date.toLocaleString()
}

function statusConfig(status: string | undefined) {
  const key = String(status ?? '').toLowerCase()
  return DEPLOYMENT_STATUS_CONFIG[key] ?? { i18nKey: `deployment.status.${key}`, variant: 'neutral' as const }
}
</script>

<template>
  <ElCard
    shadow="never"
    class="deployment-card"
  >
    <template #header>
      <div class="deployment-card__header">
        <i class="i-lucide-server" />
        <span>{{ t('deployment.details.containerInstances') }}</span>
      </div>
    </template>

    <LoadingState
      v-if="loading"
      :message="t('deployment.details.loadingContainers')"
      inline
    />
    <EmptyState
      v-else-if="containers.length === 0"
      :title="t('deployment.details.noContainers')"
      bordered
    />
    <div
      v-else
      class="deployment-card__list"
    >
      <div
        v-for="ctr in containers"
        :key="ctr.container_id"
        class="deployment-card__item"
        :class="{ 'deployment-card__item--selected': selected === ctr.container_id }"
        @click="emit('select', ctr.container_id)"
      >
        <div class="deployment-card__item-main">
          <div class="deployment-card__item-id">
            {{ ctr.container_id }}
          </div>
          <div class="deployment-card__item-meta">
            {{ t('deployment.details.device') }} {{ ctr.device_id || '--' }} ·
            {{ t('deployment.details.status') }} {{ ctr.status || '--' }}
          </div>
          <div class="deployment-card__item-meta">
            {{ t('deployment.details.createdAt') }}: {{ formatTimestamp(ctr.created_at) }}
          </div>
        </div>
        <div class="deployment-card__item-side">
          <StatusBadge
            :label="t(statusConfig(ctr.status).i18nKey)"
            :variant="statusConfig(ctr.status).variant"
          />
          <el-tag size="small">
            {{ t('deployment.details.gpuPerContainer') }}: {{ ctr.gpus_per_container ?? '--' }}
          </el-tag>
          <el-button
            v-if="ctr.public_url"
            size="small"
            link
            type="primary"
            @click.stop="emit('open-url', ctr.public_url!)"
          >
            <i class="i-ep-link" />
            {{ t('deployment.details.visitContainer') }}
          </el-button>
        </div>
        <div
          v-if="ctr.events && ctr.events.length > 0"
          class="deployment-card__events"
        >
          <div class="deployment-card__events-title">
            {{ t('deployment.details.recentEvents') }}
          </div>
          <div class="deployment-card__events-list">
            <div
              v-for="(event, idx) in ctr.events"
              :key="`${ctr.container_id}-${event.time}-${idx}`"
              class="deployment-card__event-item"
            >
              <span class="deployment-card__event-time">{{ formatTimestamp(event.time) }}</span>
              <span class="deployment-card__event-msg">{{ event.message || '--' }}</span>
            </div>
          </div>
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

  &__list {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-2);
  }

  &__item {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-2);
    padding: var(--ys-spacing-3);
    cursor: pointer;
    background: var(--el-fill-color-light);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: var(--el-border-radius-base);
    transition: border-color 0.15s;

    &--selected {
      border-color: var(--el-color-primary);
    }

    &:hover {
      border-color: var(--el-color-primary-light-5);
    }
  }

  &__item-main {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  &__item-id {
    font-family: var(--el-font-family-monospace, monospace);
    font-weight: 500;
  }

  &__item-meta {
    font-size: var(--el-font-size-small);
    color: var(--el-text-color-secondary);
  }

  &__item-side {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    align-items: center;
  }

  &__events {
    padding: var(--ys-spacing-2);
    background: var(--el-bg-color);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: var(--el-border-radius-base);
  }

  &__events-title {
    margin-bottom: 4px;
    font-size: var(--el-font-size-small);
    color: var(--el-text-color-secondary);
  }

  &__events-list {
    display: flex;
    flex-direction: column;
    gap: 2px;
    max-height: 128px;
    overflow-y: auto;
  }

  &__event-item {
    display: flex;
    gap: var(--ys-spacing-2);
    font-family: var(--el-font-family-monospace, monospace);
    font-size: var(--el-font-size-extra-small);
  }

  &__event-time {
    min-width: 140px;
    color: var(--el-text-color-secondary);
  }

  &__event-msg {
    flex: 1;
    word-break: break-all;
  }
}
</style>
