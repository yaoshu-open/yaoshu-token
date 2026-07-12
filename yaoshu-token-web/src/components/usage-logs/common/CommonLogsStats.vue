<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { View, Hide } from '@element-plus/icons-vue'
import { formatQuotaBilling } from '@/utils/currency'
import type { LogStatistics } from '@/api/usage-log/types'

interface CommonLogsStatsProps {
  stats: LogStatistics
  loading?: boolean
  sensitiveVisible: boolean
}

const props = defineProps<CommonLogsStatsProps>()
const emit = defineEmits<{ (e: 'toggle-sensitive'): void }>()
const { t } = useI18n()

const quotaText = computed(() => {
  if (!props.sensitiveVisible) return '••••'
  return formatQuotaBilling(props.stats.quota)
})

const rpmText = computed(() => new Intl.NumberFormat().format(props.stats.rpm))
const tpmText = computed(() => new Intl.NumberFormat().format(props.stats.tpm))
</script>

<template>
  <div class="common-logs-stats">
    <div class="common-logs-stats__badges">
      <span class="common-logs-stats__badge">
        <span class="common-logs-stats__dot common-logs-stats__dot--primary" />
        <span class="common-logs-stats__label">{{ t('usageLogs.stats.usage') }}</span>
        <ElSkeleton
          v-if="loading"
          :rows="0"
          animated
          style="width: 60px"
        />
        <span
          v-else
          class="common-logs-stats__value"
        >{{ quotaText }}</span>
      </span>
      <span class="common-logs-stats__badge">
        <span class="common-logs-stats__dot common-logs-stats__dot--danger" />
        <span class="common-logs-stats__label">{{ t('usageLogs.stats.rpm') }}</span>
        <span class="common-logs-stats__value">{{ rpmText }}</span>
      </span>
      <span class="common-logs-stats__badge">
        <span class="common-logs-stats__dot common-logs-stats__dot--info" />
        <span class="common-logs-stats__label">{{ t('usageLogs.stats.tpm') }}</span>
        <span class="common-logs-stats__value">{{ tpmText }}</span>
      </span>
    </div>
    <ElButton
      :icon="sensitiveVisible ? View : Hide"
      circle
      size="small"
      @click="emit('toggle-sensitive')"
    />
  </div>
</template>

<style scoped lang="scss">
.common-logs-stats {
  display: flex;
  gap: var(--ys-spacing-3);
  align-items: center;

  &__badges {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-2);
  }

  &__badge {
    display: inline-flex;
    gap: 6px;
    align-items: center;
    height: 28px;
    padding: 0 10px;
    font-size: var(--ys-font-size-xs);
    background: var(--el-fill-color-light);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: var(--ys-radius-base);
  }

  &__dot {
    width: 3px;
    height: 14px;
    border-radius: 2px;

    &--primary { background: var(--el-color-primary); }
    &--danger { background: var(--el-color-danger); }
    &--info { background: var(--el-text-color-placeholder); }
  }

  &__label {
    color: var(--el-text-color-secondary);
  }

  &__value {
    font-family: var(--el-font-family-mono, monospace);
    font-weight: 600;
    font-variant-numeric: tabular-nums;
    color: var(--el-text-color-primary);
  }
}
</style>
