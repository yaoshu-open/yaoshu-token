<script setup lang="ts">
import { computed } from 'vue'

type StatusVariant =
  | 'success'
  | 'warning'
  | 'danger'
  | 'info'
  | 'neutral'
  | 'primary'

const props = withDefaults(
  defineProps<{
    label?: string
    variant?: StatusVariant
    size?: 'sm' | 'md' | 'lg'
    showDot?: boolean
    pulse?: boolean
    icon?: string
  }>(),
  {
    label: undefined,
    variant: 'neutral',
    size: 'sm',
    showDot: false,
    pulse: false,
    icon: undefined,
  },
)

const variantClass = computed(() => `status-badge--${props.variant}`)
const sizeClass = computed(() => `status-badge--${props.size}`)
</script>

<template>
  <span
    class="status-badge"
    :class="[variantClass, sizeClass, { 'status-badge--pulse': pulse }]"
  >
    <span
      v-if="showDot"
      class="status-badge__dot"
      :class="`status-badge__dot--${variant}`"
    />
    <i
      v-if="icon"
      :class="icon"
      class="status-badge__icon"
    />
    <span
      v-if="label || $slots.default"
      class="status-badge__label"
    >
      <slot>{{ label }}</slot>
    </span>
  </span>
</template>

<style scoped lang="scss">
.status-badge {
  display: inline-flex;
  gap: var(--ys-spacing-1);
  align-items: center;
  max-width: 100%;
  font-weight: 500;
  white-space: nowrap;
  border-radius: 9999px;
  transition: color 0.15s;

  &--sm {
    height: 20px;
    padding: 0 6px;
    font-size: var(--ys-font-size-xs);
    line-height: 1;
  }

  &--md {
    height: 22px;
    padding: 0 var(--ys-spacing-2);
    font-size: var(--ys-font-size-xs);
    line-height: 1;
  }

  &--lg {
    gap: 6px;
    height: 26px;
    padding: 0 10px;
    font-size: var(--ys-font-size-xs);
    line-height: 1;
  }

  &--success {
    color: var(--el-color-success);
    background: var(--el-color-success-light-9);
  }

  &--warning {
    color: var(--el-color-warning);
    background: var(--el-color-warning-light-9);
  }

  &--danger {
    color: var(--el-color-danger);
    background: var(--el-color-danger-light-9);
  }

  &--info {
    color: var(--el-color-info);
    background: var(--el-color-info-light-9);
  }

  &--primary {
    color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
  }

  &--neutral {
    color: var(--el-text-color-secondary);
    background: var(--el-fill-color-light);
  }

  &--pulse {
    animation: status-badge-pulse 2s ease-in-out infinite;
  }

  &__dot {
    display: inline-block;
    flex-shrink: 0;
    width: 6px;
    height: 6px;
    border-radius: 50%;

    &--success { background: var(--el-color-success); }
    &--warning { background: var(--el-color-warning); }
    &--danger { background: var(--el-color-danger); }
    &--info { background: var(--el-color-info); }
    &--primary { background: var(--el-color-primary); }
    &--neutral { background: var(--el-text-color-secondary); }
  }

  &__icon {
    flex-shrink: 0;
    font-size: var(--ys-font-size-base);
  }

  &__label {
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

@keyframes status-badge-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}
</style>
