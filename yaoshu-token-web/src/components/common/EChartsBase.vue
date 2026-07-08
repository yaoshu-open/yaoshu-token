<script setup lang="ts">
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent
} from 'echarts/components'
import VChart from 'vue-echarts'
import type { EChartsOption } from 'echarts'

// 按需注册 ECharts 常用模块（模块级单次注册，echarts use 幂等）。
// 消费方使用 PieChart 等未注册类型时，在组件内补充 use([...])。
use([
  CanvasRenderer,
  BarChart,
  LineChart,
  GridComponent,
  TooltipComponent,
  LegendComponent
])

interface Props {
  option: EChartsOption
  /** ECharts 主题名（'dark' / 'light' / undefined），由消费方根据 resolvedMode 传入 */
  theme?: string
  /** 容器高度，默认 100%（父容器需有明确高度） */
  height?: string
}

withDefaults(defineProps<Props>(), {
  theme: undefined,
  height: '100%'
})
</script>

<template>
  <VChart
    class="echarts-base"
    :option="option"
    :theme="theme"
    autoresize
    :style="{ height }"
  />
</template>

<style scoped>
.echarts-base {
  width: 100%;
}
</style>
