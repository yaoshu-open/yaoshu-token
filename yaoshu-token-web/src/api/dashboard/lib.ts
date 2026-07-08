import {
  DEFAULT_DASHBOARD_CHART_PREFERENCES,
  DASHBOARD_CHART_PREFERENCES_STORAGE_KEY,
  EMPTY_DASHBOARD_FILTERS,
  TIME_RANGE_PRESETS,
} from './constants'
import type {
  ConsumptionDistributionChartType,
  DashboardChartPreferences,
  DashboardFilters,
  ModelAnalyticsChartTab,
  TimeGranularity,
} from './types'

function isTimeGranularity(value: unknown): value is TimeGranularity {
  return value === 'hour' || value === 'day' || value === 'week'
}

function isConsumptionDistributionChartType(
  value: unknown,
): value is ConsumptionDistributionChartType {
  return value === 'bar' || value === 'area'
}

function isModelAnalyticsChartTab(value: unknown): value is ModelAnalyticsChartTab {
  return value === 'trend' || value === 'proportion' || value === 'top'
}

function isTimeRangePresetDays(value: unknown): value is number {
  return typeof value === 'number' && TIME_RANGE_PRESETS.some((p) => p.days === value)
}

/** 读取 localStorage 持久化的图表偏好，缺失/解析失败回落默认 */
export function getSavedChartPreferences(): DashboardChartPreferences {
  if (typeof window === 'undefined') return DEFAULT_DASHBOARD_CHART_PREFERENCES
  try {
    const raw = window.localStorage.getItem(DASHBOARD_CHART_PREFERENCES_STORAGE_KEY)
    if (!raw) return DEFAULT_DASHBOARD_CHART_PREFERENCES
    const parsed = JSON.parse(raw) as Partial<DashboardChartPreferences>
    return {
      consumptionDistributionChart: isConsumptionDistributionChartType(
        parsed.consumptionDistributionChart,
      )
        ? parsed.consumptionDistributionChart
        : DEFAULT_DASHBOARD_CHART_PREFERENCES.consumptionDistributionChart,
      modelAnalyticsChart: isModelAnalyticsChartTab(parsed.modelAnalyticsChart)
        ? parsed.modelAnalyticsChart
        : DEFAULT_DASHBOARD_CHART_PREFERENCES.modelAnalyticsChart,
      defaultTimeRangeDays: isTimeRangePresetDays(parsed.defaultTimeRangeDays)
        ? parsed.defaultTimeRangeDays
        : DEFAULT_DASHBOARD_CHART_PREFERENCES.defaultTimeRangeDays,
      defaultTimeGranularity: isTimeGranularity(parsed.defaultTimeGranularity)
        ? parsed.defaultTimeGranularity
        : DEFAULT_DASHBOARD_CHART_PREFERENCES.defaultTimeGranularity,
    }
  } catch {
    return DEFAULT_DASHBOARD_CHART_PREFERENCES
  }
}

/** 保存图表偏好至 localStorage */
export function saveChartPreferences(preferences: DashboardChartPreferences): void {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.setItem(
      DASHBOARD_CHART_PREFERENCES_STORAGE_KEY,
      JSON.stringify(preferences),
    )
  } catch {
    /* privacy mode */
  }
}

/** 基于偏好构造默认筛选条件（滚动时间窗口） */
export function buildDefaultDashboardFilters(
  preferences: DashboardChartPreferences = getSavedChartPreferences(),
): DashboardFilters {
  const now = Math.floor(Date.now() / 1000)
  const start = now - preferences.defaultTimeRangeDays * 86400
  return {
    ...EMPTY_DASHBOARD_FILTERS,
    start_timestamp: start,
    end_timestamp: now,
    time_granularity: preferences.defaultTimeGranularity,
  }
}

/** 清理 filters 中的空值/undefined 字段，便于查询参数透传 */
export function cleanFilters<T extends Record<string, unknown>>(filters: T): Partial<T> {
  const cleaned: Partial<T> = {}
  for (const [key, value] of Object.entries(filters)) {
    if (value === undefined || value === null) continue
    if (typeof value === 'string') {
      const trimmed = value.trim()
      if (trimmed) cleaned[key as keyof T] = trimmed as T[keyof T]
      continue
    }
    cleaned[key as keyof T] = value as T[keyof T]
  }
  return cleaned
}
