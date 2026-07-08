<script setup lang="ts">
import type { Component } from 'vue'
import type { SuccessRateLevel } from '@/utils/perfFormat'

interface Props {
  icon: Component
  label: string
  value: string
  hint?: string
  /** 值的语义配色（成功率卡用），默认无强调色 */
  intent?: SuccessRateLevel
}

withDefaults(defineProps<Props>(), {
  hint: undefined,
  intent: undefined
})
</script>

<template>
  <div class="perf-metric-card">
    <span class="perf-metric-card__label">
      <component
        :is="icon"
        class="perf-metric-card__icon"
      />
      {{ label }}
    </span>
    <span
      class="perf-metric-card__value"
      :class="intent && `perf-metric-card__value--${intent}`"
    >
      {{ value }}
    </span>
    <span
      v-if="hint"
      class="perf-metric-card__hint"
    >
      {{ hint }}
    </span>
  </div>
</template>

<style scoped lang="scss">
.perf-metric-card {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding: 0.625rem 0.75rem;
  background: var(--el-fill-color-light);
  border-radius: var(--ys-radius-lg);

  &__label {
    display: inline-flex;
    gap: 0.375rem;
    align-items: center;
    font-size: 11px;
    font-weight: 500;
    color: var(--el-text-color-secondary);
    text-transform: uppercase;
    letter-spacing: 0.05em;
  }

  &__icon {
    flex-shrink: 0;
    width: 0.75rem;
    height: 0.75rem;
  }

  &__value {
    font-family: var(--el-font-family-mono, monospace);
    font-size: 1rem;
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

  &__hint {
    font-size: 11px;
    color: var(--el-text-color-secondary);
  }
}
</style>
