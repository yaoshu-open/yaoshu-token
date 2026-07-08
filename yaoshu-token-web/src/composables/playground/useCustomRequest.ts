/**
 * Playground 自定义请求体 composable。
 *
 * 关键约束（人类拍板）：customRequestBody 不持久化（每次会话重置）
 *
 * 暴露：
 * - customRequestMode/customRequestBody
 * - isValidJson（实时校验）
 * - canSend（仅当模式开启 + JSON 合法时可发送）
 * - formatJson（美化）
 * - loadDefaultPayload（开启时自动填入 payload-builder 输出）
 */
import { ref, computed } from 'vue'
import type { ChatCompletionRequest } from '@/api/playground/types'

export function useCustomRequest() {
  const customRequestMode = ref<boolean>(false)
  const customRequestBody = ref<string>('')

  const isValidJson = computed<boolean>(() => {
    if (!customRequestBody.value.trim()) return true
    try {
      JSON.parse(customRequestBody.value)
      return true
    } catch {
      return false
    }
  })

  const canSend = computed<boolean>(() => {
    if (!customRequestMode.value) return true
    return isValidJson.value
  })

  function setMode(enabled: boolean, defaultPayload?: ChatCompletionRequest): void {
    customRequestMode.value = enabled
    if (enabled && defaultPayload) {
      customRequestBody.value = JSON.stringify(defaultPayload, null, 2)
    }
  }

  function setBody(body: string): void {
    customRequestBody.value = body
  }

  function formatJson(): boolean {
    try {
      const parsed = JSON.parse(customRequestBody.value)
      customRequestBody.value = JSON.stringify(parsed, null, 2)
      return true
    } catch {
      return false
    }
  }

  function reset(): void {
    customRequestMode.value = false
    customRequestBody.value = ''
  }

  return {
    customRequestMode,
    customRequestBody,
    isValidJson,
    canSend,
    setMode,
    setBody,
    formatJson,
    reset
  }
}
