import { computed, ref } from 'vue'
import { getSetupStatus } from '@/api/setup'
import type { SetupStatus } from '@/api/setup/types'

// 模块级缓存：应用启动检测一次，跨组件/守卫共享（setup 属一次性引导，不进 Pinia）
const setupStatus = ref<SetupStatus | null>(null)
const fetched = ref(false)
const loading = ref(false)

/**
 * 系统初始化状态检测。
 *
 * 行为：
 * - fetchSetupStatus 首次拉取后缓存，后续调用直接返回（force=true 可强制刷新）
 * - 拉取失败不阻塞：返回 null，由调用方按"已初始化"放行，避免后端不可达时锁死前端
 */
export function useSetupStatus() {
  const isInitialized = computed(() => setupStatus.value?.status === true)
  const needsSetup = computed(() => setupStatus.value?.status === false)

  async function fetchSetupStatus(force = false): Promise<SetupStatus | null> {
    if (fetched.value && !force) return setupStatus.value
    if (loading.value) return setupStatus.value
    loading.value = true
    try {
      setupStatus.value = await getSetupStatus()
      fetched.value = true
      return setupStatus.value
    } catch {
      // 检测失败：标记已尝试避免本会话重复请求，按已初始化放行（刷新页面重置）
      fetched.value = true
      return null
    } finally {
      loading.value = false
    }
  }

  return {
    setupStatus,
    isInitialized,
    needsSetup,
    loading,
    fetchSetupStatus
  }
}
