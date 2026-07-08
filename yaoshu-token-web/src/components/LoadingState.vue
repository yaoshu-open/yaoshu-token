<script setup lang="ts">
import { useI18n } from 'vue-i18n'

withDefaults(
  defineProps<{
    message?: string
    size?: 'sm' | 'md' | 'lg'
    inline?: boolean
  }>(),
  {
    message: undefined,
    size: 'md',
    inline: false,
  },
)

const { t } = useI18n()

const sizeMap = {
  sm: '16px',
  md: '24px',
  lg: '32px',
} as const
</script>

<template>
  <!-- 内联模式 -->
  <span
    v-if="inline"
    class="loading-state loading-state--inline"
  >
    <i
      class="i-ep-loading loading-state__spinner"
      :style="{ fontSize: sizeMap[size] }"
    />
    <span
      v-if="message"
      class="loading-state__text"
    >{{ message }}</span>
  </span>

  <!-- 居中模式 -->
  <div
    v-else
    class="loading-state"
  >
    <i
      class="i-ep-loading loading-state__spinner"
      :style="{ fontSize: sizeMap[size] }"
    />
    <p class="loading-state__text">
      {{ message ?? t('common.loading') }}
    </p>
  </div>
</template>

<style scoped lang="scss">
.loading-state {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-3);
  align-items: center;
  justify-content: center;
  min-height: 200px;

  &--inline {
    display: inline-flex;
    flex-direction: row;
    gap: var(--ys-spacing-2);
    min-height: auto;
  }

  &__spinner {
    color: var(--el-color-primary);
    animation: spin 1s linear infinite;
  }

  &__text {
    margin: 0;
    font-size: var(--el-font-size-base);
    color: var(--el-text-color-secondary);
  }
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}
</style>
