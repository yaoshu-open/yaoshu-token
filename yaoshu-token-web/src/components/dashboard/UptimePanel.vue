<script setup lang="ts">
/**
 * 服务可用性面板（PD-08 后续）。
 * 数据源：GET /api/uptime/status（独立端点，不通过 /api/status）。
 * 配置项：管理员通过系统设置 console_setting.uptime_kuma_groups 配置（JSON 数组）。
 */
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElIcon } from 'element-plus'
import EmptyState from '@/components/EmptyState.vue'
import { getUptimeStatus } from '@/api/uptime'
import type { UptimeGroupResult } from '@/api/uptime/types'

const { t } = useI18n()

const loading = ref(true)
const groups = ref<UptimeGroupResult[]>([])

async function fetchData() {
  loading.value = true
  try {
    groups.value = (await getUptimeStatus()) ?? []
  } catch {
    groups.value = []
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)

function statusColor(status: number): string {
  switch (status) {
    case 1: return '#10b981'
    case 0: return '#ef4444'
    case 2: return '#f59e0b'
    case 3: return '#94a3b8'
    default: return '#94a3b8'
  }
}

function statusText(status: number): string {
  switch (status) {
    case 1: return t('dashboard.uptime.statusUp')
    case 0: return t('dashboard.uptime.statusDown')
    case 2: return t('dashboard.uptime.statusWarning')
    case 3: return t('dashboard.uptime.statusMaintenance')
    default: return t('dashboard.uptime.statusUnknown')
  }
}

function formatUptime(uptime: number): string {
  return `${(uptime * 100).toFixed(2)}%`
}
</script>

<template>
  <div class="uptime-panel">
    <div class="uptime-panel__header">
      <ElIcon class="uptime-panel__icon">
        <i class="i-ep-aim" />
      </ElIcon>
      <span class="uptime-panel__title">{{ t('dashboard.uptime.title') }}</span>
      <ElIcon
        class="uptime-panel__refresh"
        :class="{ 'is-loading': loading }"
        @click="fetchData"
      >
        <i class="i-ep-refresh" />
      </ElIcon>
    </div>
    <div
      v-if="!loading && groups.length === 0"
      class="uptime-panel__empty"
    >
      <EmptyState :description="t('dashboard.uptime.empty')" />
    </div>
    <div
      v-else
      class="uptime-panel__groups"
    >
      <div
        v-for="(group, gIdx) in groups"
        :key="gIdx"
        class="uptime-panel__group"
      >
        <div class="uptime-panel__group-name">{{ group.categoryName }}</div>
        <ul class="uptime-panel__monitors">
          <li
            v-for="(monitor, mIdx) in group.monitors"
            :key="mIdx"
            class="uptime-panel__monitor"
          >
            <span
              class="uptime-panel__status-dot"
              :style="{ backgroundColor: statusColor(monitor.status) }"
            />
            <span class="uptime-panel__monitor-name">{{ monitor.name }}</span>
            <span class="uptime-panel__monitor-status">{{ statusText(monitor.status) }}</span>
            <span class="uptime-panel__monitor-uptime">{{ formatUptime(monitor.uptime) }}</span>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens' as *;

.uptime-panel {
  padding: $spacing-4;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: $radius-md;

  &__header {
    display: flex;
    gap: $spacing-2;
    align-items: center;
    margin-bottom: $spacing-3;
  }

  &__icon {
    font-size: var(--ys-font-size-lg);
    color: var(--el-text-color-secondary);
  }

  &__title {
    flex: 1;
    font-size: $font-size-base;
    font-weight: $font-weight-medium;
    color: var(--el-text-color-primary);
  }

  &__refresh {
    color: var(--el-text-color-secondary);
    cursor: pointer;
    transition: color 0.2s;

    &:hover {
      color: var(--el-color-primary);
    }

    &.is-loading {
      animation: rotate 1s linear infinite;
    }
  }

  &__empty {
    padding: $spacing-6 0;
  }

  &__groups {
    display: flex;
    flex-direction: column;
    gap: $spacing-3;
  }

  &__group-name {
    margin-bottom: $spacing-2;
    font-size: $font-size-sm;
    font-weight: $font-weight-semibold;
    color: var(--el-text-color-primary);
  }

  &__monitors {
    padding: 0;
    margin: 0;
    list-style: none;
  }

  &__monitor {
    display: flex;
    gap: $spacing-2;
    align-items: center;
    padding: $spacing-1 0;
    font-size: $font-size-sm;
  }

  &__status-dot {
    display: inline-block;
    flex-shrink: 0;
    width: 8px;
    height: 8px;
    border-radius: 50%;
  }

  &__monitor-name {
    flex: 1;
    color: var(--el-text-color-regular);
  }

  &__monitor-status {
    font-size: $font-size-xs;
    color: var(--el-text-color-secondary);
  }

  &__monitor-uptime {
    font-family: $font-family-mono;
    font-weight: $font-weight-medium;
    color: var(--el-text-color-primary);
  }
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
