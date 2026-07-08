/**
 * Playground 核心状态 composable。
 *
 * 状态分层：
 * - 持久化：config / parameterEnabled（localStorage）
 * - 运行时：models / groups（接口加载，不持久化）
 * - 消息状态由 useAiChat 的 Chat<UIMessage> 实例独立管理（不在本 composable）
 */
import { ref, watch } from 'vue'
import type {
  PlaygroundConfig,
  ParameterEnabled,
  ModelOption,
  GroupOption
} from '@/api/playground/types'
import {
  DEFAULT_CONFIG,
  DEFAULT_PARAMETER_ENABLED
} from '@/views/playground/constants'
import {
  loadConfig,
  saveConfig,
  loadParameterEnabled,
  saveParameterEnabled
} from '@/views/playground/lib/storage'

export function usePlaygroundState() {
  const config = ref<PlaygroundConfig>({
    ...DEFAULT_CONFIG,
    ...loadConfig()
  })
  const parameterEnabled = ref<ParameterEnabled>({
    ...DEFAULT_PARAMETER_ENABLED,
    ...loadParameterEnabled()
  })
  const models = ref<ModelOption[]>([])
  const groups = ref<GroupOption[]>([])

  // 持久化 watch（saveConfig 内部 try-catch，不阻塞 UI）
  watch(
    config,
    (next) => {
      saveConfig(next)
    },
    { deep: true }
  )
  watch(
    parameterEnabled,
    (next) => {
      saveParameterEnabled(next)
    },
    { deep: true }
  )

  function updateConfig<K extends keyof PlaygroundConfig>(
    key: K,
    value: PlaygroundConfig[K]
  ): void {
    config.value = { ...config.value, [key]: value }
  }

  function updateParameterEnabled(
    key: keyof ParameterEnabled,
    value: boolean
  ): void {
    parameterEnabled.value = { ...parameterEnabled.value, [key]: value }
  }

  function resetConfig(): void {
    config.value = { ...DEFAULT_CONFIG }
    parameterEnabled.value = { ...DEFAULT_PARAMETER_ENABLED }
  }

  return {
    // state
    config,
    parameterEnabled,
    models,
    groups,
    // setters (models/groups 由 useModelsLoader 填充)
    setModels: (v: ModelOption[]) => {
      models.value = v
    },
    setGroups: (v: GroupOption[]) => {
      groups.value = v
    },
    // actions
    updateConfig,
    updateParameterEnabled,
    resetConfig
  }
}
