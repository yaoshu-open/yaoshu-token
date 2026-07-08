// 聊天预设 composable：从系统配置获取聊天链接预设
import { computed } from 'vue'
import { useSystemConfigStore } from '@/store/modules/system-config'
import { parseChatConfig, type ChatPreset } from '@/utils/chat-links'

export function useChatPresets() {
  const store = useSystemConfigStore()

  const presets = computed<ChatPreset[]>(() => {
    // chats 配置存储在 rawStatus 中（后端 /api/status 返回的 chats 字段）
    const raw = store.rawStatus as { chats?: unknown } | null
    return parseChatConfig(raw?.chats)
  })

  const serverAddress = computed(() => {
    // 后端地址 = 当前站点 origin（API 代理同源）
    return window.location.origin
  })

  return { presets, serverAddress }
}
