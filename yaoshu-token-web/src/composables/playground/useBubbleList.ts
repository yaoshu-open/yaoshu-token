/**
 * UIMessage → BubbleListItem 转换 composable。
 *
 * 将 @ai-sdk/vue Chat 的 UIMessage[] 转换为 vue-element-plus-x BubbleList 所需的格式，
 * 并提取 reasoning / text parts 供 Thinking 组件和 MarkdownRender 消费。
 */
import { computed } from 'vue'
import type { Ref } from 'vue'
import type { UIMessage } from 'ai'
import type { MessageUsageInfo } from './useAiChat'
// PG-E10: Assistant 头像本地化（替代远程 CDN URL）
import logoUrl from '@/assets/logo/logo.webp'

// user 消息头像（中性色 SVG data URI，开源/商业通用）
const userAvatarUrl = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='32' height='32' viewBox='0 0 32 32'%3E%3Ccircle cx='16' cy='16' r='16' fill='%2364748b'/%3E%3Ccircle cx='16' cy='13' r='5' fill='white'/%3E%3Cpath d='M6 28c0-5.5 4.5-10 10-10s10 4.5 10 10' fill='white'/%3E%3C/svg%3E"

/** BubbleList item 属性（与 vue-element-plus-x BubbleProps 对齐） */
interface BubbleListItemProps {
  placement?: 'start' | 'end'
  avatar?: string
  content?: string
  loading?: boolean
  shape?: 'round' | 'corner'
  variant?: 'filled' | 'borderless' | 'outlined' | 'shadow'
  maxWidth?: string
  avatarSize?: string
  avatarGap?: string
}

/** Playground 消息引用来源（S7: Sources 引用展示） */
export interface PlaygroundSource {
  title: string
  url: string
  snippet?: string
}

export interface PlaygroundBubbleItem extends BubbleListItemProps {
  key: string
  role: 'user' | 'assistant' | 'system'
  /** 纯文本内容（供 MarkdownRender 流式渲染） */
  textContent: string
  /** reasoning 内容（供 Thinking 组件） */
  reasoningContent: string
  /** reasoning 是否仍在流式 */
  isReasoningStreaming: boolean
  /** 是否加载中 */
  isLoading: boolean
  /** 原始 UIMessage（供操作按钮消费） */
  rawMessage: UIMessage
  /** 消息创建时间戳（运行时记录，近似值） */
  createdAt?: number
  /** token 用量与耗时（仅 assistant 消息） */
  usage?: MessageUsageInfo
  /** S7: 消息版本历史（编辑后保留的原版本快照） */
  versions?: PlaygroundBubbleItem[]
  /** S7: 当前版本索引（0-based，最新版本为 length-1） */
  currentVersion?: number
  /** S7: 引用来源（搜索/工具调用结果） */
  sources?: PlaygroundSource[]
}

// UIMessage 无 createdAt 字段，运行时首次见到消息 id 时记录近似时间戳
const messageTimestamps = new Map<string, number>()

export function useBubbleList(
  messages: Ref<UIMessage[]>,
  usageMap?: Ref<Record<string, MessageUsageInfo>>,
  /** S7: 消息版本历史（key=消息ID，value=历史版本快照数组） */
  versionMap?: Ref<Record<string, PlaygroundBubbleItem[]>>,
  /** S7: 当前版本索引（key=消息ID，value=当前查看的版本索引） */
  currentVersionMap?: Ref<Record<string, number>>
) {
  const bubbleItems = computed<PlaygroundBubbleItem[]>(() => {
    return messages.value.map((msg) => {
      // 记录首次见到该消息的时间戳
      if (!messageTimestamps.has(msg.id)) {
        messageTimestamps.set(msg.id, Date.now())
      }
      const createdAt = messageTimestamps.get(msg.id)

      const textParts = msg.parts.flatMap((p) =>
        p.type === 'text' ? [p] : []
      )
      const reasoningParts = msg.parts.flatMap((p) =>
        p.type === 'reasoning' ? [p] : []
      )

      let textContent = textParts.map((p) => p.text).join('')
      let reasoningContent = reasoningParts
        .map((p) => (p.type === 'reasoning' ? p.text : ''))
        .join('')

      // 判断 reasoning 是否仍在流式（最后一个 part 状态为 streaming）
      const lastReasoningPart = reasoningParts[reasoningParts.length - 1]
      const isReasoningStreaming =
        lastReasoningPart?.type === 'reasoning' &&
        lastReasoningPart.state === 'streaming'

      // 判断是否加载中（assistant 无 text 且无 reasoning）
      const isLoading =
        msg.role === 'assistant' &&
        !textContent &&
        !reasoningContent &&
        textParts.length === 0

      const isUser = msg.role === 'user'

      // S7: 附加版本历史
      const versions = versionMap?.value[msg.id]
      const currentVersionIdx = versions && versions.length > 0
        ? (currentVersionMap?.value[msg.id] ?? versions.length - 1)
        : undefined

      // S7: 版本切换时显示历史版本的内容（rawMessage 保持当前消息）
      if (versions && currentVersionIdx !== undefined && currentVersionIdx < versions.length - 1) {
        const historical = versions[currentVersionIdx]
        if (historical) {
          textContent = historical.textContent
          reasoningContent = historical.reasoningContent
        }
      }

      return {
        key: msg.id,
        role: msg.role,
        placement: isUser ? 'end' : 'start',
        content: textContent,
        textContent,
        reasoningContent,
        isReasoningStreaming,
        isLoading,
        variant: isUser ? 'outlined' : 'filled',
        shape: 'corner',
        // user 消息限制宽度避免短文本气泡过大；assistant/system 消息充分利用宽度
        maxWidth: isUser ? '70%' : '85%',
        avatar: msg.role === 'assistant' ? logoUrl : (msg.role === 'user' ? userAvatarUrl : undefined),
        avatarSize: '32px',
        avatarGap: '12px',
        rawMessage: msg,
        createdAt,
        usage: msg.role === 'assistant' ? usageMap?.value?.[msg.id] : undefined,
        versions,
        currentVersion: currentVersionIdx,
        sources: undefined
      }
    })
  })

  /** 清空时间戳缓存（清空对话时调用） */
  function clearTimestamps(): void {
    messageTimestamps.clear()
  }

  return { bubbleItems, clearTimestamps }
}
