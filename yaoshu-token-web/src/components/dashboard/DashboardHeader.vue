<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Refresh } from '@element-plus/icons-vue'
import type { UserInfo } from '@/api/user/types'

interface DashboardHeaderProps {
  userInfo: UserInfo | null
  loading?: boolean
}

const props = defineProps<DashboardHeaderProps>()
const emit = defineEmits<{ (e: 'refresh'): void }>()
const { t } = useI18n()

const greetingKey = computed(() => {
  const h = new Date().getHours()
  if (h >= 5 && h <= 11) return 'dashboard.greeting.morning'
  if (h >= 12 && h <= 17) return 'dashboard.greeting.afternoon'
  if (h >= 18 && h <= 22) return 'dashboard.greeting.evening'
  return 'dashboard.greeting.night'
})

const greetingEmoji = computed(() => {
  const h = new Date().getHours()
  if (h >= 5 && h <= 11) return '☀️'
  if (h >= 12 && h <= 17) return '🌤️'
  if (h >= 18 && h <= 22) return '🌙'
  return '🌃'
})

const displayName = computed(() => {
  return props.userInfo?.displayName || props.userInfo?.username || ''
})
</script>

<template>
  <div class="dashboard-header">
    <div class="dashboard-header__text">
      <h1 class="dashboard-header__title">
        {{ greetingEmoji }} {{ t(greetingKey) }}<span v-if="displayName">, {{ displayName }}</span>
      </h1>
      <p class="dashboard-header__subtitle">
        {{ t('dashboard.subtitle') }}
      </p>
    </div>
    <slot name="actions">
      <ElButton
        :icon="Refresh"
        :loading="loading"
        circle
        @click="emit('refresh')"
      />
    </slot>
  </div>
</template>

<style scoped lang="scss">
.dashboard-header {
  display: flex;
  gap: var(--ys-spacing-4);
  align-items: center;
  justify-content: space-between;

  &__text {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-1);
  }

  &__title {
    margin: 0;
    font-size: var(--ys-font-size-2xl);
    font-weight: 600;
    color: var(--el-text-color-primary);
    letter-spacing: -0.025em;
  }

  &__subtitle {
    margin: 0;
    font-size: var(--ys-font-size-base);
    color: var(--el-text-color-secondary);
  }
}
</style>
