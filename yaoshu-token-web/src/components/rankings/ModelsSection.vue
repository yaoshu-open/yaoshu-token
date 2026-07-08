<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { EChartsOption } from 'echarts'
import EChartsBase from '@/components/common/EChartsBase.vue'
import ModelLeaderboard from './ModelLeaderboard.vue'
import { formatTokens } from './format'
import { useEChartsTheme } from '@/composables/useEChartsTheme'
import { PERIOD_DESCRIPTION_KEYS, TOOLTIP_MAX_ROWS } from '@/api/rankings/constants'
import type {
  ModelHistorySeries,
  ModelRanking,
  RankingPeriod
} from '@/api/rankings/types'

// ECharts axis tooltip formatter 参数子集（CallbackDataParams 结构兼容子类型）
interface AxisTooltipParam {
  seriesName: string
  value: number
  marker: string
  color: string
}

interface Props {
  history?: ModelHistorySeries
  rows: ModelRanking[]
  period: RankingPeriod
}
const props = withDefaults(defineProps<Props>(), {
  history: undefined,
})

const { t } = useI18n()
const { isDark, themeReady, chartTextColor, chartGridColor, chartColors } = useEChartsTheme()

const totalTokens = computed(() =>
  props.rows.reduce((sum, r) => sum + r.totalTokens, 0)
)

// 将扁平 points 预处理为 ECharts 多 series 堆叠结构（按 models 顺序对齐底部）
const option = computed<EChartsOption | null>(() => {
  const history = props.history
  if (!history || history.points.length === 0) return null
  const labels = Array.from(new Set(history.points.map((p) => p.label)))
  const modelNames = history.models.map((m) => m.name)
  const series = modelNames.map((name) => ({
    name,
    type: 'bar' as const,
    stack: 'total',
    emphasis: { focus: 'series' as const },
    data: labels.map((label) => {
      const p = history.points.find(
        (pt) => pt.label === label && pt.model === name
      )
      return p ? p.tokens : 0
    })
  }))

  return {
    color: [...chartColors.value],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      valueFormatter: (val) => formatTokens(Number(val) || 0),
      formatter: (params: unknown) => {
        const list = (params as AxisTooltipParam[])
          .filter((p) => Number(p.value) > 0)
          .sort((a, b) => Number(b.value) - Number(a.value))
        const sum = list.reduce((s, p) => s + (Number(p.value) || 0), 0)
        const visible = list.slice(0, TOOLTIP_MAX_ROWS)
        const overflow = list.slice(TOOLTIP_MAX_ROWS)
        const lines = visible.map(
          (p) => `${p.marker} ${p.seriesName}: ${formatTokens(Number(p.value))}`
        )
        if (overflow.length > 0) {
          const otherSum = overflow.reduce(
            (s, p) => s + (Number(p.value) || 0),
            0
          )
          lines.push(
            `${t('rankings.tooltip.more', { count: overflow.length })}: ${formatTokens(otherSum)}`
          )
        }
        lines.unshift(`${t('rankings.tooltip.total')}: ${formatTokens(sum)}`)
        return lines.join('<br/>')
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
      axisLabel: {
        color: chartTextColor.value,
        fontSize: 10,
        formatter: (val: number) => formatTokens(val)
      },
      splitLine: { lineStyle: { color: chartGridColor.value, type: 'dashed' } }
    },
    series
  }
})
</script>

<template>
  <section class="models-section">
    <header class="models-section__head">
      <div class="models-section__title-wrap">
        <h2 class="models-section__title">
          <i class="i-lucide-bar-chart-3 models-section__icon" />
          {{ t('rankings.topModels') }}
        </h2>
        <p class="models-section__desc">
          {{ t(PERIOD_DESCRIPTION_KEYS[period]) }}
        </p>
      </div>
      <div class="models-section__total">
        <div class="models-section__total-value">
          {{ formatTokens(totalTokens) }}
        </div>
        <div class="models-section__total-label">
          {{ t('rankings.tokens') }}
        </div>
      </div>
    </header>

    <div class="models-section__chart">
      <EChartsBase
        v-if="themeReady && option"
        :option="option"
        :theme="isDark ? 'dark' : undefined"
      />
      <div
        v-else
        class="models-section__chart-empty"
      >
        {{ t('rankings.noHistoryData') }}
      </div>
    </div>

    <div class="models-section__leaderboard">
      <header class="models-section__lb-head">
        <h3 class="models-section__lb-title">
          <i class="i-lucide-trophy models-section__lb-icon" />
          {{ t('rankings.llmLeaderboard') }}
        </h3>
        <p class="models-section__lb-desc">
          {{ t('rankings.leaderboardDesc') }}
        </p>
      </header>
      <div
        v-if="rows.length"
        class="models-section__lb-body"
      >
        <ModelLeaderboard :rows="rows" />
      </div>
      <div
        v-else
        class="models-section__lb-empty"
      >
        {{ t('rankings.noModelsMatch') }}
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.models-section {
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: var(--ys-radius-md);

  &__head {
    display: flex;
    gap: 1rem;
    align-items: flex-start;
    justify-content: space-between;
    padding: 1rem 1.25rem;
  }

  &__title-wrap {
    flex: 1;
    min-width: 0;
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

  &__total {
    flex-shrink: 0;
    text-align: right;
  }

  &__total-value {
    font-family: var(--uno-font-mono, ui-monospace, monospace);
    font-size: 1.5rem;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
    color: var(--el-text-color-primary);
  }

  &__total-label {
    font-size: 10px;
    font-weight: 500;
    color: var(--el-text-color-secondary);
    text-transform: uppercase;
    letter-spacing: 0.1em;
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

  &__leaderboard {
    border-top: 1px solid var(--el-border-color);
  }

  &__lb-head {
    padding: 1rem 1.25rem 0.5rem;
  }

  &__lb-title {
    display: inline-flex;
    gap: 0.5rem;
    align-items: center;
    margin: 0;
    font-size: 0.875rem;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  &__lb-icon {
    font-size: 0.875rem;
    color: #f59e0b;
  }

  &__lb-desc {
    margin: 0.125rem 0 0;
    font-size: 0.75rem;
    color: var(--el-text-color-secondary);
  }

  &__lb-body {
    padding: 0.25rem 1.25rem 1rem;
  }

  &__lb-empty {
    padding: 2rem 1.25rem;
    font-size: 0.875rem;
    color: var(--el-text-color-secondary);
    text-align: center;
  }
}
</style>
