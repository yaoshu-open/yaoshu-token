/**
 * Playground Message 工具集。
 *
 * 关键能力：
 * - Message/MessageVersion CRUD（getCurrentVersion/updateCurrentVersionContent）
 * - think 标签解析（parseThinkTags，区分完整/未闭合）
 * - 流式增量处理（processStreamingContent）
 * - 流式收尾（finalizeMessage：剥离 think 标签 + 汇总 reasoning）
 * - 加载时清洗（sanitizeMessagesOnLoad：loading/streaming 状态自救）
 * - 错误标记（updateAssistantMessageWithError）
 */
import i18n from '@/plugins/i18n'
import { MESSAGE_ROLES, MESSAGE_STATUS, ERROR_MESSAGES } from '../constants'
import type {
  Message,
  MessageVersion,
  ChatCompletionMessage,
  ContentPart
} from '@/api/playground/types'

// 简易 ID 生成器（避免引入 nanoid 新依赖）
let idCounter = 0
function genId(): string {
  idCounter += 1
  return `${Date.now().toString(36)}-${idCounter.toString(36)}-${Math.random().toString(36).slice(2, 8)}`
}

export function createMessageVersion(content: string): MessageVersion {
  return { id: genId(), content }
}

export function getCurrentVersion(message: Message): MessageVersion {
  return message.versions[0] || { id: 'default', content: '' }
}

export function updateCurrentVersionContent(
  message: Message,
  content: string
): Message {
  const current = getCurrentVersion(message)
  return {
    ...message,
    versions: [{ ...current, content }]
  }
}

export function createUserMessage(content: string): Message {
  return {
    key: genId(),
    from: MESSAGE_ROLES.USER,
    versions: [createMessageVersion(content)]
  }
}

export function createLoadingAssistantMessage(): Message {
  return {
    key: genId(),
    from: MESSAGE_ROLES.ASSISTANT,
    versions: [createMessageVersion('')],
    reasoning: undefined,
    isReasoningComplete: false,
    isContentComplete: false,
    isReasoningStreaming: false,
    status: MESSAGE_STATUS.LOADING
  }
}

/**
 * 多模态文本构造：text + imageUrls → ContentPart[] 或纯文本
 */
export function buildMessageContent(
  text: string,
  imageUrls: string[] = []
): string | ContentPart[] {
  const validImages = imageUrls.filter((u) => u.trim() !== '')
  if (validImages.length === 0) return text
  return [
    { type: 'text', text: text || '' },
    ...validImages.map((url) => ({
      type: 'image_url' as const,
      image_url: { url: url.trim() }
    }))
  ]
}

/**
 * 提取 ContentPart[] 中的纯文本
 */
export function getTextContent(content: string | ContentPart[]): string {
  if (typeof content === 'string') return content
  if (Array.isArray(content)) {
    const textPart = content.find((p) => p.type === 'text')
    return textPart?.text || ''
  }
  return ''
}

/**
 * 格式化为 API 入参形态
 */
export function formatMessageForAPI(message: Message): ChatCompletionMessage {
  const current = getCurrentVersion(message)
  return { role: message.from, content: current.content }
}

/**
 * 是否为合法可发送的 message
 * - 必须有 from + versions
 * - assistant 空内容（loading/streaming 占位）排除
 */
export function isValidMessage(message: Message): boolean {
  if (!message || !message.from || !message.versions.length) return false
  const content = message.versions[0]?.content
  if (content === undefined) return false
  if (message.from === 'assistant' && !content.trim()) return false
  return true
}

/**
 * 解析 <think>...</think> 标签
 * - 完整配对 → 拆为 visibleContent + reasoning
 * - 未闭合 → 剩余 reasoning 累积 + hasUnclosedTag=true
 */
export function parseThinkTags(content: string): {
  visibleContent: string
  reasoning: string
  hasUnclosedTag: boolean
} {
  if (!content.includes('<think>')) {
    return { visibleContent: content, reasoning: '', hasUnclosedTag: false }
  }

  const visibleParts: string[] = []
  const reasoningParts: string[] = []
  let currentPos = 0
  let hasUnclosed = false

  while (true) {
    const openPos = content.indexOf('<think>', currentPos)
    if (openPos === -1) {
      if (currentPos < content.length) {
        visibleParts.push(content.substring(currentPos))
      }
      break
    }

    if (openPos > currentPos) {
      visibleParts.push(content.substring(currentPos, openPos))
    }

    const closePos = content.indexOf('</think>', openPos + 7)
    if (closePos === -1) {
      reasoningParts.push(content.substring(openPos + 7))
      hasUnclosed = true
      break
    }

    reasoningParts.push(content.substring(openPos + 7, closePos))
    currentPos = closePos + 8
  }

  return {
    visibleContent: visibleParts.join('').trim(),
    reasoning: reasoningParts.join('\n\n').trim(),
    hasUnclosedTag: hasUnclosed
  }
}

/**
 * 更新最后一条 assistant 消息
 */
export function updateLastAssistantMessage(
  messages: Message[],
  updater: (message: Message) => Message
): Message[] {
  if (messages.length === 0) return messages
  const last = messages[messages.length - 1]
  if (!last || last.from !== MESSAGE_ROLES.ASSISTANT) return messages
  const updated = [...messages]
  updated[updated.length - 1] = updater(last)
  return updated
}

/**
 * 流式增量处理：附加 content chunk + 同步解析 think 标签
 */
export function processStreamingContent(
  message: Message,
  contentChunk?: string
): Message {
  const current = getCurrentVersion(message)
  const fullContent = contentChunk
    ? current.content + contentChunk
    : current.content

  const { reasoning, hasUnclosedTag } = parseThinkTags(fullContent)

  // 优先保留已有 reasoning（API reasoning_content 已记录）
  const finalReasoning = reasoning
    ? { content: reasoning, duration: 0 }
    : message.reasoning

  return {
    ...updateCurrentVersionContent(message, fullContent),
    reasoning: finalReasoning,
    isReasoningStreaming: hasUnclosedTag
  }
}

/**
 * 流式收尾：剥离 think 标签 + 汇总 reasoning 来源优先级
 * 1. apiReasoningContent 参数（非流式响应）
 * 2. message.reasoning（流式 API reasoning_content）
 * 3. content 中的 think 标签
 */
export function finalizeMessage(
  message: Message,
  apiReasoningContent?: string
): Message {
  const current = getCurrentVersion(message)
  const { visibleContent, reasoning } = parseThinkTags(current.content)

  const finalReasoning =
    apiReasoningContent || message.reasoning?.content || reasoning || ''

  return {
    ...updateCurrentVersionContent(message, visibleContent),
    reasoning: finalReasoning
      ? {
          content: finalReasoning,
          duration: message.reasoning?.duration || 0
        }
      : undefined,
    isReasoningStreaming: false
  }
}

/**
 * 标记最后一条 assistant 为错误态
 */
export function updateAssistantMessageWithError(
  messages: Message[],
  errorMessage: string,
  errorCode?: string
): Message[] {
  return updateLastAssistantMessage(messages, (message) => {
    const updated = updateCurrentVersionContent(
      message,
      `${i18n.global.t(ERROR_MESSAGES.API_REQUEST_ERROR)}: ${errorMessage}`
    )
    return {
      ...updated,
      status: MESSAGE_STATUS.ERROR,
      isReasoningStreaming: false,
      errorCode: errorCode || null
    }
  })
}

/**
 * 加载时清洗：把异常停留在 loading/streaming 的 assistant 标记为 complete 或 error
 */
export function sanitizeMessagesOnLoad(messages: Message[]): Message[] {
  let targetIndex = -1
  for (let i = messages.length - 1; i >= 0; i -= 1) {
    const m = messages[i]
    if (
      m?.from === MESSAGE_ROLES.ASSISTANT &&
      (m?.status === MESSAGE_STATUS.LOADING ||
        m?.status === MESSAGE_STATUS.STREAMING)
    ) {
      targetIndex = i
      break
    }
  }
  if (targetIndex === -1) return messages

  const finalized = finalizeMessage(messages[targetIndex])
  const hasContent = finalized.versions?.[0]?.content?.trim()
  const hasReasoning = finalized.reasoning?.content?.trim()

  const sanitized: Message =
    hasContent || hasReasoning
      ? {
          ...finalized,
          status: MESSAGE_STATUS.COMPLETE,
          isReasoningStreaming: false
        }
      : {
          ...updateCurrentVersionContent(
            finalized,
            `${i18n.global.t(ERROR_MESSAGES.API_REQUEST_ERROR)}: ${i18n.global.t(ERROR_MESSAGES.INTERRUPTED)}`
          ),
          status: MESSAGE_STATUS.ERROR,
          isReasoningStreaming: false
        }

  const result = [...messages]
  result[targetIndex] = sanitized
  return result
}
