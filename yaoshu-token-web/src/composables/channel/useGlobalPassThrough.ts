/**
 * 全局请求透传开关状态。
 *
 * 从 /api/option/ 查找 key = 'global.pass_through_request_enabled' 的配置项
 */
import { ref, onMounted } from 'vue'
import { request } from '@/utils/request'

interface OptionItem {
  key: string
  value: string
}

function toBoolean(value: unknown): boolean {
  if (typeof value === 'boolean') return value
  if (typeof value === 'string') return value === 'true'
  return false
}

export function useGlobalPassThrough() {
  const globalPassThroughEnabled = ref<boolean>(false)

  async function fetchGlobalPassThrough(): Promise<void> {
    try {
      const data = await request.get<OptionItem[]>('/api/option/')
      if (!Array.isArray(data)) return
      const option = data.find((item) => item?.key === 'global.pass_through_request_enabled')
      if (option) {
        globalPassThroughEnabled.value = toBoolean(option.value)
      }
    } catch {
      globalPassThroughEnabled.value = false
    }
  }

  onMounted(fetchGlobalPassThrough)

  return { globalPassThroughEnabled }
}
