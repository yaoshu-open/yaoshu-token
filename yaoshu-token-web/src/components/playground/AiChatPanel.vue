<script setup lang="ts">
/**
 * AiChatPanel — 基于 vue-element-plus-x 的 AI 对话面板。
 *
 * 装配 BubbleList（消息列表）+ 自定义气泡内容（Thinking + MarkdownRender）+ XSender（输入）。
 * 替代原 PlaygroundChat + PlaygroundInput 的自研组合。
 *
 * 数据流由 useAiChat composable 驱动（@ai-sdk/vue Chat + OpenAIChatTransport）。
 */
import { ref, computed, onMounted, onUnmounted, nextTick, toRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  BubbleList,
  XSender,
  Thinking
} from 'vue-element-plus-x'
import { MarkdownRender } from 'markstream-vue'
import dayjs from 'dayjs'
import { setThinkingExpanded, type PlaygroundBubbleItem } from '@/composables/playground/useBubbleList'
import { createSuggestionList } from '@/views/playground/constants'
import RoleSelector from './RoleSelector.vue'
import { useMessageWindow } from '@/composables/playground/useMessageWindow'

interface Props {
  items: PlaygroundBubbleItem[]
  isGenerating: boolean
  isInputDisabled: boolean
  hasError?: boolean
  errorMessage?: string
  imageUrls?: string[]
  modelLoadError?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  hasError: false,
  imageUrls: () => [],
  modelLoadError: false
})

const emit = defineEmits<{
  submit: [text: string]
  stop: []
  copy: [id: string]
  delete: [id: string]
  regenerate: [id: string]
  edit: [id: string]
  retry: []
  'retry-load': []
  'paste-image': [dataUrl: string]
  'switch-version': [payload: { messageId: string; version: number }]
  /** 插入指定角色的消息（不触发请求，用于 few-shot 构造） */
  insert: [payload: { role: 'assistant' | 'system'; text: string }]
  /** 唤起快捷键帮助弹窗 */
  'show-shortcuts': []
}>()

const { t } = useI18n()
const suggestionList = createSuggestionList(t)

// P2: Welcome 能力引导卡片
interface CapabilityCard {
  key: string
  icon: string
  titleKey: string
  descKey: string
  promptKey: string
}

const capabilityCards: CapabilityCard[] = [
  { key: 'debug', icon: 'i-ep-monitor', titleKey: 'playground.welcomeCapabilities.debug', descKey: 'playground.welcomeCapabilities.debugDesc', promptKey: 'playground.welcomeCapabilities.debugPrompt' },
  { key: 'systemPrompt', icon: 'i-ep-chat-dot-round', titleKey: 'playground.welcomeCapabilities.systemPrompt', descKey: 'playground.welcomeCapabilities.systemPromptDesc', promptKey: 'playground.welcomeCapabilities.systemPromptPrompt' },
  { key: 'roleSwitch', icon: 'i-ep-user', titleKey: 'playground.welcomeCapabilities.roleSwitch', descKey: 'playground.welcomeCapabilities.roleSwitchDesc', promptKey: 'playground.welcomeCapabilities.roleSwitchPrompt' },
  { key: 'comparison', icon: 'i-ep-data-analysis', titleKey: 'playground.welcomeCapabilities.comparison', descKey: 'playground.welcomeCapabilities.comparisonDesc', promptKey: 'playground.welcomeCapabilities.comparisonPrompt' }
]

function handleCapabilityClick(cap: CapabilityCard): void {
  senderRef.value?.setText?.(t(cap.promptKey))
  if (cap.key === 'roleSwitch') {
    selectedRole.value = 'assistant'
  }
}

// 角色切换：user 触发发送请求，assistant/system 仅插入消息（few-shot 构造）
const selectedRole = ref<'user' | 'assistant' | 'system'>('user')

// XSender 是 contenteditable 富文本组件，自身管理文本状态，无 v-model 协议。
// 真实输入文本通过模板引用调用 getModelValue().text 读取。
const senderRef = ref<InstanceType<typeof XSender> | null>(null)

// PG-E15: 字数统计（监听 XSender change 事件）
const charCount = ref(0)
function handleSenderChange(): void {
  const model = senderRef.value?.getModelValue?.()
  charCount.value = (model?.text ?? '').length
}

const isEmpty = computed(() => props.items.length === 0)

// 消息渲染窗口化分批：底层数据全量，仅渲染最近 N 条，滚动到顶追加更早 M 条
const messageWindow = useMessageWindow(toRef(props, 'items'))
const visibleItems = messageWindow.visibleItems

// 清空对话时重置窗口
watch(
  () => props.items.length,
  (len) => {
    if (len === 0) messageWindow.reset()
  }
)

// 分批加载：监听 BubbleList 内部滚动容器，到顶触发 loadMore 并保持滚动锚点
const scrollContainer = ref<HTMLElement | null>(null)

function handleListScroll(): void {
  const el = scrollContainer.value
  if (!el) return
  if (el.scrollTop < 80 && messageWindow.hasMore.value) {
    const prevHeight = el.scrollHeight
    messageWindow.loadMore()
    // 加载后补偿 scrollTop，避免视图跳动
    nextTick(() => {
      if (scrollContainer.value) {
        scrollContainer.value.scrollTop += scrollContainer.value.scrollHeight - prevHeight
      }
    })
  }
}

function handleSubmit(): void {
  if (props.isGenerating) return
  // 从 XSender 暴露的 API 读取真实文本（v-model 不生效）
  const model = senderRef.value?.getModelValue?.()
  const text = (model?.text ?? '').trim()
  if (!text) return
  if (selectedRole.value === 'user') {
    emit('submit', text)
  } else {
    emit('insert', { role: selectedRole.value, text })
  }
  // 清空输入（XSender 暴露 clear()）
  senderRef.value?.clear?.()
}

async function handleCopy(item: PlaygroundBubbleItem): Promise<void> {
  try {
    await navigator.clipboard.writeText(item.textContent)
    ElMessage.success(t('playground.message.copied'))
  } catch {
    ElMessage.error(t('playground.message.copyFailed'))
  }
}

function handleSuggestionClick(text: string): void {
  senderRef.value?.setText?.(text)
}

function handlePasteFile(file: File): void {
  // 只处理图片
  if (!file.type.startsWith('image/')) return
  // 限制大小 5MB
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning(t('playground.message.imageTooLarge'))
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    const dataUrl = reader.result as string
    emit('paste-image', dataUrl)
  }
  reader.readAsDataURL(file)
}

// PG-E03: assistant 消息删除需二次确认，user 消息直接删除
async function handleDeleteClick(item: PlaygroundBubbleItem): Promise<void> {
  if (item.role === 'assistant') {
    try {
      await ElMessageBox.confirm(t('playground.message.deleteConfirm'), t('playground.message.deleteConfirmTitle'), {
        type: 'warning',
        confirmButtonText: t('playground.actions.delete'),
        cancelButtonText: t('common.cancel')
      })
      emit('delete', item.rawMessage.id)
    } catch {
      // 用户取消删除
    }
    return
  }
  emit('delete', item.rawMessage.id)
}

// PG-E02: 暴露给父组件填入编辑文本到 XSender
function fillEditor(text: string): void {
  senderRef.value?.setText?.(text)
}

// S7: 消息版本切换（包装 emit，避免模板中直接调用多参数 emit 的类型问题）
function handleSwitchVersion(item: PlaygroundBubbleItem, delta: number): void {
  const current = item.currentVersion ?? 0
  const next = current + delta
  if (item.versions && next >= 0 && next < item.versions.length) {
    emit('switch-version', { messageId: item.rawMessage.id, version: next })
  }
}

defineExpose({ fillEditor })

// 历史消息追踪：onMounted 时捕获的消息 ID 视为"已完成"，不再触发打字机效果
// 解决问题：强刷页面后 localStorage 恢复的历史消息被 MarkdownRender 重新打字
const historicalMessageIds = new Set<string>()

onMounted(() => {
  for (const item of props.items) {
    historicalMessageIds.add(item.key)
  }
  // 首次进入有历史消息时滚到底部（定位最新一条）
  nextTick(() => {
    scrollToBottom()
    // 绑定 BubbleList 滚动容器，监听到顶加载更早消息
    const el = document.querySelector<HTMLElement>('.ai-chat-panel__list .elx-bubble-list__list')
    if (el) {
      scrollContainer.value = el
      el.addEventListener('scroll', handleListScroll, { passive: true })
    }
  })
})

onUnmounted(() => {
  if (scrollContainer.value) {
    scrollContainer.value.removeEventListener('scroll', handleListScroll)
  }
})

/** 滚动到消息列表底部 */
function scrollToBottom(): void {
  const container = document.querySelector('.ai-chat-panel__list .elx-bubble-list__list')
  if (container) {
    container.scrollTop = container.scrollHeight
  }
}

/** 判断某条消息是否应跳过打字机效果（历史消息 或 非正在生成的消息） */
function isFinalMessage(itemKey: string): boolean {
  // 历史消息：始终跳过打字机
  if (historicalMessageIds.has(itemKey)) return true
  // 新消息：仅在未生成时跳过打字机
  return !props.isGenerating
}

// F09: 流式中断视觉标记
const lastItemAborted = computed(() => {
  if (!props.hasError || props.items.length === 0) return false
  const last = props.items[props.items.length - 1]
  return last?.role === 'assistant' && !props.isGenerating
})

// PG-E05: usage 格式化（tokens · 耗时）
function formatUsage(usage: NonNullable<PlaygroundBubbleItem['usage']>): string {
  const parts: string[] = []
  if (usage.totalTokens !== undefined) parts.push(`${usage.totalTokens} tokens`)
  if (usage.duration !== undefined) parts.push(`${usage.duration}s`)
  return parts.join(' · ')
}

// PG-E11: 流式光标 — 最后一条正在生成的 assistant 消息 key（替代原 streamingItemIndex，适配 BubbleList slot API 无 index 参数）
const lastAssistantKey = computed(() => {
  if (!props.isGenerating || props.items.length === 0) return ''
  for (let i = props.items.length - 1; i >= 0; i--) {
    if (props.items[i].role === 'assistant') return props.items[i].key
  }
  return ''
})

// 最后一条消息的 key（用于中断标记判断）
const lastItemKey = computed(() => props.items[props.items.length - 1]?.key ?? '')
</script>

<template>
  <div class="ai-chat-panel">
    <!-- 空态欢迎页 -->
    <div
      v-if="isEmpty"
      class="ai-chat-panel__welcome"
    >
      <!-- PG-E13: 模型加载失败错误态 -->
      <template v-if="modelLoadError">
        <div class="ai-chat-panel__welcome-error">
          <i class="i-ep-warning-filled ai-chat-panel__welcome-error-icon" />
          <p class="ai-chat-panel__welcome-error-text">
            模型列表加载失败
          </p>
          <el-button
            type="primary"
            size="small"
            @click="emit('retry-load')"
          >
            刷新重试
          </el-button>
        </div>
      </template>
      <!-- 正常态 -->
      <template v-else>
        <div class="ai-chat-panel__welcome-hero">
          <i class="i-ep-chat-dot-round ai-chat-panel__welcome-icon" />
          <h2 class="ai-chat-panel__welcome-title">{{ t('playground.welcomeTitle') }}</h2>
          <p class="ai-chat-panel__welcome-desc">{{ t('playground.welcomeDesc') }}</p>
        </div>
        <div class="ai-chat-panel__capabilities">
          <button
            v-for="cap in capabilityCards"
            :key="cap.key"
            type="button"
            class="ai-chat-panel__capability-card"
            @click="handleCapabilityClick(cap)"
          >
            <i :class="cap.icon" class="ai-chat-panel__capability-icon" />
            <span class="ai-chat-panel__capability-name">{{ t(cap.titleKey) }}</span>
            <span class="ai-chat-panel__capability-desc">{{ t(cap.descKey) }}</span>
          </button>
        </div>
        <div class="ai-chat-panel__suggestions">
          <button
            v-for="suggestion in suggestionList"
            :key="suggestion.key"
            type="button"
            class="ai-chat-panel__suggestion-chip"
            :style="{ borderColor: suggestion.color }"
            @click="handleSuggestionClick(suggestion.text)"
          >
            {{ suggestion.text }}
          </button>
        </div>
      </template>
    </div>

    <!-- 消息列表 -->
    <BubbleList
      v-else
      :list="visibleItems"
      max-height="100%"
      :auto-scroll="true"
      :show-back-button="true"
      class="ai-chat-panel__list"
    >
      <!-- 自定义气泡内容（Thinking + MarkdownRender + 光标 + 中断标记） -->
      <template #content="{ item }">
        <div :class="item.role === 'system' ? 'ai-chat-panel__system-content' : 'ai-chat-panel__bubble-content'">
        <!-- system 消息特殊标识 -->
        <div
          v-if="item.role === 'system'"
          class="ai-chat-panel__system-chip"
        >
          <span>system</span>
        </div>
        <!-- 推理过程（Thinking 受控折叠，状态持久化在 useBubbleList 模块级 thinkingExpandedState） -->
        <Thinking
          v-if="item.reasoningContent"
          :content="item.reasoningContent"
          :status="item.isReasoningStreaming ? 'thinking' : 'end'"
          :auto-collapse="!item.isReasoningStreaming"
          :model-value="item.thinkingExpanded"
          @update:model-value="setThinkingExpanded(item.key, $event)"
        />

        <!-- 正文 Markdown 流式渲染 -->
        <MarkdownRender
          v-if="item.textContent"
          :content="item.textContent"
          :final="isFinalMessage(item.key)"
          mode="chat"
          :code-block-props="{ showCopyButton: true, showHeader: true }"
          class="ai-chat-panel__markdown"
        />

        <!-- S7: 引用来源 -->
        <div
          v-if="item.sources && item.sources.length > 0"
          class="ai-chat-panel__sources"
        >
          <div
            v-for="(source, idx) in item.sources"
            :key="idx"
            class="ai-chat-panel__source"
          >
            <a
              :href="source.url"
              target="_blank"
              rel="noopener noreferrer"
              class="ai-chat-panel__source-title"
            >
              <i class="i-ep-link" />
              {{ source.title }}
            </a>
            <p
              v-if="source.snippet"
              class="ai-chat-panel__source-snippet"
            >
              {{ source.snippet }}
            </p>
          </div>
        </div>

        <!-- S7: 消息版本分支切换 -->
        <div
          v-if="item.versions && item.versions.length > 1"
          class="ai-chat-panel__branch"
        >
          <button
            type="button"
            class="ai-chat-panel__branch-btn"
            :disabled="(item.currentVersion ?? 0) === 0"
            @click="handleSwitchVersion(item, -1)"
          >
            <i class="i-ep-arrow-left" />
          </button>
          <span class="ai-chat-panel__branch-indicator">
            {{ (item.currentVersion ?? 0) + 1 }} / {{ item.versions.length }}
          </span>
          <button
            type="button"
            class="ai-chat-panel__branch-btn"
            :disabled="(item.currentVersion ?? 0) === (item.versions.length - 1)"
            @click="handleSwitchVersion(item, 1)"
          >
            <i class="i-ep-arrow-right" />
          </button>
        </div>

        <!-- PG-E11: 流式光标 -->
        <span
          v-if="item.key === lastAssistantKey && item.textContent"
          class="ai-chat-panel__cursor"
        >▍</span>

        <!-- F09: 流式中断标记 -->
        <span
          v-if="lastItemAborted && item.key === lastItemKey"
          class="ai-chat-panel__aborted"
        >
          已中断
        </span>
        </div>
      </template>

      <!-- 自定义加载占位 -->
      <template #loading>
        <div class="ai-chat-panel__loading">
          <span class="ai-chat-panel__loading-dot" />
          <span class="ai-chat-panel__loading-dot" />
          <span class="ai-chat-panel__loading-dot" />
        </div>
      </template>

      <!-- 自定义底部（时间戳 + usage + 操作按钮） -->
      <template #footer="{ item }">
        <div class="ai-chat-panel__actions">
          <span
            v-if="item.createdAt"
            class="ai-chat-panel__timestamp"
            :title="dayjs(item.createdAt).format('YYYY-MM-DD HH:mm:ss')"
          >
            {{ dayjs(item.createdAt).format('HH:mm') }}
          </span>
          <!-- PG-E05: token 用量与耗时（仅 assistant 且非流式中） -->
          <span
            v-if="item.usage && !isGenerating"
            class="ai-chat-panel__usage"
          >
            {{ formatUsage(item.usage) }}
          </span>
          <button
            type="button"
            class="ai-chat-panel__action-btn"
            :title="t('playground.actions.copy')"
            @click="handleCopy(item)"
          >
            <i class="i-ep-copy-document" />
          </button>
          <!-- PG-E02: user 消息编辑后重发 -->
          <button
            v-if="item.role === 'user'"
            type="button"
            class="ai-chat-panel__action-btn"
            :title="t('playground.actions.edit')"
            @click="emit('edit', item.rawMessage.id)"
          >
            <i class="i-ep-edit" />
          </button>
          <!-- PG-E01: assistant 消息重新生成 -->
          <button
            v-if="item.role === 'assistant'"
            type="button"
            class="ai-chat-panel__action-btn"
            :title="t('playground.actions.regenerate')"
            @click="emit('regenerate', item.rawMessage.id)"
          >
            <i class="i-ep-refresh-right" />
          </button>
          <!-- PG-E03: 所有消息均可删除，assistant 需确认 -->
          <button
            type="button"
            class="ai-chat-panel__action-btn ai-chat-panel__action-btn--danger"
            :title="t('playground.actions.delete')"
            @click="handleDeleteClick(item)"
          >
            <i class="i-ep-delete" />
          </button>
        </div>
      </template>
    </BubbleList>

    <!-- PG-E04: 错误消息内联显示（不只靠 toast） -->
    <div
      v-if="errorMessage && !isGenerating"
      class="ai-chat-panel__error-bar"
    >
      <div class="ai-chat-panel__error-content">
        <i class="i-ep-warning-filled ai-chat-panel__error-icon" />
        <span class="ai-chat-panel__error-text">{{ errorMessage }}</span>
      </div>
      <button
        type="button"
        class="ai-chat-panel__error-retry"
        @click="emit('retry')"
      >
        <i class="i-ep-refresh-right" />
        {{ t('common.retry') }}
      </button>
    </div>

    <!-- 输入区 -->
    <div class="ai-chat-panel__sender">
      <!-- 功能栏（输入框上方） -->
      <div class="ai-chat-panel__sender-toolbar">
        <RoleSelector
          v-model="selectedRole"
          :disabled="isGenerating"
        />
        <el-tooltip
          :content="t('playground.shortcuts.title') + ' (?)'"
          placement="top"
        >
          <button
            type="button"
            class="ai-chat-panel__shortcut-btn"
            @click="emit('show-shortcuts')"
          >
            <i class="i-ep-question-filled" />
          </button>
        </el-tooltip>
      </div>
      <!-- XSender 自身是卡片式输入框（自带 border+shadow+focus），无需额外包裹 -->
      <XSender
        ref="senderRef"
        :placeholder="t('playground.inputPlaceholder')"
        :loading="isGenerating"
        :disabled="isInputDisabled"
        submit-type="enter"
        @submit="handleSubmit"
        @cancel="emit('stop')"
        @change="handleSenderChange"
        @paste-file="handlePasteFile"
      >
        <template #footer>
          <div class="ai-chat-panel__sender-meta">
            <span
              v-if="charCount > 0"
              class="ai-chat-panel__char-count"
            >
              {{ charCount }} 字
            </span>
          </div>
        </template>
      </XSender>
    </div>
  </div>
</template>

<style scoped lang="scss">
.ai-chat-panel {
  display: flex;
  flex: 1 1 0%; // 填充 mainArea 剩余空间（modeBar + toolbar 之后）
  flex-direction: column;
  min-height: 0; // 允许子元素收缩
  overflow: hidden; // 防止内容溢出把 sender 推出屏幕

  &__welcome {
    display: flex;
    flex: 1;
    flex-direction: column;
    align-items: center;
    justify-content: center;
  }

  &__welcome-error {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-3);
    align-items: center;
  }

  &__welcome-error-icon {
    font-size: 48px;
    color: var(--el-color-warning);
  }

  &__welcome-error-text {
    margin: 0;
    font-size: 15px;
    color: var(--el-text-color-regular);
  }

  &__suggestions {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-2);
    justify-content: center;
    margin-top: var(--ys-spacing-4);
  }

  &__suggestion-chip {
    padding: 6px var(--ys-spacing-4);
    font-size: var(--ys-font-size-sm);
    color: var(--el-text-color-regular);
    cursor: pointer;
    background: var(--el-bg-color);
    border: 1px solid var(--el-border-color);
    border-radius: 20px;
    transition: all 0.2s;

    &:hover {
      color: var(--ys-color-primary);
      background: var(--ys-bg-brand-soft);
    }
  }

  &__list {
    flex: 1;
    min-height: 0;

    // 左侧 padding 给头像留空间，右侧不留 padding 使滚动条紧贴屏幕边缘
    padding-left: 16px;

    // 修复：overflow:hidden 让 innerList 的 height:100% 在 block 布局中生效
    // 防止 Thinking 展开后内容溢出把 sender 推出屏幕
    overflow: hidden;

    // 修复：给 innerList 设置 height:100%（而非 maxHeight），确保填满外层
    :deep(.elx-bubble-list__list) {
      height: 100%;
      padding-top: 8px; // 第一条消息上边距
    }
  }

  // 修复：移除 XSender footer 的 borderTop，避免与 ::after 伪元素形成双重边框
  :deep(.elx-x-sender__footer) {
    border-top: none;
  }

  &__bubble-content {
    max-width: 100%;

    // Thinking 组件：宽度填满 + 视觉强区分（背景色/边框/斜体）
    :deep(.elx-thinking) {
      // 内容区域宽度填满气泡
      --elx-thinking-content-wrapper-width: 100%;

      // 思考内容区视觉区分：浅紫背景 + 紫色左边框 + 斜体
      --elx-thinking-content-wrapper-background-color: color-mix(in srgb, var(--ys-color-secondary) 6%, transparent);
      --elx-thinking-content-wrapper-color: var(--el-text-color-regular);
    }

    :deep(.elx-thinking__content pre) {
      max-width: 100%;
      font-size: var(--ys-font-size-sm);
      font-style: italic;
      line-height: 1.7;
      border-color: var(--ys-color-secondary-hover);
      border-left: 3px solid var(--ys-color-secondary-hover);
    }

    // Thinking 触发按钮：与内容区保持视觉一致
    :deep(.elx-thinking__trigger) {
      background: color-mix(in srgb, var(--ys-color-secondary) 3%, transparent);
      border-color: color-mix(in srgb, var(--ys-color-secondary) 20%, transparent);
    }
  }

  // user 消息（placement=end）收紧 padding 和 min-height
  :deep(.elx-bubble--end .elx-bubble__content) {
    --elx-bubble-padding-y: 8px;
    --elx-bubble-padding-x: 14px;

    min-height: auto;

    // 主色调方案：用户气泡浅蓝背景 + 无边框
    background: var(--el-color-primary-light-9);
    border: none;
  }

  // assistant 消息（placement=start）白色背景 + 细边框
  :deep(.elx-bubble--start .elx-bubble__content) {
    background: var(--el-bg-color);
    border: 1px solid var(--el-border-color-lighter);
  }

  // avatar 间距 + 防拉伸
  :deep(.elx-bubble) {
    gap: var(--el-bubble-avatar-placeholder-gap, var(--ys-spacing-3));
  }

  // 头像容器顶部对齐，防止被 flex stretch 拉伸到气泡全高
  :deep(.elx-bubble__avatar) {
    align-self: flex-start;
  }

  // P2: system 消息特殊样式
  &__system-content {
    padding-left: var(--ys-spacing-3);
    border-left: 3px solid var(--ys-color-secondary-hover);
  }

  &__system-chip {
    display: inline-flex;
    align-items: center;
    padding: 2px var(--ys-spacing-2);
    margin-bottom: var(--ys-spacing-2);
    font-size: 11px;
    font-weight: 600;
    color: var(--ys-color-secondary-hover);
    background: color-mix(in srgb, var(--ys-color-secondary) 8%, transparent);
    border-radius: var(--el-border-radius-small);
  }

  // P2: Welcome 能力引导卡片
  &__welcome-hero {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-2);
    align-items: center;
    margin-bottom: var(--ys-spacing-6);
  }

  &__welcome-icon {
    font-size: 48px;
    color: var(--el-color-primary);
  }

  &__welcome-title {
    margin: 0;
    font-size: var(--ys-font-size-xl);
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  &__welcome-desc {
    margin: 0;
    font-size: var(--ys-font-size-base);
    color: var(--el-text-color-secondary);
    text-align: center;
  }

  &__capabilities {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: var(--ys-spacing-3);
    max-width: 600px;
    margin: 0 auto var(--ys-spacing-4);
  }

  &__capability-card {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-1);
    align-items: center;
    padding: var(--ys-spacing-4) var(--ys-spacing-3);
    cursor: pointer;
    background: var(--el-fill-color-blank);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: var(--ys-radius-md);
    transition: all 0.2s;

    &:hover {
      background: var(--ys-bg-brand-soft);
      border-color: var(--el-color-primary);
    }
  }

  &__capability-icon {
    font-size: var(--ys-font-size-2xl);
    color: var(--el-color-primary);
  }

  &__capability-name {
    font-size: var(--ys-font-size-sm);
    font-weight: 500;
    color: var(--el-text-color-primary);
  }

  &__capability-desc {
    font-size: 11px;
    color: var(--el-text-color-secondary);
    text-align: center;
  }

  &__loading {
    display: flex;
    gap: var(--ys-spacing-1);
    padding: var(--ys-spacing-2) var(--ys-spacing-1);
  }

  &__loading-dot {
    width: 8px;
    height: 8px;
    background: var(--el-text-color-placeholder);
    border-radius: 50%;
    animation: ai-chat-loading-bounce 1.4s ease-in-out infinite both;

    &:nth-child(1) { animation-delay: -0.32s; }
    &:nth-child(2) { animation-delay: -0.16s; }
  }

  &__markdown {
    font-size: var(--ys-font-size-base);
    line-height: 1.6;

    // markstream-vue 的 paragraph-node 默认 margin: 14px 0 导致短文本气泡过高
    :deep(.paragraph-node) {
      margin: 0;

      & + .paragraph-node {
        margin-top: 8px;
      }
    }
  }

  &__cursor {
    display: inline-block;
    margin-left: 2px;
    font-weight: bold;
    color: var(--el-color-primary);
    animation: ai-chat-cursor-blink 1s steps(2) infinite;
  }

  &__actions {
    display: flex;
    gap: var(--ys-spacing-1);
    align-items: center;
    margin-top: 6px;
    padding-top: 4px;
    border-top: 1px solid var(--el-border-color-extra-light);
  }

  &__timestamp {
    margin-right: 2px;
    font-size: var(--ys-font-size-xs);
    font-weight: 500;
    color: var(--el-text-color-secondary);
  }

  &__usage {
    margin-right: var(--ys-spacing-1);
    padding: 1px 6px;
    font-size: var(--ys-font-size-xs);
    font-weight: 500;
    color: var(--el-color-primary);
    background: var(--ys-bg-brand-soft);
    border-radius: 10px;
  }

  &__action-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    padding: 0;
    font-size: var(--ys-font-size-base);
    color: var(--el-text-color-secondary);
    cursor: pointer;
    background: var(--el-fill-color);
    border: 1px solid transparent;
    border-radius: var(--el-border-radius-base);
    transition: all 0.15s ease;

    &:hover {
      color: var(--el-color-primary);
      background: var(--ys-bg-brand-soft);
      border-color: var(--el-color-primary-light-5);
      transform: translateY(-1px);
    }

    &:active {
      transform: translateY(0);
    }

    &--danger {
      color: var(--el-text-color-secondary);

      &:hover {
        color: var(--el-color-danger);
        background: var(--el-color-danger-light-9);
        border-color: var(--el-color-danger-light-5);
      }
    }
  }

  &__aborted {
    display: inline-block;
    padding: 2px var(--ys-spacing-2);
    margin-top: 4px;
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-secondary);
    background: var(--el-fill-color-light);
    border-radius: var(--el-border-radius-small);
  }

  // S7: 引用来源
  &__sources {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-2);
    padding: var(--ys-spacing-2) var(--ys-spacing-3);
    margin-top: 8px;
    background: var(--el-fill-color-lighter);
    border-radius: var(--el-border-radius-base);
  }

  &__source {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  &__source-title {
    display: inline-flex;
    gap: var(--ys-spacing-1);
    align-items: center;
    font-size: var(--ys-font-size-sm);
    font-weight: 500;
    color: var(--el-color-primary);
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }

  &__source-snippet {
    margin: 0;
    font-size: var(--ys-font-size-xs);
    line-height: 1.4;
    color: var(--el-text-color-secondary);
  }

  // S7: 消息版本分支切换
  &__branch {
    display: inline-flex;
    gap: var(--ys-spacing-1);
    align-items: center;
    padding: 2px var(--ys-spacing-1);
    margin-top: 8px;
    background: var(--el-fill-color-light);
    border-radius: var(--el-border-radius-small);
  }

  &__branch-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    padding: 0;
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-secondary);
    cursor: pointer;
    background: transparent;
    border: 0;
    border-radius: var(--el-border-radius-small);
    transition: all 0.2s;

    &:disabled {
      cursor: not-allowed;
      opacity: 0.4;
    }

    &:hover:not(:disabled) {
      color: var(--el-color-primary);
      background: var(--el-fill-color);
    }
  }

  &__branch-indicator {
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-secondary);
    user-select: none;
  }

  &__error-bar {
    display: flex;
    gap: var(--ys-spacing-3);
    align-items: center;
    justify-content: space-between;
    padding: 10px var(--ys-spacing-4);
    margin: 0 var(--ys-spacing-4);
    background: var(--el-color-danger-light-9);
    border: 1px solid var(--el-color-danger-light-5);
    border-radius: var(--el-border-radius-base);
  }

  &__error-content {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
    min-width: 0;
  }

  &__error-icon {
    flex-shrink: 0;
    font-size: var(--ys-font-size-lg);
    color: var(--el-color-danger);
  }

  &__error-text {
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: var(--ys-font-size-sm);
    color: var(--el-color-danger);
    white-space: nowrap;
  }

  &__error-retry {
    display: flex;
    flex-shrink: 0;
    gap: var(--ys-spacing-1);
    align-items: center;
    padding: var(--ys-spacing-1) var(--ys-spacing-3);
    font-size: var(--ys-font-size-sm);
    color: var(--el-color-danger);
    cursor: pointer;
    background: var(--el-bg-color);
    border: 1px solid var(--el-color-danger-light-5);
    border-radius: var(--el-border-radius-small);
    transition: all 0.2s;

    &:hover {
      color: #fff;
      background: var(--el-color-danger);
      border-color: var(--el-color-danger);
    }
  }

  &__sender {
    flex-shrink: 0;
    padding: var(--ys-spacing-3) var(--ys-spacing-4) var(--ys-spacing-4);
    margin-top: var(--ys-spacing-4); // 与 bubbleList 之间留出间距
    background: var(--el-bg-color);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: var(--ys-radius-lg);
    box-shadow: var(--ys-shadow-sm);
  }

  &__sender-toolbar {
    display: flex;
    flex-shrink: 0;
    gap: var(--ys-spacing-2);
    align-items: center;
    padding: 0 var(--ys-spacing-1) var(--ys-spacing-2);
  }

  &__shortcut-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    padding: 0;
    font-size: var(--ys-font-size-lg);
    color: var(--el-text-color-secondary);
    cursor: pointer;
    background: transparent;
    border: 0;
    border-radius: var(--el-border-radius-small);
    transition: all 0.2s;

    &:hover {
      color: var(--el-color-primary);
      background: var(--el-fill-color-light);
    }
  }

  &__sender-meta {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    padding: 2px var(--ys-spacing-3);
  }

  &__char-count {
    font-size: 11px;
    color: var(--el-text-color-placeholder);
  }
}

@keyframes ai-chat-loading-bounce {
  0%, 80%, 100% {
    transform: scale(0);
  }

  40% {
    transform: scale(1);
  }
}

@keyframes ai-chat-cursor-blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}
</style>
