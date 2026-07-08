/**
 * 系统配置数据管理 composable。
 *
 * 职责：拉取全量 SystemOption[] + 提供 getOptionValue 泛型解析。
 */
import { ref, type Ref } from 'vue'
import { getSystemOptions } from '@/api/system-option'
import type { SystemOption } from '@/api/system-option/types'

/** 从 SystemOption[] 解析为 key-value 字典 */
export function toOptionMap(options: SystemOption[]): Record<string, string> {
  const map: Record<string, string> = {}
  for (const opt of options) {
    map[opt.key] = opt.value
  }
  return map
}

/** 从 option 字典获取值，支持默认值 */
export function getOptionValue<T extends object>(
  options: SystemOption[] | undefined,
  defaults: T,
): T {
  if (!options) return defaults
  const map = toOptionMap(options)
  const result = { ...defaults } as Record<string, unknown>
  for (const key of Object.keys(defaults)) {
    if (key in map) {
      const defaultVal = (defaults as Record<string, unknown>)[key]
      const raw = map[key]
      if (typeof defaultVal === 'boolean') {
        result[key] = raw === 'true' || raw === '1'
      } else if (typeof defaultVal === 'number') {
        const n = Number(raw)
        result[key] = Number.isNaN(n) ? defaultVal : n
      } else {
        result[key] = raw
      }
    }
  }
  return result as T
}

export function useSystemOptions() {
  const data: Ref<SystemOption[] | null> = ref(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchOptions() {
    loading.value = true
    error.value = null
    try {
      data.value = await getSystemOptions()
    } catch (e) {
      error.value = e instanceof Error ? e.message : '加载系统配置失败'
      data.value = null
    } finally {
      loading.value = false
    }
  }

  return { data, loading, error, fetchOptions }
}
