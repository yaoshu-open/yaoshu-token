/**
 * AI Chat composable — 基于 @ai-sdk/vue Chat 类的 Playground 对话编排。
 *
 * 替代原 useChatHandler + useStreamRequest 的自研组合，
 * 使用 @ai-sdk/vue 官方 Chat 类管理消息状态 + 自定义 OpenAIChatTransport 桥接后端。
 *
 * 保留的现有能力：
 * - usePlaygroundState（config / models / groups）
 * - useCustomRequest（自定义请求体覆盖）
 * - useDebugPanel（SSE 事件追踪 + payload 预览）
 * - 401 集成（authStore + router）
 *
 * 新增能力：
 * - UIMessage 标准格式（parts 数组，支持 text/reasoning/tool/source）
 * - chat.regenerate() 官方重发生
 * - chat.stop() 官方中断
 * - 模块级单例（effectScope detached）：组件卸载不销毁，流式中离开页面 SSE 不中断、消息持续持久化
 * - localStorage 按用户隔离（key 挂载 userId）
 */
import { Chat } from '@ai-sdk/vue'
import type { UIMessage } from 'ai'
import { computed, effectScope, ref, watch } from 'vue'
import type { EffectScope } from 'vue'
import { useRouter } from 'vue-router'
import { usePlaygroundState } from './usePlaygroundState'
import { useCustomRequest } from './useCustomRequest'
import { useDebugPanel } from './useDebugPanel'
import {
  OpenAIChatTransport,
  type PlaygroundChatBody
} from './openai-chat-transport'
import { useAuthStore, registerLogoutCallback } from '@/store/modules/auth'
import { STORAGE_KEYS } from '@/views/playground/constants'
import { clearPlaygroundData } from '@/views/playground/lib/storage'
import type { ChatCompletionRequest } from '@/api/playground/types'

/** 单条消息的 token 用量与耗时 */
export interface MessageUsageInfo {
  promptTokens?: number
  completionTokens?: number
  totalTokens?: number
  /** 耗时（秒） */
  duration?: number
}

/** localStorage key 生成（按用户隔离） */
function messagesKey(userId: string): string {
  return `${STORAGE_KEYS.MESSAGES}:${userId}`
}

function usageKey(userId: string): string {
  return `${STORAGE_KEYS.MESSAGES}:${userId}__usage`
}

function loadStoredMessages(userId: string): UIMessage[] {
  try {
    const stored = localStorage.getItem(messagesKey(userId))
    if (!stored) return []
    const messages = JSON.parse(stored) as UIMessage[]
    // 防御：流式中途刷新会导致 part.state停留在 'streaming'，加载后强制标记为已完成
    // 历史消息不可能还在流式，将所有 streaming/draft state 的 part 修正为 done
    for (const msg of messages) {
      if (Array.isArray(msg.parts)) {
        for (const part of msg.parts) {
          if (part && typeof part === 'object' && 'state' in part) {
            const p = part as { state?: string }
            if (p.state === 'streaming' || p.state === 'draft') {
              p.state = 'done'
            }
          }
        }
      }
    }
    return messages
  } catch {
    return []
  }
}

function saveStoredMessages(userId: string, messages: UIMessage[]): void {
  try {
    localStorage.setItem(messagesKey(userId), JSON.stringify(messages))
  } catch {
    // localStorage 写入失败：静默
  }
}

function loadStoredUsage(userId: string): Record<string, MessageUsageInfo> {
  try {
    const stored = localStorage.getItem(usageKey(userId))
    return stored ? (JSON.parse(stored) as Record<string, MessageUsageInfo>) : {}
  } catch {
    return {}
  }
}

function saveStoredUsage(userId: string, usage: Record<string, MessageUsageInfo>): void {
  try {
    localStorage.setItem(usageKey(userId), JSON.stringify(usage))
  } catch {
    // localStorage 写入失败：静默
  }
}

export interface UseAiChatOptions {
  imageUrls?: () => string[]
  imageEnabled?: () => boolean
}

// ============================================================================
// 模块级单例：Chat 实例生命周期独立于路由组件
// effectScope(true) = detached scope，组件卸载不停止其内部 watch/effect
// ============================================================================
let chatScope: EffectScope | null = null
let chatInstance: ReturnType<typeof createChatInstance> | null = null

// 可更新的 options 引用（组件每次挂载调用 useAiChat 时刷新，避免单例持有旧组件闭包）
let currentImageUrls: (() => string[]) | null = null
let currentImageEnabled: (() => boolean) | null = null

/** 创建 Chat 实例（在 detached effectScope 内执行） */
function createChatInstance(userId: string) {
  const router = useRouter()
  const authStore = useAuthStore()

  function onUnauthorized(): void {
    authStore.clearAuthToken()
    const redirect = encodeURIComponent(router.currentRoute.value.fullPath)
    router.push(`/sign-in?redirect=${redirect}`)
  }

  const state = usePlaygroundState(userId)
  const custom = useCustomRequest()
  const debug = useDebugPanel()

  // PG-E05: 消息级 usage 缓存（key = assistant messageId），持久化到 localStorage 避免刷新/重进丢失
  const usageMap = ref<Record<string, MessageUsageInfo>>(loadStoredUsage(userId))
  // 临时捕获当前请求的 usage（流式最后一个 chunk 携带）
  let pendingUsage: MessageUsageInfo | null = null
  let requestStartTime = 0

  const transport = new OpenAIChatTransport({
    onUnauthorized,
    onSseEvent: (raw, parsed) => {
      debug.appendSseEvent({
        raw,
        delta: parsed?.choices?.[0]?.delta
      })
    },
    onUsage: (usage) => {
      // 防御：后端可能在流末尾追加非标准汇总 chunk（驼峰命名），不覆盖已有有效值
      const incoming = {
        promptTokens: usage.prompt_tokens,
        completionTokens: usage.completion_tokens,
        totalTokens: usage.total_tokens
      }
      // 仅当新值包含有效 token 数时才覆盖（防止汇总 chunk 的 undefined 覆盖上游标准值）
      if (incoming.totalTokens !== undefined || pendingUsage === null) {
        pendingUsage = incoming
      }
    }
  })

  const chat = new Chat<UIMessage>({
    transport,
    messages: loadStoredMessages(userId),
    onError: (error) => {
      debug.appendSseEvent({ raw: error.message, isError: true })
    }
  })

  // 响应式状态（Chat 类内部使用 VueChatState，getter 返回 ref.value）
  const messages = computed(() => chat.messages)
  const status = computed(() => chat.status)
  const error = computed(() => chat.error)
  const isGenerating = computed(
    () => status.value === 'submitted' || status.value === 'streaming'
  )

  /** 构建 sendMessage 的 body 参数（Playground 配置透传给 transport） */
  function buildBody(): PlaygroundChatBody {
    const config = state.config.value
    const enabled = state.parameterEnabled.value
    return {
      model: config.model,
      stream: config.stream,
      temperature: enabled.temperature ? config.temperature : undefined,
      top_p: enabled.top_p ? config.top_p : undefined,
      max_tokens: enabled.max_tokens ? config.max_tokens : undefined,
      frequency_penalty: enabled.frequency_penalty ? config.frequency_penalty : undefined,
      presence_penalty: enabled.presence_penalty ? config.presence_penalty : undefined,
      seed: enabled.seed ? config.seed : undefined,
      customRequestBody:
        custom.customRequestMode.value && custom.isValidJson.value
          ? custom.customRequestBody.value
          : undefined,
      // 读模块级变量（最新组件实例的闭包），而非创建时的固定闭包
      imageUrls: currentImageEnabled?.() === false ? [] : currentImageUrls?.() ?? [],
      systemPrompt: config.systemPrompt
    }
  }

  /** 构建 debug 面板预览 payload */
  function buildPreviewPayload(): ChatCompletionRequest {
    const config = state.config.value
    const body = buildBody()

    const payload: ChatCompletionRequest = {
      model: body.model ?? config.model,
      messages: messages.value.map((m) => ({
        role: m.role,
        content: m.parts
          .flatMap((p) => (p.type === 'text' ? [p.text] : []))
          .join('')
      })),
      stream: config.stream
    }

    if (body.temperature !== undefined) payload.temperature = body.temperature
    if (body.top_p !== undefined) payload.top_p = body.top_p
    if (body.max_tokens !== undefined) payload.max_tokens = body.max_tokens
    if (body.frequency_penalty !== undefined) payload.frequency_penalty = body.frequency_penalty
    if (body.presence_penalty !== undefined) payload.presence_penalty = body.presence_penalty
    if (body.seed !== undefined && body.seed !== null) payload.seed = body.seed

    return payload
  }

  /** 发送用户消息 */
  async function sendMessage(text: string): Promise<void> {
    const trimmed = text.trim()
    if (!trimmed) return
    if (isGenerating.value) return
    if (!custom.canSend.value) return

    debug.clearSseEvents()
    debug.setPreviewPayload(buildPreviewPayload())
    debug.setActualRequest(buildPreviewPayload())

    pendingUsage = null
    requestStartTime = Date.now()
    await chat.sendMessage({ text }, { body: buildBody() })
    recordUsageForLastAssistant()
  }

  /** 停止生成 */
  async function stopGeneration(): Promise<void> {
    await chat.stop()
  }

  /** 重新生成（指定消息 ID 或最后一条 assistant 消息） */
  async function regenerate(messageId?: string): Promise<void> {
    if (isGenerating.value) return
    debug.clearSseEvents()
    debug.setPreviewPayload(buildPreviewPayload())
    pendingUsage = null
    requestStartTime = Date.now()
    // Bug-FE-13: 必须传 body，否则 transport 拿不到 model/参数（与 sendMessage 路径一致）
    // Chat.regenerate 签名为单参数合并对象 {messageId?, ...ChatRequestOptions}
    await chat.regenerate({
      ...(messageId ? { messageId } : {}),
      body: buildBody()
    })
    recordUsageForLastAssistant()
  }

  /** 将本次请求的 usage + 耗时关联到最后一条 assistant 消息 */
  function recordUsageForLastAssistant(): void {
    const duration = requestStartTime
      ? Math.round((Date.now() - requestStartTime) / 100) / 10
      : undefined
    const info: MessageUsageInfo = { ...pendingUsage ?? {}, duration }
    // 找到最后一条 assistant 消息
    for (let i = messages.value.length - 1; i >= 0; i--) {
      if (messages.value[i].role === 'assistant') {
        usageMap.value = {
          ...usageMap.value,
          [messages.value[i].id]: info
        }
        saveStoredUsage(userId, usageMap.value)
        break
      }
    }
    pendingUsage = null
    requestStartTime = 0
  }

  /** 清除错误状态 */
  function clearError(): void {
    chat.clearError()
  }

  /** 清空所有消息 */
  function clearMessages(): void {
    chat.messages = []
    usageMap.value = {}
    try {
      localStorage.removeItem(messagesKey(userId))
      localStorage.removeItem(usageKey(userId))
    } catch {
      // 静默
    }
  }

  /** 插入指定角色的消息（不触发请求，用于 few-shot 构造 / assistant 预填 / 中场 system 指令） */
  function insertMessage(role: 'assistant' | 'system', text: string): void {
    const trimmed = text.trim()
    if (!trimmed) return
    const newMsg: UIMessage = {
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
      role,
      parts: [{ type: 'text', text: trimmed }]
    }
    chat.messages = [...chat.messages, newMsg]
  }

  // 消息持久化（此 watch 属于 detached scope，组件卸载不停止）
  watch(messages, (next) => saveStoredMessages(userId, next), { deep: true })

  return {
    // 标识
    userId,
    // 状态
    state,
    custom,
    debug,
    messages,
    status,
    error,
    isGenerating,
    usageMap,
    // 操作
    sendMessage,
    stopGeneration,
    regenerate,
    clearError,
    clearMessages,
    insertMessage,
    // 透传（UI 组件按需消费）
    chat,
    transport,
    // 工具
    buildBody,
    buildPreviewPayload
  }
}

/**
 * 获取 Chat 单例（首次调用创建，后续返回缓存）。
 * 在 detached effectScope 内创建，组件卸载不销毁——保证流式中离开页面 SSE 不中断、消息持续持久化。
 */
export function useAiChat(options?: UseAiChatOptions) {
  // 更新可变依赖（每次组件挂载时刷新闭包引用，避免单例持有旧组件实例的 ref）
  if (options?.imageUrls) currentImageUrls = options.imageUrls
  if (options?.imageEnabled) currentImageEnabled = options.imageEnabled

  if (!chatInstance) {
    const authStore = useAuthStore()
    const userId = String(authStore.userInfo?.id ?? 'guest')
    chatScope = effectScope(true)
    chatInstance = chatScope.run(() => createChatInstance(userId))!
  }
  return chatInstance
}

/**
 * 销毁 Chat 单例（用户登出时调用）。
 * 停止 detached scope（中断 SSE + 清理 watch），释放模块级引用，下次进入重建加载新用户数据。
 */
export function disposeChat(): void {
  // 销毁前清理当前用户的持久化数据
  if (chatInstance) {
    clearPlaygroundData(chatInstance.userId)
  }
  chatScope?.stop()
  chatScope = null
  chatInstance = null
  currentImageUrls = null
  currentImageEnabled = null
}

// IoC 注册：auth store clearAuthToken 时自动调 disposeChat（Playground 自主注册，auth 不反向依赖 Playground）
registerLogoutCallback(disposeChat)
