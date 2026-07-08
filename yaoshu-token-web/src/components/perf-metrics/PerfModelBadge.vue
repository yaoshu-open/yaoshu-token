<script setup lang="ts">
import { computed } from 'vue'
import type { PerfModelSummary } from '@/api/perf-metrics/types'
import { formatUptimePct, getSuccessRateLevel } from '@/utils/perfFormat'

interface Props {
  model: PerfModelSummary
}

const props = defineProps<Props>()

const level = computed(() => getSuccessRateLevel(props.model.successRate))
const rateText = computed(() => formatUptimePct(props.model.successRate))
</script>

<template>
  <span class="perf-model-badge">
    <span class="perf-model-badge__name">{{ model.modelName }}</span>
    <span
      class="perf-model-badge__dot"
      :class="`perf-model-badge__dot--${level}`"
    />
    <span
      class="perf-model-badge__rate"
      :class="`perf-model-badge__rate--${level}`"
    >
      {{ rateText }}
    </span>
  </span>
</template>

<style scoped lang="scss">
.perf-model-badge {
  display: inline-flex;
  gap: 0.375rem;
  align-items: center;
  padding: 0.25rem 0.625rem;
  background: var(--el-fill-color-light);
  border-radius: 9999px;

  &__name {
    max-width: 10rem;
    overflow: hidden;
    text-overflow: ellipsis;
    font-family: var(--el-font-family-mono, monospace);
    font-size: 11px;
    white-space: nowrap;
  }

  &__dot {
    flex-shrink: 0;
    width: 0.375rem;
    height: 0.375rem;
    border-radius: 9999px;

    &--success {
      background: var(--el-color-success);
    }

    &--warning {
      background: var(--el-color-warning);
    }

    &--danger {
      background: var(--el-color-danger);
    }

    &--unknown {
      background: var(--el-text-color-secondary);
    }
  }

  &__rate {
    font-family: var(--el-font-family-mono, monospace);
    font-size: 11px;
    font-weight: 600;
    font-variant-numeric: tabular-nums;

    &--success {
      color: var(--el-color-success);
    }

    &--warning {
      color: var(--el-color-warning);
    }

    &--danger {
      color: var(--el-color-danger);
    }

    &--unknown {
      color: var(--el-text-color-secondary);
    }
  }
}
</style>
