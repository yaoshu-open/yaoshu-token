<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElSkeleton } from 'element-plus'
import ErrorState from '@/components/ErrorState.vue'
import RankingsHero from '@/components/rankings/RankingsHero.vue'
import ModelsSection from '@/components/rankings/ModelsSection.vue'
import MarketShareSection from '@/components/rankings/MarketShareSection.vue'
import PulseSection from '@/components/rankings/PulseSection.vue'
import { useRankings } from '@/composables/rankings/useRankings'
import { DEFAULT_PERIOD, VALID_PERIODS } from '@/api/rankings/constants'
import type { RankingPeriod } from '@/api/rankings/types'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

// period 从 URL query 读取并校验，非法值 fallback 默认周
const period = computed<RankingPeriod>(() => {
  const raw = route.query.period
  if (typeof raw === 'string' && (VALID_PERIODS as string[]).includes(raw)) {
    return raw as RankingPeriod
  }
  return DEFAULT_PERIOD
})

function handlePeriodChange(next: RankingPeriod) {
  router.replace({ query: { ...route.query, period: next } })
}

const { snapshot, loading, error, reload } = useRankings(period)

const errorMessage = computed(() =>
  error.value instanceof Error
    ? error.value.message
    : t('rankings.unableToLoadData')
)
</script>

<template>
  <div class="rankings-view">
    <!-- 装饰背景 -->
    <div
      aria-hidden="true"
      class="rankings-view__bg"
    />

    <div class="rankings-view__content">
      <RankingsHero
        :period="period"
        @update:period="handlePeriodChange"
      />

      <!-- 加载态 -->
      <div
        v-if="loading"
        class="rankings-view__loading"
      >
        <ElSkeleton
          :rows="8"
          animated
          class="rankings-view__skeleton"
        />
        <ElSkeleton
          :rows="6"
          animated
          class="rankings-view__skeleton"
        />
        <ElSkeleton
          :rows="4"
          animated
          class="rankings-view__skeleton"
        />
      </div>

      <!-- 错误/空态 -->
      <ErrorState
        v-else-if="!snapshot"
        :title="t('rankings.unableToLoad')"
        :description="errorMessage"
        @retry="reload"
      />

      <!-- 数据态 -->
      <template v-else>
        <ModelsSection
          :history="snapshot.modelsHistory"
          :rows="snapshot.models"
          :period="period"
        />
        <MarketShareSection
          :history="snapshot.vendorShareHistory"
          :rows="snapshot.vendors"
          :period="period"
        />
        <PulseSection
          :movers="snapshot.topMovers"
          :droppers="snapshot.topDroppers"
        />
      </template>
    </div>
  </div>
</template>

<style scoped lang="scss">
.rankings-view {
  position: relative;

  &__bg {
    position: absolute;
    inset: 0 0 auto;
    height: 600px;
    pointer-events: none;
    background:
      radial-gradient(ellipse 60% 50% at 20% 20%, color-mix(in srgb, var(--ys-color-primary) 80%, transparent) 0%, transparent 70%),
      radial-gradient(ellipse 50% 40% at 80% 15%, color-mix(in srgb, var(--ys-color-primary) 60%, transparent) 0%, transparent 70%),
      radial-gradient(ellipse 40% 35% at 50% 70%, color-mix(in srgb, var(--ys-color-secondary) 40%, transparent) 0%, transparent 70%);
    opacity: 0.2;
    mask-image: linear-gradient(to bottom, black 40%, transparent 100%);

    :global(.dark) & {
      opacity: 0.1;
    }
  }

  &__content {
    position: relative;
    display: flex;
    flex-direction: column;
    gap: 2rem;
    width: 100%;
    max-width: 1280px;
    padding: 4rem 0.75rem 2.5rem;
    margin: 0 auto;

    @media (width >= 640px) {
      padding: 5rem 1.5rem 3rem;
    }

    @media (width >= 1280px) {
      padding: 5rem 2rem 3rem;
    }
  }

  &__loading {
    display: flex;
    flex-direction: column;
    gap: 1.5rem;
  }

  &__skeleton {
    :deep(.el-skeleton__item) {
      height: 200px;
      border-radius: var(--ys-radius-lg);
    }
  }
}
</style>
