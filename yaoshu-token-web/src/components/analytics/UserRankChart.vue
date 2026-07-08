<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { use } from 'echarts/core'
import { BarChart } from 'echarts/charts'
import type { EChartsOption } from 'echarts'
import EChartsBase from '@/components/common/EChartsBase.vue'
import { useEChartsTheme } from '@/composables/useEChartsTheme'
import { formatQuotaBilling } from '@/utils/currency'
import type { ChartDataPoint } from '@/composables/analytics/useModelAnalytics'

use([BarChart])

const props = defineProps<{
  data: ChartDataPoint[]
  loading: boolean
}>()

const { t } = useI18n()
const { isDark, themeReady, chartTextColor, chartGridColor, chartColors } = useEChartsTheme()

const option = computed<EChartsOption | null>(() => {
  if (props.data.length === 0) return null
  const reversed = [...props.data].reverse()
  return {
    color: [...chartColors.value],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: unknown) => {
        const arr = Array.isArray(params) ? params : [params]
        const p = arr[0] as { name: string; value: number }
        return `${p.name}<br/>${t('analytics.chart.userRank')}: ${formatQuotaBilling(p.value)}`
      },
    },
    grid: { left: '2%', right: '4%', top: '6%', bottom: '4%', containLabel: true },
    xAxis: {
      type: 'value',
      axisLabel: {
        color: chartTextColor.value,
        fontSize: 10,
        formatter: (v: number) => formatQuotaBilling(v, { digitsLarge: 0, digitsSmall: 0, abbreviate: true }),
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
        name: t('analytics.chart.userRank'),
        type: 'bar',
        data: reversed.map((r) => r.value),
        itemStyle: { borderRadius: [0, 4, 4, 0] },
        label: {
          show: true,
          position: 'right',
          formatter: (p: unknown) => {
            const item = p as { value: number }
            return formatQuotaBilling(item.value, { digitsLarge: 2, digitsSmall: 2, abbreviate: true })
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
  <div class="user-rank-chart">
    <EChartsBase
      v-if="themeReady && option && !loading"
      :option="option"
      :theme="isDark ? 'dark' : undefined"
      height="22rem"
    />
    <div v-else-if="loading" class="user-rank-chart__placeholder" />
    <div v-else class="user-rank-chart__empty">
      {{ t('common.noData') }}
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens' as *;

.user-rank-chart {
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
