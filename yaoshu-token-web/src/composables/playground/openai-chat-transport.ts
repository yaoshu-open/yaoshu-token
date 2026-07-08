/**
 * OpenAI 兼容 ChatTransport — 桥接 @ai-sdk/vue Chat 与 /pg/chat/completions。
 *
 * 职责：
 * 1. 将 UIMessage[] 转换为 OpenAI ChatCompletionMessage[] 格式
 * 2. 合并 Playground 参数（model/group/temperature 等）来自 body 选项
 * 3. 支持自定义请求体覆盖（customRequestBody）
 * 4. 发送 POST 到 /pg/chat/completions，解析 SSE
 * 5. 将 OpenAI delta 转换为 UIMessageChunk 流（text-delta / reasoning-delta / finish）
 * 6. 支持 SSE 事件回调（调试面板消费）
 */
import type { ChatTransport, UIMessage, UIMessageChunk } from 'ai'
import type {
  ChatCompletionChunk,
  ChatCompletionMessage,
  ChatCompletionRequest,
  ContentPart
} from '@/api/playground/types'
import { getToken } from '@/utils/auth'

const DEFAULT_API = '/pg/chat/completions'
const BASE_URL = import.meta.env.VITE_API_BASE || ''

/** Transport 构造选项 */
export interface OpenAIChatTransportOptions {
  api?: string
  baseURL?: string
  onUnauthorized?: () => void
  onSseEvent?: (raw: string, parsed?: ChatCompletionChunk | null) => void
  /** SSE 流式 usage 提取回调（最后一个 chunk 可能携带） */
  onUsage?: (usage: { prompt_tokens: number; completion_tokens: number; total_tokens: number }) => void
}

/** 从 sendMessage body 传入的 Playground 参数 */
export interface PlaygroundChatBody {
  model?: string
  stream?: boolean
  temperature?: number
  top_p?: number
  max_tokens?: number
  frequency_penalty?: number
  presence_penalty?: number
  seed?: number | null
  customRequestBody?: string
  imageUrls?: string[]
  /** system prompt（每次请求前置注入到 messages 数组最前，空则不注入） */
  systemPrompt?: string
}

function generateId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
}

/** 将 UIMessage[] 转为 OpenAI ChatCompletionMessage[] */
function toOpenAIMessages(
  messages: UIMessage[],
  imageUrls?: string[]
): ChatCompletionMessage[] {
  return messages
    .map((msg, index) => {
      const text = msg.parts
        .flatMap((p) => (p.type === 'text' ? [p.text] : []))
        .join('')

      // 最后一条 user 消息支持图片注入
      if (
        msg.role === 'user' &&
        imageUrls &&
        imageUrls.length > 0 &&
        index === messages.length - 1
      ) {
        const parts: ContentPart[] = [
          { type: 'text', text },
          ...imageUrls
            .filter(u => u.trim())
            .map(url => ({ type: 'image_url' as const, image_url: { url: url.trim() } }))
        ]
        return { role: msg.role, content: parts }
      }

      return { role: msg.role, content: text }
    })
}

/** 从 SSE 事件块提取 data 内容 */
function extractData(block: string): string | null {
  const lines = block.split('\n')
  const dataLines: string[] = []
  for (const line of lines) {
    if (line.startsWith('data:')) {
      dataLines.push(line.length > 5 && line[5] === ' ' ? line.slice(6) : line.slice(5))
    }
  }
  return dataLines.length > 0 ? dataLines.join('\n') : null
}

export class OpenAIChatTransport implements ChatTransport<UIMessage> {
  private api: string
  private baseURL: string
  private onUnauthorized?: () => void
  private onSseEvent?: (raw: string, parsed?: ChatCompletionChunk | null) => void
  private onUsage?: OpenAIChatTransportOptions['onUsage']

  constructor(options: OpenAIChatTransportOptions = {}) {
    this.api = options.api ?? DEFAULT_API
    this.baseURL = options.baseURL ?? BASE_URL
    this.onUnauthorized = options.onUnauthorized
    this.onSseEvent = options.onSseEvent
    this.onUsage = options.onUsage
  }

  async sendMessages(options: {
    trigger: 'submit-message' | 'regenerate-message'
    chatId: string
    messageId: string | undefined
    messages: UIMessage[]
    abortSignal: AbortSignal | undefined
    headers?: Record<string, string> | Headers
    body?: object
    metadata?: unknown
  }): Promise<ReadableStream<UIMessageChunk>> {
    const body = (options.body ?? {}) as PlaygroundChatBody
    const payload = this.buildPayload(options.messages, body)

    const url = `${this.baseURL}${this.api}`
    const token = getToken()

    return new ReadableStream<UIMessageChunk>({
      start: (controller) => {
        void this.processStream(controller, url, payload, token, options.abortSignal)
      },
      cancel: () => {
        // abortSignal 由 Chat 类管理，fetch 会自动中断
      }
    })
  }

  async reconnectToStream(): Promise<ReadableStream<UIMessageChunk> | null> {
    return null
  }

  /** 构建请求 payload（支持自定义请求体覆盖） */
  private buildPayload(messages: UIMessage[], body: PlaygroundChatBody): ChatCompletionRequest {
    // 自定义请求体模式
    if (body.customRequestBody) {
      try {
        const parsed = JSON.parse(body.customRequestBody) as ChatCompletionRequest
        parsed.stream = body.stream ?? true
        return parsed
      } catch {
        // JSON 非法：走标准模式
      }
    }

    // 标准模式
    const openaiMessages = toOpenAIMessages(messages, body.imageUrls)

    // system prompt 前置注入（空则跳过）
    const sysPrompt = body.systemPrompt?.trim()
    if (sysPrompt) {
      openaiMessages.unshift({ role: 'system', content: sysPrompt })
    }

    const payload: ChatCompletionRequest = {
      model: body.model ?? '',
      messages: openaiMessages,
      stream: body.stream ?? true
    }

    // 注入可选参数
    if (body.temperature !== undefined) payload.temperature = body.temperature
    if (body.top_p !== undefined) payload.top_p = body.top_p
    if (body.max_tokens !== undefined) payload.max_tokens = body.max_tokens
    if (body.frequency_penalty !== undefined) payload.frequency_penalty = body.frequency_penalty
    if (body.presence_penalty !== undefined) payload.presence_penalty = body.presence_penalty
    if (body.seed !== undefined && body.seed !== null) payload.seed = body.seed

    return payload
  }

  /** 处理 SSE 流并转换为 UIMessageChunk */
  private async processStream(
    controller: ReadableStreamDefaultController<UIMessageChunk>,
    url: string,
    payload: ChatCompletionRequest,
    token: string,
    abortSignal?: AbortSignal
  ): Promise<void> {
    controller.enqueue({ type: 'start' })
    controller.enqueue({ type: 'start-step' })

    const textId = generateId()
    const reasoningId = generateId()
    let textStarted = false
    let reasoningStarted = false

    try {
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
          // Sa-Token 会话认证：token-name 为 yaoshu-token（与 axios 拦截器一致）
          ...(token ? { 'yaoshu-token': token } : {})
        },
        body: JSON.stringify(payload),
        signal: abortSignal
      })

      if (response.status === 401) {
        this.onUnauthorized?.()
        controller.enqueue({ type: 'error', errorText: '登录已过期，请重新登录' })
        controller.close()
        return
      }

      if (!response.ok || !response.body) {
        let errorMsg = `HTTP ${response.status}`
        try {
          const errBody = await response.json()
          errorMsg = errBody?.error?.message || errBody?.message || errorMsg
        } catch {
          // 响应体解析失败，使用默认错误信息
        }
        controller.enqueue({ type: 'error', errorText: errorMsg })
        controller.close()
        return
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      // 事件块处理闭包（捕获 textStarted/reasoningStarted 状态）
      const processBlock = (block: string): boolean => {
        const data = extractData(block)
        if (!data) return false

        if (data === '[DONE]') {
          if (textStarted) controller.enqueue({ type: 'text-end', id: textId })
          if (reasoningStarted) controller.enqueue({ type: 'reasoning-end', id: reasoningId })
          controller.enqueue({ type: 'finish-step' })
          controller.enqueue({ type: 'finish', finishReason: 'stop' })
          controller.close()
          return true
        }

        let parsed: ChatCompletionChunk | null = null
        try {
          parsed = JSON.parse(data) as ChatCompletionChunk
        } catch {
          this.onSseEvent?.(data, null)
          return false
        }

        this.onSseEvent?.(data, parsed)

        // PG-E05: 提取 usage（流式最后一个 chunk 可能携带）
        if (parsed.usage) {
          this.onUsage?.(parsed.usage)
        }

        const delta = parsed.choices?.[0]?.delta
        if (delta?.reasoning_content) {
          if (!reasoningStarted) {
            controller.enqueue({ type: 'reasoning-start', id: reasoningId })
            reasoningStarted = true
          }
          controller.enqueue({ type: 'reasoning-delta', id: reasoningId, delta: delta.reasoning_content })
        }
        if (delta?.content) {
          if (!textStarted) {
            controller.enqueue({ type: 'text-start', id: textId })
            textStarted = true
          }
          controller.enqueue({ type: 'text-delta', id: textId, delta: delta.content })
        }
        return false
      }

      while (true) {
        if (abortSignal?.aborted) {
          controller.enqueue({ type: 'abort' })
          break
        }
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })

        let sepIndex: number
        while ((sepIndex = buffer.indexOf('\n\n')) !== -1) {
          const rawBlock = buffer.slice(0, sepIndex)
          buffer = buffer.slice(sepIndex + 2)
          if (processBlock(rawBlock)) return
        }
      }

      // 流结束（可能无 [DONE] 标记）
      if (textStarted) controller.enqueue({ type: 'text-end', id: textId })
      if (reasoningStarted) controller.enqueue({ type: 'reasoning-end', id: reasoningId })
      controller.enqueue({ type: 'finish-step' })
      controller.enqueue({ type: 'finish', finishReason: 'stop' })
      controller.close()
    } catch (error) {
      if (abortSignal?.aborted) {
        if (textStarted) controller.enqueue({ type: 'text-end', id: textId })
        if (reasoningStarted) controller.enqueue({ type: 'reasoning-end', id: reasoningId })
        controller.enqueue({ type: 'abort' })
        controller.close()
        return
      }
      const message = error instanceof Error ? error.message : '流式连接异常'
      controller.enqueue({ type: 'error', errorText: message })
      controller.close()
    }
  }

}
