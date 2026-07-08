/**
 *
 * 职责：封装 getSavedChartPreferences/saveChartPreferences 为响应式 ref + 同步持久化。
 */
import { ref } from 'vue'
import {
  getSavedChartPreferences,
  saveChartPreferences,
} from '@/api/dashboard/lib'
import type { DashboardChartPreferences } from '@/api/dashboard/types'

export function useChartPreferences() {
  const preferences = ref<DashboardChartPreferences>(getSavedChartPreferences())

  function setPreferences(next: DashboardChartPreferences) {
    preferences.value = next
    saveChartPreferences(next)
  }

  return { preferences, setPreferences }
}
