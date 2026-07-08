<script setup lang="ts">
/**
 * 模型图表组合（count 维度，对齐原版 ModelCharts）。
 * 三 tab 切换：
 *   - trend（调用趋势折线图，spec_model_line）
 *   - proportion（调用次数占比饼图，spec_pie）
 *   - top（调用次数排行柱状图，spec_rank_bar）
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElRadioGroup, ElRadioButton } from 'element-plus'
import { use } from 'echarts/core'
import { PieChart } from 'echarts/charts'
import type { EChartsOption } from 'echarts'
import EChartsBase from '@/components/common/EChartsBase.vue'
import { useEChartsTheme } from '@/composables/useEChartsTheme'
import type {
  ChartDataPoint,
  TrendData,
} from '@/composables/analytics/useModelAnalytics'
import type { ModelAnalyticsChartTab } from '@/api/dashboard/types'

use([PieChart])

const props = defineProps<{
  trend: TrendData
  proportion: ChartDataPoint[]
  rank: ChartDataPoint[]
  loading: boolean
  defaultChartTab: ModelAnalyticsChartTab
}>()

const { t } = useI18n()
const { isDark, themeReady, chartTextColor, chartGridColor, chartColors } = useEChartsTheme()

const activeTab = ref<ModelAnalyticsChartTab>(props.defaultChartTab)
watch(
  () => props.defaultChartTab,
  (next) => {
    activeTab.value = next
  },
)

const trendOption = computed<EChartsOption | null>(() => {
  if (props.trend.series.length === 0) return null
  return {
    color: [...chartColors.value],
    tooltip: {
      trigger: 'axis',
      formatter: (params: unknown) => {
        const arr = Array.isArray(params) ? params : [params]
        const lines = arr.map((p) => {
          const item = p as { seriesName: string; value: number; marker: string }
          return `${item.marker} ${item.seriesName}: ${Intl.NumberFormat().format(item.value)}`
        })
        return lines.join('<br/>')
      },
    },
    legend: {
      top: 'top',
      textStyle: { color: chartTextColor.value, fontSize: 11 },
    },
    grid: { left: '2%', right: '4%', top: '12%', bottom: '4%', containLabel: true },
    xAxis: {
      type: 'category',
      data: props.trend.times,
      boundaryGap: false,
      axisLabel: { color: chartTextColor.value, fontSize: 10 },
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        color: chartTextColor.value,
        fontSize: 10,
        formatter: (v: number) => Intl.NumberFormat(undefined, { notation: 'compact' }).format(v),
      },
      splitLine: { lineStyle: { color: chartGridColor.value, type: 'dashed' } },
    },
    series: props.trend.series.map((s) => ({
      name: s.name,
      type: 'line',
      smooth: true,
      showSymbol: false,
      lineStyle: { width: 2 },
      areaStyle: { opacity: 0.08 },
      data: s.data,
    })),
  }
})

const proportionOption = computed<EChartsOption | null>(() => {
  if (props.proportion.length === 0) return null
  // Top 10 + Other 桶（避免饼图扇区过多）
  const top = props.proportion.slice(0, 10)
  const otherSum = props.proportion.slice(10).reduce((s, i) => s + i.value, 0)
  const data =
    otherSum > 0 ? [...top, { name: t('analytics.other'), value: otherSum }] : top
  return {
    tooltip: {
      trigger: 'item',
      formatter: (p: unknown) => {
        const item = p as { name: string; value: number; percent: number }
        return `${item.name}<br/>${Intl.NumberFormat().format(item.value)} (${item.percent}%)`
      },
    },
    legend: {
      orient: 'vertical',
      right: '4%',
      top: 'middle',
      textStyle: { color: chartTextColor.value, fontSize: 11 },
    },
    series: [
      {
        name: t('analytics.chart.modelProportion'),
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['35%', '50%'],
        avoidLabelOverlap: true,
        label: { show: false },
        emphasis: {
          label: { show: true, fontSize: 14, fontWeight: 'bold' },
        },
        data: data.map((d) => ({ name: d.name, value: d.value })),
      },
    ],
  }
})

const rankOption = computed<EChartsOption | null>(() => {
  if (props.rank.length === 0) return null
  const reversed = [...props.rank].reverse()
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: unknown) => {
        const arr = Array.isArray(params) ? params : [params]
        const p = arr[0] as { name: string; value: number }
        return `${p.name}<br/>${Intl.NumberFormat().format(p.value)}`
      },
    },
    grid: { left: '2%', right: '8%', top: '6%', bottom: '4%', containLabel: true },
    xAxis: {
      type: 'value',
      axisLabel: {
        color: chartTextColor.value,
        fontSize: 10,
        formatter: (v: number) =>
          Intl.NumberFormat(undefined, { notation: 'compact' }).format(v),
      },
      splitLine: { lineStyle: { color: chartGridColor.value, type: 'dashed' } },
    },
    yAxis: {
      type: 'category',
      data: reversed.map((r) => r.name),
      axisLabel: { color: chartTextColor.value, fontSize: 10 },
    },
    series: [
      {
        name: t('analytics.chart.modelRank'),
        type: 'bar',
        data: reversed.map((r) => r.value),
        itemStyle: { borderRadius: [0, 4, 4, 0] },
        label: {
          show: true,
          position: 'right',
          formatter: (p: unknown) => {
            const item = p as { value: number }
            return Intl.NumberFormat(undefined, { notation: 'compact' }).format(item.value)
          },
          color: chartTextColor.value,
          fontSize: 10,
        },
      },
    ],
  }
})
</script>

<template>
  <div class="model-charts">
    <div class="model-charts__toolbar">
      <el-radio-group v-model="activeTab" size="small">
        <el-radio-button value="trend">
          {{ t('analytics.preferences.trend') }}
        </el-radio-button>
        <el-radio-button value="proportion">
          {{ t('analytics.preferences.proportion') }}
        </el-radio-button>
        <el-radio-button value="top">
          {{ t('analytics.preferences.top') }}
        </el-radio-button>
      </el-radio-group>
    </div>
    <EChartsBase
      v-if="themeReady && activeTab === 'trend' && trendOption && !loading"
      :option="trendOption"
      :theme="isDark ? 'dark' : undefined"
      height="22rem"
    />
    <EChartsBase
      v-else-if="themeReady && activeTab === 'proportion' && proportionOption && !loading"
      :option="proportionOption"
      :theme="isDark ? 'dark' : undefined"
      height="22rem"
    />
    <EChartsBase
      v-else-if="themeReady && activeTab === 'top' && rankOption && !loading"
      :option="rankOption"
      :theme="isDark ? 'dark' : undefined"
      height="22rem"
    />
    <div v-else-if="loading" class="model-charts__placeholder" />
    <div v-else class="model-charts__empty">
      {{ t('common.noData') }}
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens' as *;

.model-charts {
  width: 100%;

  &__toolbar {
    margin-bottom: $spacing-3;
  }

  &__placeholder {
    height: 22rem;
    background: var(--el-fill-color-light);
    border-radius: $radius-md;
    animation: pulse 1.5s ease-in-out infinite;
  }

  &__empty {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 22rem;
    font-size: $font-size-sm;
    color: var(--el-text-color-secondary);
  }
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
</style>
