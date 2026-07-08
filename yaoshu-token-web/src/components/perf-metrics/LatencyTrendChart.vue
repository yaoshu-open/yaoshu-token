<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { EChartsOption } from 'echarts'
import EChartsBase from '@/components/common/EChartsBase.vue'
import { useEChartsTheme } from '@/composables/useEChartsTheme'

/** 延迟趋势样本点（视图模型，由 ModelDetailsPerformance 从分组时序聚合产出） */
export interface LatencyTrendPoint {
  timestamp: string
  group: string
  ttft_ms: number
}

interface Props {
  series: LatencyTrendPoint[]
}

const props = defineProps<Props>()

const { t } = useI18n()
const { isDark, themeReady, chartTextColor, chartGridColor, chartColors } = useEChartsTheme()

function formatHourLabel(iso: string): string {
  const date = new Date(iso)
  return `${String(date.getHours()).padStart(2, '0')}:00`
}

const option = computed<EChartsOption | null>(() => {
  if (props.series.length === 0) return null
  const labels = props.series.map((p) => formatHourLabel(p.timestamp))
  return {
    color: [...chartColors.value],
    tooltip: {
      trigger: 'axis',
      valueFormatter: (val) => `${Math.round(Number(val) || 0)} ms`,
      formatter: (params: unknown) => {
        const p = (params as Array<{ axisValue: string; value: number }>)[0]
        return `${p.axisValue}<br/>${t('performance.avgTtft')}: ${Math.round(p.value)} ms`
      }
    },
    grid: { left: '2%', right: '4%', top: '6%', bottom: '4%', containLabel: true },
    xAxis: {
      type: 'category',
      data: labels,
      boundaryGap: false,
      axisLabel: { color: chartTextColor.value, fontSize: 10 },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        color: chartTextColor.value,
        fontSize: 10,
        formatter: '{value} ms'
      },
      splitLine: { lineStyle: { color: chartGridColor.value, type: 'dashed' } }
    },
    series: [
      {
        name: t('performance.avgTtft'),
        type: 'line',
        smooth: true,
        showSymbol: true,
        symbolSize: 6,
        lineStyle: { width: 2 },
        itemStyle: { borderColor: '#ffffff', borderWidth: 1.5 },
        data: props.series.map((p) => p.ttft_ms)
      }
    ]
  }
})
</script>

<template>
  <div class="latency-trend-chart">
    <EChartsBase
      v-if="themeReady && option"
      :option="option"
      :theme="isDark ? 'dark' : undefined"
      height="16rem"
    />
    <div
      v-else
      class="latency-trend-chart__empty"
    >
      {{ t('performance.noLatencyData') }}
    </div>
  </div>
</template>

<style scoped lang="scss">
.latency-trend-chart {
  width: 100%;

  &__empty {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 12rem;
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-secondary);
    border: 1px solid var(--el-border-color);
    border-radius: var(--ys-radius-md);
  }
}
</style>
