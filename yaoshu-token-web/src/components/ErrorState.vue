<script setup lang="ts">
import { ElButton } from 'element-plus'
import { useI18n } from 'vue-i18n'

withDefaults(
  defineProps<{
    title?: string
    description?: string
    icon?: string
    showRetry?: boolean
  }>(),
  {
    title: undefined,
    description: undefined,
    icon: undefined,
    showRetry: true,
  },
)

const emit = defineEmits<{
  (e: 'retry'): void
}>()

const { t } = useI18n()
</script>

<template>
  <div class="error-state">
    <div class="error-state__icon">
      <i :class="icon ?? 'i-ep-warning-filled'" />
    </div>
    <h3 class="error-state__title">
      {{ title ?? t('common.error.title') }}
    </h3>
    <p
      v-if="description"
      class="error-state__desc"
    >
      {{ description }}
    </p>
    <div class="error-state__action">
      <ElButton
        v-if="showRetry"
        variant="default"
        size="small"
        @click="emit('retry')"
      >
        {{ t('common.retry') }}
      </ElButton>
      <slot name="action" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.error-state {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-3);
  align-items: center;
  justify-content: center;
  min-height: 300px;

  &__icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 48px;
    height: 48px;
    font-size: var(--ys-font-size-2xl);
    color: var(--el-color-danger);
    background: var(--el-color-danger-light-9);
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
