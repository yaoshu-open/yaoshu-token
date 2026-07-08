/**
 * Playground 通用 SSE 流式请求封装。
 *
 * 设计考量：原生 fetch + ReadableStream 方案（零新依赖 + 完全可控 + AbortController 原生中断）。
 * 不沿用 default 的 sse.js 第三方小库（维护方个人、readyState 检测 hacky）。
 *
 * 关键约束：
 * 1. 不经 axios 拦截器（流式无 Content-Type 标准化 + 401 mid-stream 集成需独立处理）
 * 2. baseURL 与 request.ts 对齐（import.meta.env.VITE_API_BASE，空串=同源走 vite proxy）
 * 3. 401 集成走 onUnauthorized 回调（避免 utils 直接依赖 store + router，保持解耦）
 * 4. PG-C4 心跳检测：流传输期间定期（30s）校验 token 有效性，mid-stream 401 触发 onUnauthorized
 *
 * 协议：OpenAI 兼容 SSE
 * - 每个事件块以 \n\n 分隔
 * - data: <json> 或 data: [DONE]
 * - 增量 delta 字段：choices[0].delta.content / choices[0].delta.reasoning_content
 */
import type { ChatCompletionChunk, ChatCompletionRequest } from '@/api/playground/types'
import { getToken } from './auth'

const BASE_URL = import.meta.env.VITE_API_BASE || ''
const SSE_PATH = '/pg/chat/completions'
// PG-C4 心跳检测：流传输期间定期校验 token，30s 间隔平衡及时性与服务器负载
const HEARTBEAT_INTERVAL_MS = 30_000
const HEARTBEAT_PATH = '/api/user/self'

export interface SseStreamCallbacks {
  /** reasoning 增量（API reasoning_content 字段） */
  onReasoning?: (chunk: string) => void
  /** 正文增量（API content 字段） */
  onContent?: (chunk: string) => void
  /** 原始事件（用于调试 SSE 时间线） */
  onEvent?: (raw: string, parsed?: ChatCompletionChunk | null) => void
  /** 流式正常结束（data: [DONE]） */
  onComplete?: () => void
  /** 业务错误或网络错误 */
  onError?: (message: string, code?: string) => void
  /** 401 未授权：默认清 token + 跳登录；调用方可覆盖做自定义处理 */
  onUnauthorized?: () => void
}

export interface SseStreamHandle {
  /** 中止当前请求（Stop 按钮触发） */
  abort: () => void
}

interface ErrorPayload {
  error?: { message?: string; code?: string }
  message?: string
}

function extractErrorMessage(data: ErrorPayload | string | undefined): {
  message: string
  code?: string
} {
  if (!data) return { message: '请求失败' }
  if (typeof data === 'string') return { message: data }
  if (data.error?.message) {
    return { message: data.error.message, code: data.error.code }
  }
  if (data.message) return { message: data.message }
  return { message: '请求失败' }
}

export function sendSseStreamRequest(
  payload: ChatCompletionRequest,
  callbacks: SseStreamCallbacks
): SseStreamHandle {
  const controller = new AbortController()
  const { signal } = controller

  const url = `${BASE_URL}${SSE_PATH}`
  const token = getToken()

  // PG-C4 心跳检测：流传输期间定期校验 token，mid-stream 401 触发 onUnauthorized
  let heartbeatId: ReturnType<typeof setInterval> | null = null
  let unauthorizedTriggered = false

  function startHeartbeat(): void {
    if (heartbeatId !== null) return
    heartbeatId = setInterval(async () => {
      if (signal.aborted || unauthorizedTriggered) return
      try {
        const res = await fetch(`${BASE_URL}${HEARTBEAT_PATH}`, {
          headers: { ...(token ? { 'yaoshu-token': token } : {}) },
          // 心跳请求不跟随主 controller（避免主 abort 干扰心跳响应解析）
        })
        if (res.status === 401) {
          unauthorizedTriggered = true
          stopHeartbeat()
          controller.abort()
          callbacks.onUnauthorized?.()
          callbacks.onError?.('登录已过期，请重新登录', 'unauthorized')
        }
        // 非 401（含 200/5xx）：不中断流，避免临时服务器抖动误杀活跃会话
      } catch {
        // 网络错误：忽略，避免与流式错误混淆
      }
    }, HEARTBEAT_INTERVAL_MS)
  }

  function stopHeartbeat(): void {
    if (heartbeatId !== null) {
      clearInterval(heartbeatId)
      heartbeatId = null
    }
  }

  // 异步执行（fetch 自身返回 Promise，但 streaming read 是同步循环）
  ;(async () => {
    let response: Response
    try {
      response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
          // Sa-Token 会话认证：token-name 为 yaoshu-token（与 axios 拦截器一致）
          ...(token ? { 'yaoshu-token': token } : {})
        },
        body: JSON.stringify(payload),
        signal
      })
    } catch (err) {
      if (signal.aborted) return // 用户主动停止：静默
      const message = err instanceof Error ? err.message : '网络异常'
      callbacks.onError?.(message)
      return
    }

    if (signal.aborted) return

    // 401 集成：清 token + 跳登录（与 request.ts 401 策略对齐）
    if (response.status === 401) {
      callbacks.onUnauthorized?.()
      callbacks.onError?.('登录已过期，请重新登录', 'unauthorized')
      return
    }

    // 非 200：从 body 解析 error.message/code
    if (!response.ok || !response.body) {
      try {
        const errBody = (await response.json()) as ErrorPayload
        const { message, code } = extractErrorMessage(errBody)
        callbacks.onError?.(message, code)
      } catch {
        callbacks.onError?.(`HTTP ${response.status}`)
      }
      return
    }

    // PG-C4：流开始读取时启动心跳检测（流前 401 已处理，此处检测 mid-stream 401）
    startHeartbeat()

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    try {
      while (true) {
        if (signal.aborted) return
        const { value, done } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })

        // 按 \n\n 分割事件块
        let sepIndex: number

        while ((sepIndex = buffer.indexOf('\n\n')) !== -1) {
          const rawBlock = buffer.slice(0, sepIndex)
          buffer = buffer.slice(sepIndex + 2)
          processEventBlock(rawBlock, callbacks)
        }
      }
      // 流结束：处理 buffer 残余
      if (buffer.trim().length > 0) {
        processEventBlock(buffer, callbacks)
      }
      callbacks.onComplete?.()
    } catch (err) {
      if (signal.aborted) return
      const message = err instanceof Error ? err.message : '流式连接异常'
      callbacks.onError?.(message)
    } finally {
      stopHeartbeat()
      reader.releaseLock()
    }
  })()

  return {
    abort: () => {
      stopHeartbeat()
      controller.abort()
    }
  }
}

function processEventBlock(
  block: string,
  callbacks: SseStreamCallbacks
): void {
  // 提取 data: 行（可能跨多行，但常见为单行）
  const lines = block.split('\n')
  const dataLines: string[] = []
  for (const line of lines) {
    if (line.startsWith('data:')) {
      // 保留第一个空格之后的内容（"data: foo" → "foo"），但允许空 payload（"data:"）
      dataLines.push(line.length > 5 && line[5] === ' ' ? line.slice(6) : line.slice(5))
    }
  }
  if (dataLines.length === 0) return
  const data = dataLines.join('\n')

  // [DONE] 结束标记
  if (data === '[DONE]') {
    callbacks.onComplete?.()
    return
  }

  let parsed: ChatCompletionChunk | null = null
  try {
    parsed = JSON.parse(data) as ChatCompletionChunk
  } catch {
    // 解析失败：仍然上报原始数据（调试场景）
    callbacks.onEvent?.(data, null)
    return
  }

  callbacks.onEvent?.(data, parsed)

  const delta = parsed.choices?.[0]?.delta
  if (delta) {
    if (delta.reasoning_content) {
      callbacks.onReasoning?.(delta.reasoning_content)
    }
    if (delta.content) {
      callbacks.onContent?.(delta.content)
    }
  }
}
