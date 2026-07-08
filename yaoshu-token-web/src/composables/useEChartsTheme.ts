import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useThemeStore } from '@/store/modules/theme'

/**
 * ECharts 主题色解析。
 * 基于 theme store 的 resolvedMode 输出图表配色，确保图表随亮/暗主题切换。
 * 公共基座，后续 performance-metrics / Dashboard / pricing 模块复用。
 */
export function useEChartsTheme() {
  const themeStore = useThemeStore()
  const { resolvedMode } = storeToRefs(themeStore)

  const isDark = computed(() => resolvedMode.value === 'dark')
  // theme store 同步初始化，首屏即就绪（对齐 default useChartTheme 的 themeReady 语义）
  const themeReady = computed(() => true)
  const chartTextColor = computed(() =>
    isDark.value ? 'rgba(255, 255, 255, 0.68)' : 'rgba(15, 23, 42, 0.58)'
  )
  const chartGridColor = computed(() =>
    isDark.value ? 'rgba(255, 255, 255, 0.12)' : 'rgba(15, 23, 42, 0.12)'
  )

  // 图表色板：动态读取 CSS 变量 --ys-chart-1~5
  // 开源端用通用色板，商业版通过覆盖 CSS 变量自动获得品牌双色板
  // 依赖 isDark 触发暗色切换时重新计算
  const chartColors = computed<readonly string[]>(() => {
    if (typeof document === 'undefined') {
      return ['#409eff', '#8b5cf6', '#ec4899', '#f59e0b', '#10b981']
    }
    const cs = getComputedStyle(document.documentElement)
    return [
      cs.getPropertyValue('--ys-chart-1').trim() || '#409eff',
      cs.getPropertyValue('--ys-chart-2').trim() || '#8b5cf6',
      cs.getPropertyValue('--ys-chart-3').trim() || '#ec4899',
      cs.getPropertyValue('--ys-chart-4').trim() || '#f59e0b',
      cs.getPropertyValue('--ys-chart-5').trim() || '#10b981',
    ]
  })

  return {
    resolvedMode,
    isDark,
    themeReady,
    chartTextColor,
    chartGridColor,
    chartColors
  }
}
