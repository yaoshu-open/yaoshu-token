<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { EChartsOption } from 'echarts'
import EChartsBase from '@/components/common/EChartsBase.vue'
import { useEChartsTheme } from '@/composables/useEChartsTheme'
import { formatQuotaBilling } from '@/utils/currency'
import type { TrendData } from '@/composables/analytics/useModelAnalytics'

const props = defineProps<{
  data: TrendData
  loading: boolean
}>()

const { t } = useI18n()
const { isDark, themeReady, chartTextColor, chartGridColor, chartColors } = useEChartsTheme()

const option = computed<EChartsOption | null>(() => {
  if (props.data.series.length === 0) return null
  return {
    color: [...chartColors.value],
    tooltip: {
      trigger: 'axis',
      formatter: (params: unknown) => {
        const arr = Array.isArray(params) ? params : [params]
        const lines = arr.map((p) => {
          const item = p as { seriesName: string; value: number; marker: string }
          return `${item.marker} ${item.seriesName}: ${formatQuotaBilling(item.value)}`
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
      data: props.data.times,
      boundaryGap: false,
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
      type: 'line',
      smooth: true,
      showSymbol: false,
      lineStyle: { width: 2 },
      areaStyle: { opacity: 0.08 },
      data: s.data,
    })),
  }
})
</script>

<template>
  <div class="user-trend-chart">
    <EChartsBase
      v-if="themeReady && option && !loading"
      :option="option"
      :theme="isDark ? 'dark' : undefined"
      height="22rem"
    />
    <div v-else-if="loading" class="user-trend-chart__placeholder" />
    <div v-else class="user-trend-chart__empty">
      {{ t('common.noData') }}
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens' as *;

.user-trend-chart {
  width: 100%;

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
