<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { EChartsOption } from 'echarts'
import EChartsBase from '@/components/common/EChartsBase.vue'
import VendorLink from './VendorLink.vue'
import { formatShare, formatTokens } from './format'
import { useEChartsTheme } from '@/composables/useEChartsTheme'
import {
  FALLBACK_PALETTE,
  MARKET_PERIOD_DESCRIPTION_KEYS,
  MAX_VENDORS_IN_LIST,
  VENDOR_COLOURS
} from '@/api/rankings/constants'
import type {
  RankingPeriod,
  VendorRanking,
  VendorShareSeries
} from '@/api/rankings/types'

interface AxisTooltipParam {
  seriesName: string
  value: number
  marker: string
  color: string
}

interface Props {
  history?: VendorShareSeries
  rows: VendorRanking[]
  period: RankingPeriod
}
const props = withDefaults(defineProps<Props>(), {
  history: undefined,
})

const { t } = useI18n()
const { isDark, themeReady, chartTextColor, chartGridColor, chartColors } = useEChartsTheme()

// 构建供应商调色板：已知厂商用固定色，未知厂商回退 FALLBACK_PALETTE
function buildVendorColourMap(names: string[]): Record<string, string> {
  const result: Record<string, string> = {}
  let fallbackIdx = 0
  for (const name of names) {
    if (VENDOR_COLOURS[name]) {
      result[name] = VENDOR_COLOURS[name]
    } else {
      result[name] = FALLBACK_PALETTE[fallbackIdx % FALLBACK_PALETTE.length]
      fallbackIdx += 1
    }
  }
  return result
}

const colourMap = computed(() =>
  props.history ? buildVendorColourMap(props.history.vendors.map((v) => v.name)) : {}
)

const option = computed<EChartsOption | null>(() => {
  const history = props.history
  if (!history || history.points.length === 0) return null
  const labels = Array.from(new Set(history.points.map((p) => p.label)))
  const vendorNames = history.vendors.map((v) => v.name)
  const series = vendorNames.map((name) => ({
    name,
    type: 'bar' as const,
    stack: 'total',
    emphasis: { focus: 'series' as const },
    itemStyle: { color: colourMap.value[name] },
    data: labels.map((label) => {
      const p = history.points.find(
        (pt) => pt.label === label && pt.vendor === name
      )
      return p ? p.share : 0
    })
  }))

  return {
    color: [...chartColors.value],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: unknown) => {
        const list = (params as AxisTooltipParam[])
          .filter((p) => Number(p.value) > 0.001)
          .sort((a, b) => Number(b.value) - Number(a.value))
        return list
          .map(
            (p) =>
              `${p.marker} ${p.seriesName}: ${(Number(p.value) * 100).toFixed(1)}%`
          )
          .join('<br/>')
      }
    },
    legend: { show: false },
    grid: { left: '2%', right: '2%', top: '4%', bottom: '2%', containLabel: true },
    xAxis: {
      type: 'category',
      data: labels,
      axisLabel: { color: chartTextColor.value, fontSize: 10 },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 1,
      axisLabel: {
        color: chartTextColor.value,
        fontSize: 10,
        formatter: (val: number) => `${Math.round(val * 100)}%`
      },
      splitLine: { lineStyle: { color: chartGridColor.value, type: 'dashed' } }
    },
    series
  }
})

// 双列供应商列表
const visibleRows = computed(() => props.rows.slice(0, MAX_VENDORS_IN_LIST))
const halfCount = computed(() => Math.ceil(visibleRows.value.length / 2))
const leftRows = computed(() => visibleRows.value.slice(0, halfCount.value))
const rightRows = computed(() => visibleRows.value.slice(halfCount.value))
</script>

<template>
  <section class="market-section">
    <header class="market-section__head">
      <h2 class="market-section__title">
        <i class="i-lucide-pie-chart market-section__icon" />
        {{ t('rankings.marketShare') }}
      </h2>
      <p class="market-section__desc">
        {{ t(MARKET_PERIOD_DESCRIPTION_KEYS[period]) }}
      </p>
    </header>

    <div class="market-section__chart">
      <EChartsBase
        v-if="themeReady && option"
        :option="option"
        :theme="isDark ? 'dark' : undefined"
      />
      <div
        v-else
        class="market-section__chart-empty"
      >
        {{ t('rankings.noHistoryData') }}
      </div>
    </div>

    <div class="market-section__list">
      <header class="market-section__list-head">
        <h3 class="market-section__list-title">
          {{ t('rankings.byModelAuthor') }}
        </h3>
        <p class="market-section__list-desc">
          {{ t('rankings.vendorsRanked') }}
        </p>
      </header>
      <div
        v-if="visibleRows.length"
        class="market-section__list-grid"
      >
        <ul class="market-section__list-col">
          <li
            v-for="vendor in leftRows"
            :key="vendor.vendor"
            class="market-section__list-row"
          >
            <span class="market-section__rank">{{ vendor.rank }}.</span>
            <span
              class="market-section__dot"
              :style="{ backgroundColor: colourMap[vendor.vendor] ?? '#94a3b8' }"
            />
            <VendorLink
              :vendor="vendor.vendor"
              class="market-section__vendor-name"
            >
              {{ vendor.vendor }}
            </VendorLink>
            <div class="market-section__vendor-stats">
              <div class="market-section__vendor-tokens">
                {{ formatTokens(vendor.totalTokens) }}
              </div>
              <div class="market-section__vendor-share">
                {{ formatShare(vendor.share) }}
              </div>
            </div>
          </li>
        </ul>
        <ul
          v-if="rightRows.length"
          class="market-section__list-col"
        >
          <li
            v-for="vendor in rightRows"
            :key="vendor.vendor"
            class="market-section__list-row"
          >
            <span class="market-section__rank">{{ vendor.rank }}.</span>
            <span
              class="market-section__dot"
              :style="{ backgroundColor: colourMap[vendor.vendor] ?? '#94a3b8' }"
            />
            <VendorLink
              :vendor="vendor.vendor"
              class="market-section__vendor-name"
            >
              {{ vendor.vendor }}
            </VendorLink>
            <div class="market-section__vendor-stats">
              <div class="market-section__vendor-tokens">
                {{ formatTokens(vendor.totalTokens) }}
              </div>
              <div class="market-section__vendor-share">
                {{ formatShare(vendor.share) }}
              </div>
            </div>
          </li>
        </ul>
      </div>
      <div
        v-else
        class="market-section__list-empty"
      >
        {{ t('rankings.noVendorData') }}
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.market-section {
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: var(--ys-radius-md);

  &__head {
    padding: 1rem 1.25rem;
  }

  &__title {
    display: inline-flex;
    gap: 0.5rem;
    align-items: center;
    margin: 0;
    font-size: 1rem;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  &__icon {
    font-size: 1rem;
    color: var(--el-color-primary);
  }

  &__desc {
    margin: 0.25rem 0 0;
    font-size: 0.875rem;
    color: var(--el-text-color-secondary);
  }

  &__chart {
    height: 18rem;
    padding: 0 1.25rem 1.25rem;

    @media (width >= 640px) {
      height: 21rem;
    }
  }

  &__chart-empty {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
    font-size: 0.75rem;
    color: var(--el-text-color-secondary);
  }

  &__list {
    border-top: 1px solid var(--el-border-color);
  }

  &__list-head {
    padding: 1rem 1.25rem 0.5rem;
  }

  &__list-title {
    margin: 0;
    font-size: 0.875rem;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  &__list-desc {
    margin: 0.125rem 0 0;
    font-size: 0.75rem;
    color: var(--el-text-color-secondary);
  }

  &__list-grid {
    display: grid;
    grid-template-columns: 1fr;
    gap: 0 2rem;
    padding: 0.25rem 1.25rem 1rem;

    @media (width >= 768px) {
      grid-template-columns: 1fr 1fr;
    }
  }

  &__list-col {
    padding: 0;
    margin: 0;
    list-style: none;
  }

  &__list-row {
    display: flex;
    gap: 0.75rem;
    align-items: center;
    padding: 0.625rem 0;
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

  &__dot {
    flex-shrink: 0;
    width: 0.625rem;
    height: 0.625rem;
    border-radius: 50%;
  }

  &__vendor-name {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: 0.875rem;
    font-weight: 500;
    color: var(--el-text-color-primary);
    white-space: nowrap;
  }

  &__vendor-stats {
    flex-shrink: 0;
    text-align: right;
  }

  &__vendor-tokens {
    font-family: var(--uno-font-mono, ui-monospace, monospace);
    font-size: 0.875rem;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
    color: var(--el-text-color-primary);
  }

  &__vendor-share {
    font-family: var(--uno-font-mono, ui-monospace, monospace);
    font-size: 0.6875rem;
    font-variant-numeric: tabular-nums;
    color: var(--el-text-color-secondary);
  }

  &__list-empty {
    padding: 2rem 1.25rem;
    font-size: 0.875rem;
    color: var(--el-text-color-secondary);
    text-align: center;
  }
}
</style>
