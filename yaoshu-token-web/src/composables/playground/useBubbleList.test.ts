/**
 * useBubbleList composable 单元测试。
 * 覆盖点：
 *   1. 时间戳记录（首次见到消息 id 时记录，后续不变）
 *   2. clearTimestamps 清空缓存
 *   3. usage 注入（assistant 消息从 usageMap 获取用量）
 *   4. textContent / reasoningContent 提取
 *   5. isLoading / isReasoningStreaming 状态判断
 *   6. placement / variant / avatar 样式映射
 */
import { ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { UIMessage } from 'ai'
import { useBubbleList } from './useBubbleList'
import type { MessageUsageInfo } from './useAiChat'

/** 构造纯文本 UIMessage */
function makeTextMessage(
  id: string,
  role: 'user' | 'assistant' | 'system',
  text: string
): UIMessage {
  return { id, role, parts: [{ type: 'text', text }] } as UIMessage
}

describe('useBubbleList', () => {
  let clearTimestamps: () => void

  beforeEach(() => {
    // 每次测试前清空模块级 messageTimestamps Map
    const tmp = useBubbleList(ref([]))
    clearTimestamps = tmp.clearTimestamps
    clearTimestamps()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('时间戳记录', () => {
    it('消息首次出现时记录 createdAt', () => {
      vi.spyOn(Date, 'now').mockReturnValue(1000)
      const messages = ref<UIMessage[]>([makeTextMessage('m1', 'user', 'Hi')])
      const { bubbleItems } = useBubbleList(messages)

      expect(bubbleItems.value[0].createdAt).toBe(1000)
    })

    it('createdAt 在 computed 重算后保持不变（has 检查生效）', () => {
      const dateNow = vi.spyOn(Date, 'now')
      dateNow.mockReturnValue(1000)
      const messages = ref<UIMessage[]>([makeTextMessage('m1', 'user', 'Hi')])
      const { bubbleItems } = useBubbleList(messages)

      expect(bubbleItems.value[0].createdAt).toBe(1000)

      dateNow.mockReturnValue(5000)
      // 替换数组触发 computed 重算（消息 id 不变）
      messages.value = [...messages.value]
      // messageTimestamps.has('m1')=true，不会重新记录
      expect(bubbleItems.value[0].createdAt).toBe(1000)
    })
  })

  describe('clearTimestamps', () => {
    it('清空后再次计算时重新记录时间戳', () => {
      const dateNow = vi.spyOn(Date, 'now')
      dateNow.mockReturnValue(1000)
      const messages = ref<UIMessage[]>([makeTextMessage('m1', 'user', 'Hi')])
      const { bubbleItems, clearTimestamps } = useBubbleList(messages)

      expect(bubbleItems.value[0].createdAt).toBe(1000)

      dateNow.mockReturnValue(2000)
      clearTimestamps()
      // 替换 messages 触发 computed 重算
      messages.value = [makeTextMessage('m1', 'user', 'Hi')]

      expect(bubbleItems.value[0].createdAt).toBe(2000)
    })
  })

  describe('usage 注入', () => {
    it('assistant 消息注入 usageMap 对应的 usage', () => {
      const usage: MessageUsageInfo = { totalTokens: 42, duration: 1.5 }
      const usageMap = ref<Record<string, MessageUsageInfo>>({ a1: usage })
      const messages = ref<UIMessage[]>([makeTextMessage('a1', 'assistant', 'response')])
      const { bubbleItems } = useBubbleList(messages, usageMap)

      expect(bubbleItems.value[0].usage).toEqual(usage)
    })

    it('user 消息不注入 usage', () => {
      const usageMap = ref<Record<string, MessageUsageInfo>>({
        u1: { totalTokens: 99 }
      })
      const messages = ref<UIMessage[]>([makeTextMessage('u1', 'user', 'question')])
      const { bubbleItems } = useBubbleList(messages, usageMap)

      expect(bubbleItems.value[0].usage).toBeUndefined()
    })

    it('usageMap 无对应 entry 时 usage 为 undefined', () => {
      const usageMap = ref<Record<string, MessageUsageInfo>>({})
      const messages = ref<UIMessage[]>([makeTextMessage('a1', 'assistant', 'response')])
      const { bubbleItems } = useBubbleList(messages, usageMap)

      expect(bubbleItems.value[0].usage).toBeUndefined()
    })
  })

  describe('内容提取', () => {
    it('拼接多个 text parts', () => {
      const messages = ref<UIMessage[]>([
        {
          id: 'm1',
          role: 'assistant',
          parts: [
            { type: 'text', text: 'Hello' },
            { type: 'text', text: ' world' }
          ]
        } as UIMessage
      ])
      const { bubbleItems } = useBubbleList(messages)

      expect(bubbleItems.value[0].textContent).toBe('Hello world')
    })

    it('提取 reasoning parts 内容', () => {
      const messages = ref<UIMessage[]>([
        {
          id: 'm1',
          role: 'assistant',
          parts: [{ type: 'reasoning', text: '思考过程', state: 'done' }]
        } as UIMessage
      ])
      const { bubbleItems } = useBubbleList(messages)

      expect(bubbleItems.value[0].reasoningContent).toBe('思考过程')
    })
  })

  describe('状态判断', () => {
    it('assistant 无 text 无 reasoning 时 isLoading=true', () => {
      const messages = ref<UIMessage[]>([
        { id: 'm1', role: 'assistant', parts: [] } as UIMessage
      ])
      const { bubbleItems } = useBubbleList(messages)

      expect(bubbleItems.value[0].isLoading).toBe(true)
    })

    it('assistant 有 text 时 isLoading=false', () => {
      const messages = ref<UIMessage[]>([
        makeTextMessage('m1', 'assistant', 'response')
      ])
      const { bubbleItems } = useBubbleList(messages)

      expect(bubbleItems.value[0].isLoading).toBe(false)
    })

    it('最后一个 reasoning part state=streaming 时 isReasoningStreaming=true', () => {
      const messages = ref<UIMessage[]>([
        {
          id: 'm1',
          role: 'assistant',
          parts: [{ type: 'reasoning', text: '思考', state: 'streaming' }]
        } as UIMessage
      ])
      const { bubbleItems } = useBubbleList(messages)

      expect(bubbleItems.value[0].isReasoningStreaming).toBe(true)
    })

    it('reasoning part state=done 时 isReasoningStreaming=false', () => {
      const messages = ref<UIMessage[]>([
        {
          id: 'm1',
          role: 'assistant',
          parts: [{ type: 'reasoning', text: '思考', state: 'done' }]
        } as UIMessage
      ])
      const { bubbleItems } = useBubbleList(messages)

      expect(bubbleItems.value[0].isReasoningStreaming).toBe(false)
    })
  })

  describe('样式映射', () => {
    it('user 消息 placement=end variant=outlined 有 user avatar', () => {
      const messages = ref<UIMessage[]>([makeTextMessage('u1', 'user', 'Q')])
      const { bubbleItems } = useBubbleList(messages)
      const item = bubbleItems.value[0]

      expect(item.placement).toBe('end')
      expect(item.variant).toBe('outlined')
      expect(item.avatar).toBeDefined()
      expect(item.avatar).toContain('data:image/svg+xml')
    })

    it('assistant 消息 placement=start variant=filled 有 avatar', () => {
      const messages = ref<UIMessage[]>([makeTextMessage('a1', 'assistant', 'A')])
      const { bubbleItems } = useBubbleList(messages)
      const item = bubbleItems.value[0]

      expect(item.placement).toBe('start')
      expect(item.variant).toBe('filled')
      expect(item.avatar).toBeDefined()
    })
  })

  describe('rawMessage 保留', () => {
    it('item.rawMessage 保留原始 UIMessage 数据', () => {
      const msg = makeTextMessage('m1', 'user', 'Hello')
      const messages = ref<UIMessage[]>([msg])
      const { bubbleItems } = useBubbleList(messages)

      expect(bubbleItems.value[0].rawMessage.id).toBe('m1')
      expect(bubbleItems.value[0].rawMessage.role).toBe('user')
    })
  })
})
