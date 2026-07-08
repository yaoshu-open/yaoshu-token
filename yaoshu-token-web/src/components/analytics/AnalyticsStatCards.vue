<script setup lang="ts">
import { markRaw } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElSkeleton, ElIcon } from 'element-plus'
import { Coin, DataLine, Collection, Timer, Odometer } from '@element-plus/icons-vue'
import type { ModelStatCards } from '@/composables/analytics/useModelAnalytics'
import { formatQuotaBilling } from '@/utils/currency'

const props = defineProps<{
  stats: ModelStatCards
  loading: boolean
}>()

const { t } = useI18n()

// 配额 → 定价货币（强制显示货币符号，CNY 模式 ¥）
function formatQuota(quota: number): string {
  return formatQuotaBilling(quota, { digitsLarge: 2, digitsSmall: 2, abbreviate: false })
}

function formatNumber(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`
  return String(n)
}

// 5 张卡片 tone 映射 --ys-chart-1~5，确保与下方图表视觉一致
interface StatCardConfig {
  key: string
  labelKey: string
  icon: ReturnType<typeof markRaw>
  tone: 1 | 2 | 3 | 4 | 5
}

const cardConfigs: StatCardConfig[] = [
  { key: 'quota', labelKey: 'analytics.stat.quota', icon: markRaw(Coin), tone: 1 },
  { key: 'count', labelKey: 'analytics.stat.count', icon: markRaw(DataLine), tone: 2 },
  { key: 'tokens', labelKey: 'analytics.stat.tokens', icon: markRaw(Collection), tone: 3 },
  { key: 'rpm', labelKey: 'analytics.stat.rpm', icon: markRaw(Timer), tone: 4 },
  { key: 'tpm', labelKey: 'analytics.stat.tpm', icon: markRaw(Odometer), tone: 5 },
]

function getValue(key: string): string {
  const s = props.stats
  switch (key) {
    case 'quota': return formatQuota(s.totalQuota)
    case 'count': return formatNumber(s.totalCount)
    case 'tokens': return formatNumber(s.totalTokens)
    case 'rpm': return formatNumber(s.rpm)
    case 'tpm': return formatNumber(s.tpm)
    default: return ''
  }
}
</script>

<template>
  <div class="analytics-stat-cards">
    <div class="analytics-stat-cards__grid">
      <div
        v-for="card in cardConfigs"
        :key="card.key"
        class="analytics-stat-cards__item"
        :class="`analytics-stat-cards__item--tone-${card.tone}`"
      >
        <el-skeleton
          v-if="loading"
          :rows="0"
          animated
          class="analytics-stat-cards__skeleton"
        >
          <template #template>
            <div class="analytics-stat-cards__skeleton-inner">
              <div class="analytics-stat-cards__skeleton-icon" />
              <div class="analytics-stat-cards__skeleton-text">
                <div class="analytics-stat-cards__skeleton-label" />
                <div class="analytics-stat-cards__skeleton-value" />
              </div>
            </div>
          </template>
        </el-skeleton>
        <template v-else>
          <div class="analytics-stat-cards__header">
            <span class="analytics-stat-cards__label">{{ t(card.labelKey) }}</span>
            <span class="analytics-stat-cards__icon-wrap">
              <el-icon :size="18">
                <component :is="card.icon" />
              </el-icon>
            </span>
          </div>
          <div class="analytics-stat-cards__value">{{ getValue(card.key) }}</div>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens' as *;

.analytics-stat-cards {
  &__grid {
    display: grid;
    grid-template-columns: repeat(5, 1fr);
    gap: $spacing-3;

    @media (width <= 1024px) {
      grid-template-columns: repeat(3, 1fr);
    }

    @media (width <= 640px) {
      grid-template-columns: repeat(2, 1fr);
    }
  }

  &__item {
    display: flex;
    flex-direction: column;
    gap: $spacing-2;
    padding: $spacing-5;
    background: var(--el-bg-color-overlay);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: var(--ys-radius-lg);
    transition: border-color 0.2s, box-shadow 0.2s;

    &:hover {
      border-color: var(--el-border-color);
      box-shadow: var(--ys-shadow-sm);
    }
  }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__label {
    font-size: $font-size-xs;
    color: var(--el-text-color-secondary);
  }

  &__icon-wrap {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    border-radius: var(--ys-radius-md);
  }

  &__value {
    font-size: $font-size-2xl;
    font-weight: $font-weight-semibold;
    font-variant-numeric: tabular-nums;
    line-height: 1.2;
    color: var(--el-text-color-primary);
    letter-spacing: -0.025em;
  }

  // tone 色系映射图表色板，确保统计卡片与下方图表视觉联动
  // color-mix 生成半透明背景，亮/暗模式 + 开源/商业版自动跟随 CSS 变量
  &__item--tone-1 .analytics-stat-cards__icon-wrap {
    color: var(--ys-chart-1);
    background: color-mix(in srgb, var(--ys-chart-1) 12%, transparent);
  }

  &__item--tone-2 .analytics-stat-cards__icon-wrap {
    color: var(--ys-chart-2);
    background: color-mix(in srgb, var(--ys-chart-2) 12%, transparent);
  }

  &__item--tone-3 .analytics-stat-cards__icon-wrap {
    color: var(--ys-chart-3);
    background: color-mix(in srgb, var(--ys-chart-3) 12%, transparent);
  }

  &__item--tone-4 .analytics-stat-cards__icon-wrap {
    color: var(--ys-chart-4);
    background: color-mix(in srgb, var(--ys-chart-4) 12%, transparent);
  }

  &__item--tone-5 .analytics-stat-cards__icon-wrap {
    color: var(--ys-chart-5);
    background: color-mix(in srgb, var(--ys-chart-5) 12%, transparent);
  }

  // 骨架屏
  &__skeleton {
    padding: 0;
  }

  &__skeleton-inner {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__skeleton-icon {
    width: 36px;
    height: 36px;
    background: var(--el-fill-color);
    border-radius: var(--ys-radius-md);
  }

  &__skeleton-text {
    flex: 1;
    margin-left: $spacing-3;
  }

  &__skeleton-label {
    width: 50%;
    height: 12px;
    margin-bottom: $spacing-2;
    background: var(--el-fill-color);
    border-radius: var(--ys-radius-sm);
  }

  &__skeleton-value {
    width: 70%;
    height: 28px;
    background: var(--el-fill-color);
    border-radius: var(--ys-radius-sm);
  }
}
</style>
