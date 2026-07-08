<script setup lang="ts">
import { useI18n } from 'vue-i18n'

withDefaults(
  defineProps<{
    title?: string
    description?: string
    icon?: string
    bordered?: boolean
  }>(),
  {
    title: undefined,
    description: undefined,
    icon: undefined,
    bordered: false,
  },
)

const { t } = useI18n()
</script>

<template>
  <div
    class="empty-state"
    :class="{ 'empty-state--bordered': bordered }"
  >
    <div class="empty-state__icon">
      <i :class="icon ?? 'i-ep-data-line'" />
    </div>
    <h3 class="empty-state__title">
      {{ title ?? t('common.empty.title') }}
    </h3>
    <p
      v-if="description"
      class="empty-state__desc"
    >
      {{ description }}
    </p>
    <div
      v-if="$slots.action"
      class="empty-state__action"
    >
      <slot name="action" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.empty-state {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-3);
  align-items: center;
  justify-content: center;
  min-height: 300px;

  &--bordered {
    border: 1px solid var(--el-border-color-lighter);
    border-radius: var(--el-border-radius-base);
  }

  &__icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 48px;
    height: 48px;
    font-size: var(--ys-font-size-2xl);
    color: var(--el-text-color-secondary);
    background: var(--el-fill-color-light);
    border-radius: 50%;
  }

  &__title {
    margin: 0;
    font-size: var(--el-font-size-medium);
    font-weight: 500;
    color: var(--el-text-color-primary);
  }

  &__desc {
    max-width: 400px;
    margin: 0;
    font-size: var(--el-font-size-base);
    color: var(--el-text-color-secondary);
    text-align: center;
  }
}
</style>
