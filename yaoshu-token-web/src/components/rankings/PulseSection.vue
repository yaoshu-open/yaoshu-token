<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import VendorIcon from '@/components/common/VendorIcon.vue'
import ModelLink from './ModelLink.vue'
import VendorLink from './VendorLink.vue'
import type { RankingMover } from '@/api/rankings/types'

/**
 * 排名涨跌榜：上涨与下降双卡片。
 */
interface Props {
  movers: RankingMover[]
  droppers: RankingMover[]
}
defineProps<Props>()

const { t } = useI18n()
</script>

<template>
  <section class="pulse-section">
    <!-- Trending up -->
    <div class="pulse-section__card">
      <header class="pulse-section__card-head">
        <h3 class="pulse-section__card-title">
          <i class="i-lucide-trending-up pulse-section__card-icon pulse-section__card-icon--up" />
          {{ t('rankings.trendingUp') }}
        </h3>
        <p class="pulse-section__card-desc">
          {{ t('rankings.trendingUpDesc') }}
        </p>
      </header>
      <div class="pulse-section__card-body">
        <p
          v-if="movers.length === 0"
          class="pulse-section__empty"
        >
          {{ t('rankings.noClimbers') }}
        </p>
        <ul
          v-else
          class="pulse-section__list"
        >
          <li
            v-for="row in movers"
            :key="row.modelName"
            class="pulse-section__row"
          >
            <VendorIcon
              :vendor="row.vendor"
              :vendor-icon="row.vendorIcon"
              :size="20"
            />
            <div class="pulse-section__info">
              <ModelLink
                :model-name="row.modelName"
                class="pulse-section__model"
              >
                {{ row.modelName }}
              </ModelLink>
              <p class="pulse-section__meta">
                #{{ row.currentRank }} ·
                <VendorLink :vendor="row.vendor">
                  {{ row.vendor.toLowerCase() }}
                </VendorLink>
              </p>
            </div>
            <span class="pulse-section__delta pulse-section__delta--up">
              <i class="i-lucide-arrow-up-right" />
              {{ Math.abs(row.rankDelta) }}
            </span>
          </li>
        </ul>
      </div>
    </div>

    <!-- Trending down -->
    <div class="pulse-section__card">
      <header class="pulse-section__card-head">
        <h3 class="pulse-section__card-title">
          <i class="i-lucide-trending-down pulse-section__card-icon pulse-section__card-icon--down" />
          {{ t('rankings.trendingDown') }}
        </h3>
        <p class="pulse-section__card-desc">
          {{ t('rankings.trendingDownDesc') }}
        </p>
      </header>
      <div class="pulse-section__card-body">
        <p
          v-if="droppers.length === 0"
          class="pulse-section__empty"
        >
          {{ t('rankings.noDrops') }}
        </p>
        <ul
          v-else
          class="pulse-section__list"
        >
          <li
            v-for="row in droppers"
            :key="row.modelName"
            class="pulse-section__row"
          >
            <VendorIcon
              :vendor="row.vendor"
              :vendor-icon="row.vendorIcon"
              :size="20"
            />
            <div class="pulse-section__info">
              <ModelLink
                :model-name="row.modelName"
                class="pulse-section__model"
              >
                {{ row.modelName }}
              </ModelLink>
              <p class="pulse-section__meta">
                #{{ row.currentRank }} ·
                <VendorLink :vendor="row.vendor">
                  {{ row.vendor.toLowerCase() }}
                </VendorLink>
              </p>
            </div>
            <span class="pulse-section__delta pulse-section__delta--down">
              <i class="i-lucide-arrow-down-right" />
              {{ Math.abs(row.rankDelta) }}
            </span>
          </li>
        </ul>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.pulse-section {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1rem;

  @media (width >= 1024px) {
    grid-template-columns: 1fr 1fr;
  }

  &__card {
    overflow: hidden;
    background: var(--el-bg-color);
    border: 1px solid var(--el-border-color);
    border-radius: var(--ys-radius-md);
  }

  &__card-head {
    padding: 0.75rem 1rem;
    border-bottom: 1px solid var(--el-border-color);
  }

  &__card-title {
    display: inline-flex;
    gap: 0.5rem;
    align-items: center;
    margin: 0;
    font-size: 0.875rem;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  &__card-icon {
    font-size: 1rem;

    &--up {
      color: #10b981;
    }

    &--down {
      color: #f43f5e;
    }
  }

  &__card-desc {
    margin: 0.125rem 0 0;
    font-size: 0.75rem;
    color: var(--el-text-color-secondary);
  }

  &__card-body {
    padding: 0.25rem 0;
  }

  &__list {
    padding: 0;
    margin: 0;
    list-style: none;
  }

  &__row {
    display: flex;
    gap: 0.75rem;
    align-items: center;
    padding: 0.5rem 1rem;
  }

  &__info {
    flex: 1;
    min-width: 0;
  }

  &__model {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    font-family: var(--uno-font-mono, ui-monospace, monospace);
    font-size: 0.75rem;
    font-weight: 500;
    color: var(--el-text-color-primary);
    white-space: nowrap;
  }

  &__meta {
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: 0.6875rem;
    color: var(--el-text-color-secondary);
    white-space: nowrap;
  }

  &__delta {
    display: inline-flex;
    flex-shrink: 0;
    gap: 0.125rem;
    align-items: center;
    font-family: var(--uno-font-mono, ui-monospace, monospace);
    font-size: 0.75rem;
    font-weight: 600;
    font-variant-numeric: tabular-nums;

    &--up {
      color: #059669;

      :global(.dark) & {
        color: #34d399;
      }
    }

    &--down {
      color: #e11d48;

      :global(.dark) & {
        color: #fb7185;
      }
    }
  }

  &__empty {
    padding: 1.5rem 1rem;
    font-size: 0.75rem;
    color: var(--el-text-color-secondary);
    text-align: center;
  }
}
</style>
