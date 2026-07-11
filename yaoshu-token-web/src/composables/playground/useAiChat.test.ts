/**
 * useAiChat composable 单元测试。
 * 覆盖点：
 *   1. usageMap 记录（sendMessage 后 onUsage → pendingUsage → recordUsageForLastAssistant）
 *   2. clearMessages 清空 messages + usageMap + localStorage
 *   3. buildBody 构建参数透传
 *   4. clearError 透传
 *
 * Mock 策略：mock @ai-sdk/vue Chat（响应式 messages + 触发 onUsage 回调）
 *           + ./openai-chat-transport（存储 onUsage 供 Chat mock 调用）
 *           + ./usePlaygroundState / useCustomRequest / useDebugPanel（状态边界）
 *           + vue-router / pinia auth（路由与认证边界），不 mock useAiChat 本身。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { UIMessage } from 'ai'

// Mock OpenAIChatTransport：存储 onUsage 回调供 Chat mock 触发
vi.mock('./openai-chat-transport', () => ({
  OpenAIChatTransport: class MockTransport {
    onUsage: any
    onSseEvent: any
    onUnauthorized: any
    constructor(options: any) {
      this.onUsage = options.onUsage
      this.onSseEvent = options.onSseEvent
      this.onUnauthorized = options.onUnauthorized
    }
    async sendMessages() {
      return new ReadableStream({ start(c) { c.close() } })
    }
    async reconnectToStream() {
      return null
    }
  }
}))

// Mock Chat：reactive 对象（支持 chat.messages = [] 赋值触发 computed 重算）
vi.mock('@ai-sdk/vue', async () => {
  const { reactive } = await import('vue')
  return {
    Chat: class {
      constructor(options: any) {
        // constructor 返回 reactive 对象（new 表达式取返回值）
        return reactive({
          messages: options?.messages ?? [],
          status: 'ready',
          error: null as Error | null,
          async sendMessage(msg: { text: string }) {
            options.transport?.onUsage?.({
              prompt_tokens: 10,
              completion_tokens: 5,
              total_tokens: 15
            })
            this.messages.push({
              id: 'u1',
              role: 'user',
              parts: [{ type: 'text', text: msg.text }]
            } as UIMessage)
            this.messages.push({
              id: 'a1',
              role: 'assistant',
              parts: [{ type: 'text', text: 'response' }]
            } as UIMessage)
          },
          async regenerate(_messageId?: string) {
            options.transport?.onUsage?.({
              prompt_tokens: 8,
              completion_tokens: 3,
              total_tokens: 11
            })
          },
          async stop() {},
          clearError() {
            this.error = null
          }
        })
      }
    }
  }
})

vi.mock('./usePlaygroundState', () => ({
  usePlaygroundState: () => ({
    config: {
      value: {
        model: 'gpt-4o',
        group: 'default',
        stream: true,
        temperature: 0.7,
        top_p: 1,
        max_tokens: 4096,
        frequency_penalty: 0,
        presence_penalty: 0,
        seed: null,
        systemPrompt: ''
      }
    },
    parameterEnabled: {
      value: {
        temperature: true,
        top_p: true,
        max_tokens: false,
        frequency_penalty: true,
        presence_penalty: true,
        seed: false
      }
    }
  })
}))

vi.mock('./useCustomRequest', () => ({
  useCustomRequest: () => ({
    customRequestMode: { value: false },
    customRequestBody: { value: '' },
    isValidJson: { value: true },
    canSend: { value: true }
  })
}))

vi.mock('./useDebugPanel', () => ({
  useDebugPanel: () => ({
    clearSseEvents: vi.fn(),
    setPreviewPayload: vi.fn(),
    setActualRequest: vi.fn(),
    appendSseEvent: vi.fn()
  })
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({
    currentRoute: { value: { fullPath: '/playground' } },
    push: vi.fn()
  })
}))

vi.mock('@/store/modules/auth', () => ({
  useAuthStore: () => ({
    clearAuthToken: vi.fn(),
    userInfo: { id: 'test-user' }
  })
}))

describe('useAiChat', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  afterEach(() => {
    vi.resetModules()
  })

  describe('usageMap 记录', () => {
    it('sendMessage 后最后一条 assistant 消息记录 usage', async () => {
      const { useAiChat } = await import('./useAiChat')
      const { sendMessage, usageMap } = useAiChat()

      await sendMessage('Hello')

      expect(usageMap.value['a1']).toBeDefined()
      expect(usageMap.value['a1'].totalTokens).toBe(15)
      expect(usageMap.value['a1'].promptTokens).toBe(10)
      expect(usageMap.value['a1'].completionTokens).toBe(5)
    })

    it('sendMessage 后 usage 包含 duration 耗时', async () => {
      const { useAiChat } = await import('./useAiChat')
      const { sendMessage, usageMap } = useAiChat()

      await sendMessage('Hello')

      expect(usageMap.value['a1'].duration).toBeDefined()
      expect(typeof usageMap.value['a1'].duration).toBe('number')
    })

    it('regenerate 后也记录 usage', async () => {
      const { useAiChat } = await import('./useAiChat')
      const { regenerate, usageMap, sendMessage } = useAiChat()

      // 先 sendMessage 建立消息
      await sendMessage('Hello')
      // 再 regenerate
      await regenerate('a1')

      expect(usageMap.value['a1']).toBeDefined()
    })
  })

  describe('clearMessages', () => {
    it('清空 messages 和 usageMap', async () => {
      const { useAiChat } = await import('./useAiChat')
      const { sendMessage, clearMessages, usageMap, messages } = useAiChat()

      await sendMessage('Hello')
      expect(usageMap.value['a1']).toBeDefined()
      expect(messages.value.length).toBeGreaterThan(0)

      clearMessages()

      expect(usageMap.value).toEqual({})
      expect(messages.value).toHaveLength(0)
    })

    it('清空 localStorage 中的消息缓存', async () => {
      const { useAiChat } = await import('./useAiChat')
      const { sendMessage, clearMessages } = useAiChat()

      await sendMessage('Hello')
      // watch 会将 messages 写入 localStorage（key 按用户隔离）
      expect(localStorage.getItem('playground_messages:test-user')).not.toBeNull()

      clearMessages()
      expect(localStorage.getItem('playground_messages:test-user')).toBeNull()
    })
  })

  describe('buildBody', () => {
    it('正确构建 body 参数', async () => {
      const { useAiChat } = await import('./useAiChat')
      const { buildBody } = useAiChat()

      const body = buildBody()
      expect(body.model).toBe('gpt-4o')
      expect(body.stream).toBe(true)
      // max_tokens 未启用（parameterEnabled.max_tokens=false）
      expect(body.max_tokens).toBeUndefined()
      // temperature 已启用
      expect(body.temperature).toBe(0.7)
    })

    it('imageUrls 默认为空数组', async () => {
      const { useAiChat } = await import('./useAiChat')
      const { buildBody } = useAiChat()

      const body = buildBody()
      expect(body.imageUrls).toEqual([])
    })

    it('systemPrompt 从 config 透传到 body', async () => {
      const { useAiChat } = await import('./useAiChat')
      const { buildBody, state } = useAiChat()

      // 默认空串
      expect(buildBody().systemPrompt).toBe('')

      // 修改后透传
      state.config.value.systemPrompt = '你是翻译助手'
      expect(buildBody().systemPrompt).toBe('你是翻译助手')
    })
  })

  describe('insertMessage', () => {
    it('插入 assistant 消息到 messages 数组（不触发请求）', async () => {
      const { useAiChat } = await import('./useAiChat')
      const { insertMessage, messages } = useAiChat()

      const beforeLen = messages.value.length
      insertMessage('assistant', '预填的助手回复')

      expect(messages.value).toHaveLength(beforeLen + 1)
      const inserted = messages.value[messages.value.length - 1]
      expect(inserted.role).toBe('assistant')
      expect(inserted.parts[0]).toEqual({ type: 'text', text: '预填的助手回复' })
    })

    it('插入 system 消息（用于 few-shot / 中场指令）', async () => {
      const { useAiChat } = await import('./useAiChat')
      const { insertMessage, messages } = useAiChat()

      insertMessage('system', '请用JSON格式回复')

      const inserted = messages.value[messages.value.length - 1]
      expect(inserted.role).toBe('system')
      expect(inserted.parts[0]).toMatchObject({ type: 'text', text: '请用JSON格式回复' })
    })

    it('空白文本 trim 后跳过插入', async () => {
      const { useAiChat } = await import('./useAiChat')
      const { insertMessage, messages } = useAiChat()

      const beforeLen = messages.value.length
      insertMessage('assistant', '   \n  ')

      expect(messages.value).toHaveLength(beforeLen)
    })
  })

  describe('clearError', () => {
    it('调用 chat.clearError', async () => {
      const { useAiChat } = await import('./useAiChat')
      const { clearError, error } = useAiChat()

      clearError()
      expect(error.value).toBeNull()
    })
  })

  describe('isGenerating', () => {
    it('初始状态为 false', async () => {
      const { useAiChat } = await import('./useAiChat')
      const { isGenerating } = useAiChat()

      expect(isGenerating.value).toBe(false)
    })
  })
})
