<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import VendorIcon from '@/components/common/VendorIcon.vue'
import ModelLink from './ModelLink.vue'
import VendorLink from './VendorLink.vue'
import GrowthText from './GrowthText.vue'
import { formatTokens } from './format'
import type { ModelRanking } from '@/api/rankings/types'

/**
 * 双列模型排行榜列表。
 * 拆分 rows 为左右两列均匀分布，每行：排名 + 厂商图标 + 模型名 + 供应商 + Token 量 + 增长率。
 */
interface Props {
  rows: ModelRanking[]
  /** 密度变体：compact 用于分类内嵌；default 用于 Top Models 大区 */
  variant?: 'default' | 'compact'
  /** 可选截断（超出部分丢弃） */
  limit?: number
}
const props = withDefaults(defineProps<Props>(), {
  variant: 'default',
  limit: undefined
})

const { t } = useI18n()

const limited = computed(() =>
  props.limit ? props.rows.slice(0, props.limit) : props.rows
)
const half = computed(() => Math.ceil(limited.value.length / 2))
const left = computed(() => limited.value.slice(0, half.value))
const right = computed(() => limited.value.slice(half.value))
const compact = computed(() => props.variant === 'compact')
const iconSize = computed(() => (compact.value ? 20 : 22))
</script>

<template>
  <div
    v-if="limited.length"
    class="model-leaderboard"
  >
    <ul class="model-leaderboard__col">
      <li
        v-for="row in left"
        :key="row.modelName"
        class="model-leaderboard__row"
        :class="{ 'model-leaderboard__row--compact': compact }"
      >
        <span class="model-leaderboard__rank">{{ row.rank }}.</span>
        <VendorIcon
          :vendor="row.vendor"
          :vendor-icon="row.vendorIcon"
          :size="iconSize"
        />
        <div class="model-leaderboard__info">
          <ModelLink
            :model-name="row.modelName"
            :class="compact ? 'model-leaderboard__name--compact' : 'model-leaderboard__name'"
          >
            {{ row.modelName }}
          </ModelLink>
          <p class="model-leaderboard__vendor">
            by
            <VendorLink :vendor="row.vendor">
              {{ row.vendor.toLowerCase() }}
            </VendorLink>
          </p>
        </div>
        <div class="model-leaderboard__stats">
          <div :class="compact ? 'model-leaderboard__tokens--compact' : 'model-leaderboard__tokens'">
            {{ formatTokens(row.totalTokens) }}
            <span
              v-if="!compact"
              class="model-leaderboard__tokens-label"
            >{{ t('rankings.tokens') }}</span>
          </div>
          <GrowthText
            :value="row.growthPct"
            :class="compact ? 'model-leaderboard__growth--compact' : 'model-leaderboard__growth'"
          />
        </div>
      </li>
    </ul>
    <ul
      v-if="right.length"
      class="model-leaderboard__col"
    >
      <li
        v-for="row in right"
        :key="row.modelName"
        class="model-leaderboard__row"
        :class="{ 'model-leaderboard__row--compact': compact }"
      >
        <span class="model-leaderboard__rank">{{ row.rank }}.</span>
        <VendorIcon
          :vendor="row.vendor"
          :vendor-icon="row.vendorIcon"
          :size="iconSize"
        />
        <div class="model-leaderboard__info">
          <ModelLink
            :model-name="row.modelName"
            :class="compact ? 'model-leaderboard__name--compact' : 'model-leaderboard__name'"
          >
            {{ row.modelName }}
          </ModelLink>
          <p class="model-leaderboard__vendor">
            by
            <VendorLink :vendor="row.vendor">
              {{ row.vendor.toLowerCase() }}
            </VendorLink>
          </p>
        </div>
        <div class="model-leaderboard__stats">
          <div :class="compact ? 'model-leaderboard__tokens--compact' : 'model-leaderboard__tokens'">
            {{ formatTokens(row.totalTokens) }}
            <span
              v-if="!compact"
              class="model-leaderboard__tokens-label"
            >{{ t('rankings.tokens') }}</span>
          </div>
          <GrowthText
            :value="row.growthPct"
            :class="compact ? 'model-leaderboard__growth--compact' : 'model-leaderboard__growth'"
          />
        </div>
      </li>
    </ul>
  </div>
</template>

<style scoped lang="scss">
.model-leaderboard {
  display: grid;
  grid-template-columns: 1fr;
  gap: 0 2rem;

  @media (width >= 768px) {
    grid-template-columns: 1fr 1fr;
  }

  &__col {
    padding: 0;
    margin: 0;
    list-style: none;
  }

  &__row {
    display: flex;
    gap: 0.75rem;
    align-items: center;
    padding: 0.625rem 0;

    &--compact {
      padding: 0.5rem 0;
    }
  }

  &__rank {
    flex-shrink: 0;
    width: 1.5rem;
    font-family: var(--uno-font-mono, ui-monospace, monospace);
    font-size: 0.75rem;
    font-variant-numeric: tabular-nums;
    color: var(--el-text-color-secondary);
    text-align: right;
  }

  &__info {
    flex: 1;
    min-width: 0;
  }

  %model-leaderboard-name {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    font-family: var(--uno-font-mono, ui-monospace, monospace);
    font-size: 0.875rem;
    font-weight: 500;
    color: var(--el-text-color-primary);
    white-space: nowrap;
  }

  &__name {
    @extend %model-leaderboard-name;

    &--compact {
      @extend %model-leaderboard-name;

      font-size: 0.75rem;
    }
  }

  &__vendor {
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: 0.75rem;
    font-style: italic;
    color: var(--el-text-color-secondary);
    white-space: nowrap;

    :global(.dark) & {
      opacity: 0.8;
    }
  }

  &__stats {
    flex-shrink: 0;
    text-align: right;
  }

  %model-leaderboard-tokens {
    font-family: var(--uno-font-mono, ui-monospace, monospace);
    font-size: 0.875rem;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
    color: var(--el-text-color-primary);
  }

  &__tokens {
    @extend %model-leaderboard-tokens;

    &--compact {
      @extend %model-leaderboard-tokens;

      font-size: 0.75rem;
    }
  }

  &__tokens-label {
    font-weight: 400;
    color: var(--el-text-color-secondary);
  }

  &__growth {
    font-size: 0.6875rem;

    &--compact {
      font-size: 0.625rem;
    }
  }
}
</style>
