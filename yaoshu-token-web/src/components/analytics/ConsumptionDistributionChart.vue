<script setup lang="ts">
/**
 * 消费分布图（quota 维度，对齐原版 spec_line + spec_area）。
 * 支持柱状（bar）/ 面积（area）切换，堆叠展示各模型随时间的消费分布。
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElRadioGroup, ElRadioButton } from 'element-plus'
import type { EChartsOption } from 'echarts'
import EChartsBase from '@/components/common/EChartsBase.vue'
import { useEChartsTheme } from '@/composables/useEChartsTheme'
import { formatQuotaBilling } from '@/utils/currency'
import type { TrendData } from '@/composables/analytics/useModelAnalytics'
import type { ConsumptionDistributionChartType } from '@/api/dashboard/types'

const props = defineProps<{
  data: TrendData
  loading: boolean
  defaultChartType: ConsumptionDistributionChartType
}>()

const { t } = useI18n()
const { isDark, themeReady, chartTextColor, chartGridColor, chartColors } = useEChartsTheme()

const chartType = ref<ConsumptionDistributionChartType>(props.defaultChartType)
watch(
  () => props.defaultChartType,
  (next) => {
    chartType.value = next
  },
)

const option = computed<EChartsOption | null>(() => {
  if (props.data.series.length === 0) return null
  const isBar = chartType.value === 'bar'
  return {
    color: [...chartColors.value],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: isBar ? 'shadow' : 'line' },
      formatter: (params: unknown) => {
        const arr = (Array.isArray(params) ? params : [params]) as Array<{
          seriesName: string
          value: number
          marker: string
          axisValue?: string
        }>
        if (arr.length === 0) return ''
        const time = arr[0].axisValue ?? ''
        const sorted = [...arr].sort((a, b) => b.value - a.value)
        const lines = sorted
          .filter((p) => p.value > 0)
          .map((p) => `${p.marker} ${p.seriesName}: ${formatQuotaBilling(p.value)}`)
        const total = sorted.reduce((s, p) => s + (Number(p.value) || 0), 0)
        return `${time}<br/>${t('analytics.total')}: ${formatQuotaBilling(total)}<br/>${lines.join('<br/>')}`
      },
    },
    legend: {
      top: 'top',
      textStyle: { color: chartTextColor.value, fontSize: 11 },
    },
    grid: { left: '2%', right: '4%', top: '12%', bottom: '4%', containLabel: true },
    xAxis: {
      type: 'category',
      data: props.data.times,
      boundaryGap: isBar,
      axisLabel: { color: chartTextColor.value, fontSize: 10 },
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        color: chartTextColor.value,
        fontSize: 10,
        formatter: (v: number) => formatQuotaBilling(v, { digitsLarge: 0, digitsSmall: 0, abbreviate: true }),
      },
      splitLine: { lineStyle: { color: chartGridColor.value, type: 'dashed' } },
    },
    series: props.data.series.map((s) => ({
      name: s.name,
      type: isBar ? 'bar' : 'line',
      stack: 'quota',
      smooth: !isBar,
      showSymbol: false,
      lineStyle: isBar ? undefined : { width: 2 },
      areaStyle: isBar ? undefined : { opacity: 0.08 },
      emphasis: { focus: 'series' },
      data: s.data,
    })),
  }
})
</script>

<template>
  <div class="consumption-distribution-chart">
    <div class="consumption-distribution-chart__toolbar">
      <el-radio-group v-model="chartType" size="small">
        <el-radio-button value="bar">
          {{ t('analytics.preferences.bar') }}
        </el-radio-button>
        <el-radio-button value="area">
          {{ t('analytics.preferences.area') }}
        </el-radio-button>
      </el-radio-group>
    </div>
    <EChartsBase
      v-if="themeReady && option && !loading"
      :option="option"
      :theme="isDark ? 'dark' : undefined"
      height="22rem"
    />
    <div v-else-if="loading" class="consumption-distribution-chart__placeholder" />
    <div v-else class="consumption-distribution-chart__empty">
      {{ t('common.noData') }}
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens' as *;

.consumption-distribution-chart {
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
