/**
 * Chat Completion 请求体构建器。
 *
 * 三种模式：
 * 1. 自定义请求体模式（customRequestMode=true 且 JSON 合法）→ 直接 JSON.parse(customRequestBody) 覆盖
 * 2. 多模态模式（imageUrls.length>0）→ user message content 转为 ContentPart[]
 * 3. 标准模式（按 parameterEnabled 注入参数）
 */
import type {
  ChatCompletionRequest,
  Message,
  PlaygroundConfig,
  ParameterEnabled,
  ContentPart
} from '@/api/playground/types'
import {
  getCurrentVersion,
  formatMessageForAPI,
  isValidMessage
} from './message-utils'

export interface BuildPayloadOptions {
  customRequestMode?: boolean
  customRequestBody?: string
  imageUrls?: string[]
}

export function buildChatCompletionPayload(
  messages: Message[],
  config: PlaygroundConfig,
  parameterEnabled: ParameterEnabled,
  options: BuildPayloadOptions = {}
): ChatCompletionRequest {
  // 模式 1：自定义请求体模式
  if (options.customRequestMode && options.customRequestBody) {
    try {
      const parsed = JSON.parse(options.customRequestBody) as ChatCompletionRequest
      // 强制 stream 字段跟随 config.stream（避免用户误改）
      parsed.stream = config.stream
      return parsed
    } catch {
      // JSON 非法：调用方应已禁用发送按钮，此处兜底走标准模式
    }
  }

  // 模式 2/3：标准构建
  const processedMessages = messages
    .filter(isValidMessage)
    .map((msg) => formatMessageWithImages(msg, options.imageUrls ?? []))

  const payload: ChatCompletionRequest = {
    model: config.model,
    messages: processedMessages,
    stream: config.stream
  }

  // 按 parameterEnabled 注入参数（null/undefined 跳过，避免发空字段）
  const parameterKeys: Array<keyof ParameterEnabled> = [
    'temperature',
    'top_p',
    'max_tokens',
    'frequency_penalty',
    'presence_penalty',
    'seed'
  ]

  for (const key of parameterKeys) {
    if (!parameterEnabled[key]) continue
    const value = config[key as keyof PlaygroundConfig]
    if (value === undefined || value === null) continue
    ;(payload as unknown as Record<string, unknown>)[key] = value
  }

  return payload
}

/**
 * 将 message + imageUrls 转为 API ChatCompletionMessage。
 * - user + 有图片 → content 转为 ContentPart[]（text + image_url）
 * - 其他 → 纯文本
 */
function formatMessageWithImages(
  message: Message,
  imageUrls: string[]
): { role: Message['from']; content: string | ContentPart[] } {
  const base = formatMessageForAPI(message)
  // 仅 user 消息支持图片注入
  if (message.from !== 'user' || imageUrls.length === 0) {
    return base
  }

  const textContent = getCurrentVersion(message).content || ''
  const validImages = imageUrls.filter((u) => u.trim() !== '')

  const parts: ContentPart[] = [
    { type: 'text', text: textContent },
    ...validImages.map((url) => ({
      type: 'image_url' as const,
      image_url: { url: url.trim() }
    }))
  ]

  return { role: base.role, content: parts }
}
