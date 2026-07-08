/**
 * AiChatPanel 组件单元测试。
 * 覆盖点：
 *   1. 空态 Welcome 渲染 + 模型加载错误态
 *   2. formatUsage 渲染（tokens · 耗时）
 *   3. streamingItemIndex 流式光标
 *   4. 操作按钮 emit：edit / delete / regenerate / retry
 *   5. assistant 删除二次确认（ElMessageBox.confirm）
 *
 * Mock 策略：mock vue-element-plus-x（BubbleList slot 透传 + XSender/XSender stub）
 *           + markstream-vue（MarkdownRender stub）+ element-plus（ElMessage/ElMessageBox），
 *           不 mock AiChatPanel 本身逻辑。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import i18n from '@/plugins/i18n'
import type { UIMessage } from 'ai'
import type { PlaygroundBubbleItem } from '@/composables/playground/useBubbleList'
import type { MessageUsageInfo } from '@/composables/playground/useAiChat'

// Mock vue-element-plus-x：BubbleList 支持 #content/#loading/#footer slot 透传
vi.mock('vue-element-plus-x', async () => {
  const { defineComponent, h } = await import('vue')
  return {
    BubbleList: defineComponent({
      name: 'BubbleList',
      props: ['list', 'maxHeight', 'autoScroll', 'showBackButton'],
      setup(props, { slots }) {
        return () =>
          h(
            'div',
            { class: 'bubble-list-stub' },
            (props.list || []).map((item: any) => [
              slots.content?.({ item }),
              slots.loading?.({ item }),
              slots.footer?.({ item })
            ])
          )
      }
    }),
    XSender: defineComponent({
      name: 'XSender',
      props: ['placeholder', 'loading', 'disabled', 'submitType'],
      emits: ['submit', 'cancel', 'change', 'paste-file'],
      setup() {
        return { getModelValue: () => ({ text: '' }), clear: () => {}, setText: () => {} }
      },
      render() {
        return h('div', { class: 'xsender-stub' })
      }
    }),
    Welcome: defineComponent({
      name: 'Welcome',
      props: ['variant', 'title', 'description'],
      render() {
        return h('div', { class: 'welcome-stub' })
      }
    }),
    Thinking: defineComponent({
      name: 'Thinking',
      props: ['content', 'status', 'autoCollapse'],
      render() {
        return h('div', { class: 'thinking-stub' })
      }
    })
  }
})

// Mock markstream-vue
vi.mock('markstream-vue', () => ({
  MarkdownRender: {
    name: 'MarkdownRender',
    props: ['content', 'final', 'mode', 'codeBlockProps'],
    render() {
      return null
    }
  }
}))

// Mock element-plus（保留组件，替换 ElMessage / ElMessageBox）
const elMessageMock = {
  success: vi.fn(),
  error: vi.fn(),
  warning: vi.fn(),
  info: vi.fn()
}
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return {
    ...actual,
    ElMessage: elMessageMock,
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn() }
  }
})

/** 构造测试用 BubbleItem */
function makeItem(
  overrides: Partial<PlaygroundBubbleItem> = {}
): PlaygroundBubbleItem {
  return {
    key: 'item-1',
    role: 'user',
    placement: 'end',
    textContent: 'test text',
    reasoningContent: '',
    isReasoningStreaming: false,
    isLoading: false,
    rawMessage: { id: 'msg-1', role: 'user', parts: [] } as UIMessage,
    ...overrides
  }
}

async function mountPanel(props: Record<string, unknown>) {
  const AiChatPanel = (await import('@/components/playground/AiChatPanel.vue')).default
  return mount(AiChatPanel, {
    props: {
      items: [],
      isGenerating: false,
      isInputDisabled: false,
      ...props
    },
    global: {
      plugins: [i18n]
    }
  })
}

describe('AiChatPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // Mock clipboard（handleCopy 使用）
    Object.assign(navigator, {
      clipboard: { writeText: vi.fn().mockResolvedValue(undefined) }
    })
  })

  afterEach(() => {
    vi.resetModules()
  })

  describe('空态与错误态', () => {
    it('items 为空时显示 Welcome', async () => {
      const wrapper = await mountPanel({ items: [] })
      // P2 升级后 Welcome 为自定义能力引导卡片（.ai-chat-panel__welcome 容器 + __welcome-hero）
      expect(wrapper.find('.ai-chat-panel__welcome').exists()).toBe(true)
      expect(wrapper.find('.ai-chat-panel__welcome-hero').exists()).toBe(true)
    })

    it('modelLoadError=true 时显示错误态 + 刷新重试按钮', async () => {
      const wrapper = await mountPanel({ items: [], modelLoadError: true })
      expect(wrapper.find('.welcome-stub').exists()).toBe(false)
      expect(wrapper.text()).toContain('模型列表加载失败')
      expect(wrapper.find('[title="刷新重试"]').exists() || wrapper.find('button').exists()).toBe(true)
    })
  })

  describe('formatUsage 渲染', () => {
    it('assistant 消息 usage 显示 tokens + 耗时', async () => {
      const usage: MessageUsageInfo = { totalTokens: 42, duration: 1.5 }
      const items = [
        makeItem({
          key: 'a1',
          role: 'assistant',
          textContent: 'response',
          usage,
          rawMessage: { id: 'a1', role: 'assistant', parts: [] } as UIMessage
        })
      ]
      const wrapper = await mountPanel({ items, isGenerating: false })
      const usageEl = wrapper.find('.ai-chat-panel__usage')
      expect(usageEl.exists()).toBe(true)
      expect(usageEl.text()).toContain('42 tokens')
      expect(usageEl.text()).toContain('1.5s')
    })

    it('isGenerating=true 时不显示 usage', async () => {
      const items = [
        makeItem({
          key: 'a1',
          role: 'assistant',
          textContent: 'response',
          usage: { totalTokens: 42 },
          rawMessage: { id: 'a1', role: 'assistant', parts: [] } as UIMessage
        })
      ]
      const wrapper = await mountPanel({ items, isGenerating: true })
      expect(wrapper.find('.ai-chat-panel__usage').exists()).toBe(false)
    })
  })

  describe('streamingItemIndex 流式光标', () => {
    it('isGenerating + 最后一条 assistant 时显示光标', async () => {
      const items = [
        makeItem({
          key: 'a1',
          role: 'assistant',
          textContent: 'streaming response',
          rawMessage: { id: 'a1', role: 'assistant', parts: [] } as UIMessage
        })
      ]
      const wrapper = await mountPanel({ items, isGenerating: true })
      expect(wrapper.find('.ai-chat-panel__cursor').exists()).toBe(true)
    })

    it('非流式时不显示光标', async () => {
      const items = [
        makeItem({
          key: 'a1',
          role: 'assistant',
          textContent: 'response',
          rawMessage: { id: 'a1', role: 'assistant', parts: [] } as UIMessage
        })
      ]
      const wrapper = await mountPanel({ items, isGenerating: false })
      expect(wrapper.find('.ai-chat-panel__cursor').exists()).toBe(false)
    })
  })

  describe('操作按钮 emit', () => {
    it('user 消息点击编辑 → emit edit', async () => {
      const items = [makeItem({ key: 'u1', role: 'user' })]
      const wrapper = await mountPanel({ items })
      await wrapper.find('[title="编辑"]').trigger('click')
      expect(wrapper.emitted('edit')).toBeTruthy()
      expect(wrapper.emitted('edit')?.[0]).toEqual(['msg-1'])
    })

    it('user 消息点击删除 → 直接 emit delete（无确认）', async () => {
      const items = [makeItem({ key: 'u1', role: 'user' })]
      const wrapper = await mountPanel({ items })
      await wrapper.find('[title="删除"]').trigger('click')
      expect(wrapper.emitted('delete')).toBeTruthy()
      expect(wrapper.emitted('delete')?.[0]).toEqual(['msg-1'])
    })

    it('assistant 消息点击删除 → ElMessageBox.confirm 调用', async () => {
      const { ElMessageBox } = await import('element-plus')
      ;(ElMessageBox.confirm as ReturnType<typeof vi.fn>).mockResolvedValue('confirm')
      const items = [
        makeItem({
          key: 'a1',
          role: 'assistant',
          textContent: 'response',
          rawMessage: { id: 'a-msg', role: 'assistant', parts: [] } as UIMessage
        })
      ]
      const wrapper = await mountPanel({ items })
      await wrapper.find('[title="删除"]').trigger('click')
      await Promise.resolve() // 等待 confirm promise
      expect(ElMessageBox.confirm).toHaveBeenCalledOnce()
    })

    it('assistant 消息点击删除确认后 → emit delete', async () => {
      const { ElMessageBox } = await import('element-plus')
      ;(ElMessageBox.confirm as ReturnType<typeof vi.fn>).mockResolvedValue('confirm')
      const items = [
        makeItem({
          key: 'a1',
          role: 'assistant',
          textContent: 'response',
          rawMessage: { id: 'a-msg', role: 'assistant', parts: [] } as UIMessage
        })
      ]
      const wrapper = await mountPanel({ items })
      await wrapper.find('[title="删除"]').trigger('click')
      await Promise.resolve()
      expect(wrapper.emitted('delete')).toBeTruthy()
      expect(wrapper.emitted('delete')?.[0]).toEqual(['a-msg'])
    })

    it('assistant 消息点击重新生成 → emit regenerate', async () => {
      const items = [
        makeItem({
          key: 'a1',
          role: 'assistant',
          textContent: 'response',
          rawMessage: { id: 'a-msg', role: 'assistant', parts: [] } as UIMessage
        })
      ]
      const wrapper = await mountPanel({ items })
      await wrapper.find('[title="重新生成"]').trigger('click')
      expect(wrapper.emitted('regenerate')).toBeTruthy()
      expect(wrapper.emitted('regenerate')?.[0]).toEqual(['a-msg'])
    })

    it('错误条重试按钮 → emit retry', async () => {
      const wrapper = await mountPanel({
        items: [makeItem()],
        errorMessage: '请求失败',
        isGenerating: false
      })
      await wrapper.find('.ai-chat-panel__error-retry').trigger('click')
      expect(wrapper.emitted('retry')).toBeTruthy()
    })
  })

  describe('复制', () => {
    it('点击复制 → clipboard.writeText + ElMessage.success', async () => {
      const items = [makeItem({ key: 'u1', role: 'user', textContent: 'copy me' })]
      const wrapper = await mountPanel({ items })
      await wrapper.find('[title="复制"]').trigger('click')
      await Promise.resolve()
      expect(navigator.clipboard.writeText).toHaveBeenCalledWith('copy me')
      expect(elMessageMock.success).toHaveBeenCalled()
    })
  })
})
