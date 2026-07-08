/**
 * Playground 模型/分组加载器。
 */
import { ref } from 'vue'
import { getUserModels, getUserGroups } from '@/api/playground'
import type { ModelOption, GroupOption } from '@/api/playground/types'

export function useModelsLoader() {
  const loading = ref<boolean>(false)
  const error = ref<Error | null>(null)

  async function loadModels(): Promise<ModelOption[]> {
    try {
      const data = await getUserModels()
      return data
    } catch (err) {
      error.value = err instanceof Error ? err : new Error(String(err))
      return []
    }
  }

  async function loadGroups(): Promise<GroupOption[]> {
    try {
      const data = await getUserGroups()
      return data
    } catch (err) {
      error.value = err instanceof Error ? err : new Error(String(err))
      return []
    }
  }

  async function loadAll(
    setModels: (v: ModelOption[]) => void,
    setGroups: (v: GroupOption[]) => void
  ): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const [m, g] = await Promise.all([loadModels(), loadGroups()])
      setModels(m)
      setGroups(g)
    } finally {
      loading.value = false
    }
  }

  return { loading, error, loadModels, loadGroups, loadAll }
}
