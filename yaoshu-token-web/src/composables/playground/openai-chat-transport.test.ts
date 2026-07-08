/**
 * OpenAIChatTransport 单元测试。
 * 覆盖点：
 *   1. onUsage 回调触发（SSE chunk 携带 usage 字段）
 *   2. onSseEvent 回调触发
 *   3. text/reasoning delta 流式转换
 *   4. 错误响应解析（HTTP 429 + JSON body — 工单修复验证）
 *   5. 401 未授权处理
 *
 * Mock 策略：mock global.fetch（网络边界）+ @/utils/auth（token 边界），
 *           不 mock transport 本身逻辑。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/auth', () => ({
  getToken: vi.fn(() => 'test-token')
}))

/** 构造 SSE 格式的 ReadableStream body */
function makeSseBody(events: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder()
  return new ReadableStream({
    start(controller) {
      for (const ev of events) {
        controller.enqueue(encoder.encode(ev))
      }
      controller.close()
    }
  })
}

/** 读取 ReadableStream 全部 chunk */
async function readAllChunks<T>(stream: ReadableStream<T>): Promise<T[]> {
  const reader = stream.getReader()
  const chunks: T[] = []
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    chunks.push(value)
  }
  return chunks
}

const SEND_OPTIONS = {
  trigger: 'submit-message' as const,
  chatId: 'chat-1',
  messageId: undefined,
  messages: [],
  abortSignal: undefined,
  body: { model: 'gpt-4o', stream: true }
}

describe('OpenAIChatTransport', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  describe('onUsage 回调', () => {
    it('SSE chunk 携带 usage 时触发 onUsage 且参数正确', async () => {
      const onUsage = vi.fn()
      const transport = new (await import('./openai-chat-transport')).OpenAIChatTransport({ onUsage })

      vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(
        new Response(
          makeSseBody([
            'data: {"choices":[{"delta":{"content":"Hi"}}]}\n\n',
            'data: {"choices":[{"delta":{}}],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}\n\n',
            'data: [DONE]\n\n'
          ]),
          { status: 200, headers: { 'Content-Type': 'text/event-stream' } }
        )
      ))

      const stream = await transport.sendMessages(SEND_OPTIONS)
      await readAllChunks(stream)

      expect(onUsage).toHaveBeenCalledOnce()
      expect(onUsage).toHaveBeenCalledWith({
        prompt_tokens: 10,
        completion_tokens: 5,
        total_tokens: 15
      })
    })

    it('无 usage 字段的 chunk 不触发 onUsage', async () => {
      const onUsage = vi.fn()
      const transport = new (await import('./openai-chat-transport')).OpenAIChatTransport({ onUsage })

      vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(
        new Response(
          makeSseBody([
            'data: {"choices":[{"delta":{"content":"Hello"}}]}\n\n',
            'data: [DONE]\n\n'
          ]),
          { status: 200 }
        )
      ))

      const stream = await transport.sendMessages(SEND_OPTIONS)
      await readAllChunks(stream)

      expect(onUsage).not.toHaveBeenCalled()
    })
  })

  describe('text/reasoning delta 流式转换', () => {
    it('content delta 产生 text-start + text-delta + text-end', async () => {
      const transport = new (await import('./openai-chat-transport')).OpenAIChatTransport()

      vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(
        new Response(
          makeSseBody([
            'data: {"choices":[{"delta":{"content":"Hello"}}]}\n\n',
            'data: {"choices":[{"delta":{"content":" world"}}]}\n\n',
            'data: [DONE]\n\n'
          ]),
          { status: 200 }
        )
      ))

      const stream = await transport.sendMessages(SEND_OPTIONS)
      const chunks = await readAllChunks(stream) as Array<{ type: string; delta?: string }>
      const types = chunks.map((c) => c.type)

      expect(types).toContain('start')
      expect(types).toContain('start-step')
      expect(types).toContain('text-start')
      expect(types.filter((t) => t === 'text-delta')).toHaveLength(2)
      expect(types).toContain('text-end')
      expect(types).toContain('finish-step')
      expect(types).toContain('finish')
    })

    it('reasoning_content delta 产生 reasoning-start + reasoning-delta', async () => {
      const transport = new (await import('./openai-chat-transport')).OpenAIChatTransport()

      vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(
        new Response(
          makeSseBody([
            'data: {"choices":[{"delta":{"reasoning_content":"思考中"}}]}\n\n',
            'data: {"choices":[{"delta":{"content":"答案"}}]}\n\n',
            'data: [DONE]\n\n'
          ]),
          { status: 200 }
        )
      ))

      const stream = await transport.sendMessages(SEND_OPTIONS)
      const chunks = await readAllChunks(stream) as Array<{ type: string }>
      const types = chunks.map((c) => c.type)

      expect(types).toContain('reasoning-start')
      expect(types.filter((t) => t === 'reasoning-delta')).toHaveLength(1)
      expect(types).toContain('reasoning-end')
    })
  })

  describe('错误响应解析', () => {
    it('HTTP 429 + JSON error body → error chunk 包含 message（工单修复验证）', async () => {
      const transport = new (await import('./openai-chat-transport')).OpenAIChatTransport()

      vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            error: {
              message: '预扣费失败：额度不足',
              type: 'new_api_error',
              code: 'insufficient_user_quota'
            }
          }),
          { status: 429, headers: { 'Content-Type': 'application/json' } }
        )
      ))

      const stream = await transport.sendMessages(SEND_OPTIONS)
      const chunks = await readAllChunks(stream) as Array<{ type: string; errorText?: string }>
      const errorChunk = chunks.find((c) => c.type === 'error')

      expect(errorChunk).toBeDefined()
      expect(errorChunk?.errorText).toBe('预扣费失败：额度不足')
    })

    it('HTTP 500 无 body → error chunk 回退为 HTTP 状态码', async () => {
      const transport = new (await import('./openai-chat-transport')).OpenAIChatTransport()

      vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(
        new Response('', { status: 500 })
      ))

      const stream = await transport.sendMessages(SEND_OPTIONS)
      const chunks = await readAllChunks(stream) as Array<{ type: string; errorText?: string }>
      const errorChunk = chunks.find((c) => c.type === 'error')

      expect(errorChunk).toBeDefined()
      expect(errorChunk?.errorText).toBe('HTTP 500')
    })

    it('HTTP 401 → onUnauthorized 调用 + error chunk', async () => {
      const onUnauthorized = vi.fn()
      const transport = new (await import('./openai-chat-transport')).OpenAIChatTransport({ onUnauthorized })

      vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(
        new Response('', { status: 401 })
      ))

      const stream = await transport.sendMessages(SEND_OPTIONS)
      const chunks = await readAllChunks(stream) as Array<{ type: string }>

      expect(onUnauthorized).toHaveBeenCalledOnce()
      expect(chunks.some((c) => c.type === 'error')).toBe(true)
    })
  })

  describe('onSseEvent 回调', () => {
    it('每个有效 JSON chunk 触发 onSseEvent，[DONE] 不触发', async () => {
      const onSseEvent = vi.fn()
      const transport = new (await import('./openai-chat-transport')).OpenAIChatTransport({ onSseEvent })

      vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(
        new Response(
          makeSseBody([
            'data: {"choices":[{"delta":{"content":"Hi"}}]}\n\n',
            'data: {"choices":[{"delta":{"content":"!"}}]}\n\n',
            'data: [DONE]\n\n'
          ]),
          { status: 200 }
        )
      ))

      const stream = await transport.sendMessages(SEND_OPTIONS)
      await readAllChunks(stream)

      expect(onSseEvent).toHaveBeenCalledTimes(2)
    })
  })

  describe('自定义请求体覆盖', () => {
    it('customRequestBody 模式下 JSON 覆盖标准 payload', async () => {
      const transport = new (await import('./openai-chat-transport')).OpenAIChatTransport()
      const fetchMock = vi.fn().mockResolvedValueOnce(
        new Response(
          makeSseBody(['data: [DONE]\n\n']),
          { status: 200 }
        )
      )
      vi.stubGlobal('fetch', fetchMock)

      await transport.sendMessages({
        ...SEND_OPTIONS,
        body: {
          model: 'gpt-4o',
          stream: true,
          customRequestBody: JSON.stringify({ model: 'custom-model', messages: [], stream: true })
        }
      })

      const callBody = JSON.parse(fetchMock.mock.calls[0][1].body as string)
      expect(callBody.model).toBe('custom-model')
    })
  })

  describe('system prompt 前置注入', () => {
    /** 构造单条 UIMessage */
    function makeUserMessage(text: string) {
      return {
        id: 'm1',
        role: 'user' as const,
        parts: [{ type: 'text' as const, text }]
      }
    }

    it('systemPrompt 非空 → messages 数组首位注入 system 消息', async () => {
      const transport = new (await import('./openai-chat-transport')).OpenAIChatTransport()
      const fetchMock = vi.fn().mockResolvedValueOnce(
        new Response(makeSseBody(['data: [DONE]\n\n']), { status: 200 })
      )
      vi.stubGlobal('fetch', fetchMock)

      await transport.sendMessages({
        ...SEND_OPTIONS,
        messages: [makeUserMessage('你好')],
        body: { model: 'gpt-4o', stream: true, systemPrompt: '你是翻译助手' }
      })

      const callBody = JSON.parse(fetchMock.mock.calls[0][1].body as string)
      expect(callBody.messages[0]).toEqual({ role: 'system', content: '你是翻译助手' })
      expect(callBody.messages[1]).toEqual({ role: 'user', content: '你好' })
    })

    it('systemPrompt 为空字符串 → 不注入 system 消息', async () => {
      const transport = new (await import('./openai-chat-transport')).OpenAIChatTransport()
      const fetchMock = vi.fn().mockResolvedValueOnce(
        new Response(makeSseBody(['data: [DONE]\n\n']), { status: 200 })
      )
      vi.stubGlobal('fetch', fetchMock)

      await transport.sendMessages({
        ...SEND_OPTIONS,
        messages: [makeUserMessage('你好')],
        body: { model: 'gpt-4o', stream: true, systemPrompt: '' }
      })

      const callBody = JSON.parse(fetchMock.mock.calls[0][1].body as string)
      expect(callBody.messages).toHaveLength(1)
      expect(callBody.messages[0].role).toBe('user')
    })

    it('systemPrompt 仅空白 → trim 后跳过注入', async () => {
      const transport = new (await import('./openai-chat-transport')).OpenAIChatTransport()
      const fetchMock = vi.fn().mockResolvedValueOnce(
        new Response(makeSseBody(['data: [DONE]\n\n']), { status: 200 })
      )
      vi.stubGlobal('fetch', fetchMock)

      await transport.sendMessages({
        ...SEND_OPTIONS,
        messages: [makeUserMessage('你好')],
        body: { model: 'gpt-4o', stream: true, systemPrompt: '   \n  ' }
      })

      const callBody = JSON.parse(fetchMock.mock.calls[0][1].body as string)
      expect(callBody.messages).toHaveLength(1)
      expect(callBody.messages[0].role).toBe('user')
    })
  })
})
