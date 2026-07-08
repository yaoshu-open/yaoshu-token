<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { EChartsOption } from 'echarts'
import EChartsBase from '@/components/common/EChartsBase.vue'
import { useEChartsTheme } from '@/composables/useEChartsTheme'
import type { UptimePoint } from './UptimeSparkline.vue'

interface Props {
  series: UptimePoint[]
}

const props = defineProps<Props>()

const { t } = useI18n()
const { isDark, themeReady, chartTextColor, chartGridColor, chartColors } = useEChartsTheme()

function formatDayLabel(dateStr: string): string {
  const parsed = new Date(dateStr)
  if (dateStr.includes('T')) {
    return parsed.toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: '2-digit'
    })
  }
  return parsed.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
}
function pointColor(uptime: number): string {
  if (uptime >= 99.9) return '#10b981'
  if (uptime >= 99) return '#f59e0b'
  return '#ef4444'
}

const option = computed<EChartsOption | null>(() => {
  if (props.series.length === 0) return null
  const labels = props.series.map((p) => formatDayLabel(p.date))
  return {
    color: [...chartColors.value],
    tooltip: {
      trigger: 'axis',
      formatter: (params: unknown) => {
        const p = (params as Array<{ axisValue: string; dataIndex: number }>)[0]
        const point = props.series[p.dataIndex]
        return `${p.axisValue}<br/>${t('performance.uptime')}: ${Number(point.uptime_pct).toFixed(2)}%<br/>${t('performance.incidents')}: ${point.incidents}`
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
      min: 95,
      max: 100,
      axisLabel: {
        color: chartTextColor.value,
        fontSize: 10,
        formatter: '{value}%'
      },
      splitLine: { lineStyle: { color: chartGridColor.value, type: 'dashed' } }
    },
    series: [
      {
        name: t('performance.uptime'),
        type: 'line',
        smooth: true,
        showSymbol: true,
        symbolSize: 6,
        lineStyle: { color: '#10b981', width: 2 },
        itemStyle: {
          borderColor: '#ffffff',
          borderWidth: 1.5
        },
        data: props.series.map((p) => ({
          value: p.uptime_pct,
          itemStyle: { color: pointColor(p.uptime_pct) }
        }))
      }
    ]
  }
})
</script>

<template>
  <div class="uptime-trend-chart">
    <EChartsBase
      v-if="themeReady && option"
      :option="option"
      :theme="isDark ? 'dark' : undefined"
      height="14rem"
    />
    <div
      v-else
      class="uptime-trend-chart__empty"
    >
      {{ t('performance.noUptimeData') }}
    </div>
  </div>
</template>

<style scoped lang="scss">
.uptime-trend-chart {
  width: 100%;

  &__empty {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 11rem;
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-secondary);
    border: 1px solid var(--el-border-color);
    border-radius: var(--ys-radius-md);
  }
}
</style>
