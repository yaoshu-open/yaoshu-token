/**
 * Playground 调试面板 composable。
 *
 * 状态：
 * - showDebugPanel: 是否展示
 * - activeDebugTab: 当前 Tab（preview/actual/sse）
 * - previewPayload: UI 上 messages+参数构建的 payload
 * - actualRequest: 真正发送的 payload
 * - sseEvents: 响应 SSE 事件流时间线
 */
import { ref } from 'vue'
import type { ChatCompletionRequest, SseEventRecord, DebugTab } from '@/api/playground/types'

let sseIdCounter = 0
function nextSseId(): string {
  sseIdCounter += 1
  return `sse-${Date.now()}-${sseIdCounter}`
}

export function useDebugPanel() {
  const showDebugPanel = ref<boolean>(false)
  const activeDebugTab = ref<DebugTab>('preview')
  const previewPayload = ref<ChatCompletionRequest | null>(null)
  const actualRequest = ref<ChatCompletionRequest | null>(null)
  const sseEvents = ref<SseEventRecord[]>([])
  const previewTimestamp = ref<number | null>(null)
  const requestTimestamp = ref<number | null>(null)

  function setPreviewPayload(payload: ChatCompletionRequest): void {
    previewPayload.value = payload
    previewTimestamp.value = Date.now()
  }

  function setActualRequest(payload: ChatCompletionRequest): void {
    actualRequest.value = payload
    requestTimestamp.value = Date.now()
  }

  function appendSseEvent(record: Omit<SseEventRecord, 'id' | 'timestamp'>): void {
    sseEvents.value.push({
      ...record,
      id: nextSseId(),
      timestamp: Date.now()
    })
  }

  function clearSseEvents(): void {
    sseEvents.value = []
  }

  function openDebugPanel(tab: DebugTab = 'preview'): void {
    showDebugPanel.value = true
    activeDebugTab.value = tab
  }

  function closeDebugPanel(): void {
    showDebugPanel.value = false
  }

  return {
    showDebugPanel,
    activeDebugTab,
    previewPayload,
    actualRequest,
    sseEvents,
    previewTimestamp,
    requestTimestamp,
    setPreviewPayload,
    setActualRequest,
    appendSseEvent,
    clearSseEvents,
    openDebugPanel,
    closeDebugPanel
  }
}
