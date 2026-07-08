<script setup lang="ts">
import { computed } from 'vue'

interface StatCardProps {
  title: string
  value: string
  description?: string
  icon?: string
  tone?: 'rose' | 'teal' | 'gray'
  sparkline?: number[]
  loading?: boolean
}

const props = withDefaults(defineProps<StatCardProps>(), {
  tone: 'gray',
  loading: false,
})

const toneClass = computed(() => `stat-card--${props.tone}`)

const sparklinePath = computed(() => {
  if (!props.sparkline || props.sparkline.length < 2) return ''
  const max = Math.max(...props.sparkline, 1)
  const min = Math.min(...props.sparkline, 0)
  const range = max - min || 1
  const width = 100
  const height = 32
  const step = width / (props.sparkline.length - 1)
  return props.sparkline
    .map((v, i) => {
      const x = i * step
      const y = height - ((v - min) / range) * height
      return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
})
</script>

<template>
  <div
    class="stat-card"
    :class="toneClass"
  >
    <div class="stat-card__header">
      <span class="stat-card__title">{{ title }}</span>
      <ElIcon
        v-if="icon"
        class="stat-card__icon"
      >
        <component :is="icon" />
      </ElIcon>
    </div>
    <div class="stat-card__body">
      <ElSkeleton
        v-if="loading"
        :rows="1"
        animated
        style="width: 120px"
      />
      <span
        v-else
        class="stat-card__value"
      >{{ value }}</span>
    </div>
    <div
      v-if="description"
      class="stat-card__desc"
    >
      {{ description }}
    </div>
    <div
      v-if="sparklinePath"
      class="stat-card__sparkline"
    >
      <svg
        viewBox="0 0 100 32"
        preserveAspectRatio="none"
      >
        <path
          :d="sparklinePath"
          fill="none"
          stroke="currentColor"
          stroke-width="1.5"
        />
      </svg>
    </div>
  </div>
</template>

<style scoped lang="scss">
.stat-card {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-2);
  padding: var(--ys-spacing-5);
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--ys-radius-lg);
  transition: border-color 0.2s;

  &:hover {
    border-color: var(--el-border-color);
  }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__title {
    font-size: var(--ys-font-size-sm);
    color: var(--el-text-color-secondary);
  }

  &__icon {
    font-size: var(--ys-font-size-lg);
    color: var(--el-text-color-placeholder);
  }

  &__value {
    font-size: var(--ys-font-size-3xl);
    font-weight: 600;
    font-variant-numeric: tabular-nums;
    color: var(--el-text-color-primary);
    letter-spacing: -0.025em;
  }

  &__desc {
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-placeholder);
  }

  &__sparkline {
    width: 100%;
    height: 32px;
    margin-top: 4px;

    svg {
      width: 100%;
      height: 100%;
    }
  }

  &--rose {
    .stat-card__sparkline {
      color: var(--el-color-danger);
    }
  }

  &--teal {
    .stat-card__sparkline {
      color: var(--el-color-success);
    }
  }

  &--gray {
    .stat-card__sparkline {
      color: var(--el-text-color-placeholder);
    }
  }
}
</style>
