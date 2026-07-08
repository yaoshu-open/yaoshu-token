<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { VALID_PERIODS, PERIOD_LABEL_KEYS } from '@/api/rankings/constants'
import type { RankingPeriod } from '@/api/rankings/types'

/**
 * 排行榜 Hero 区：标题 + 副标题 + period 下划线 tabs。
 */
interface Props {
  period: RankingPeriod
}
defineProps<Props>()

const emit = defineEmits<{
  (e: 'update:period', value: RankingPeriod): void
}>()

const { t } = useI18n()
</script>

<template>
  <section class="rankings-hero">
    <div class="rankings-hero__head">
      <h1 class="rankings-hero__title">
        {{ t('rankings.title') }}
      </h1>
      <p class="rankings-hero__subtitle">
        {{ t('rankings.subtitle') }}
      </p>
    </div>

    <div
      class="rankings-hero__tabs"
      role="tablist"
      :aria-label="t('rankings.period.label')"
    >
      <button
        v-for="p in VALID_PERIODS"
        :key="p"
        type="button"
        role="tab"
        :aria-selected="period === p"
        :class="['rankings-hero__tab', { 'is-active': period === p }]"
        @click="emit('update:period', p)"
      >
        {{ t(PERIOD_LABEL_KEYS[p]) }}
      </button>
    </div>
  </section>
</template>

<style scoped lang="scss">
.rankings-hero {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;

  &__head {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
  }

  &__title {
    margin: 0;
    font-size: clamp(1.75rem, 4vw, 2.5rem);
    font-weight: 700;
    line-height: 1.15;
    color: var(--el-text-color-primary);
    letter-spacing: -0.02em;
  }

  &__subtitle {
    max-width: 42rem;
    margin: 0;
    font-size: 0.875rem;
    color: var(--el-text-color-secondary);
  }

  &__tabs {
    display: flex;
    align-items: center;
    border-bottom: 1px solid var(--el-border-color);
  }

  &__tab {
    position: relative;
    padding: 0.5rem 0.75rem;
    margin-bottom: -1px;
    font-size: 0.875rem;
    font-weight: 500;
    color: var(--el-text-color-secondary);
    cursor: pointer;
    background: transparent;
    border: none;
    transition: color 0.15s;

    &:hover {
      color: var(--el-text-color-primary);
    }

    &.is-active {
      color: var(--el-text-color-primary);

      &::after {
        opacity: 1;
      }
    }

    &::after {
      position: absolute;
      right: 0.75rem;
      bottom: -1px;
      left: 0.75rem;
      height: 2px;
      content: '';
      background: var(--el-text-color-primary);
      border-radius: 1px;
      opacity: 0;
      transition: opacity 0.15s;
    }
  }
}
</style>
