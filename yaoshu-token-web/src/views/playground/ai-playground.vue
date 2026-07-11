<script setup lang="ts">
/**
 * AI Playground 视图（/playground）— 路由入口。
 *
 * 职责：鉴权 + 数据加载（模型列表）+ 状态管理 + 事件处理。
 * 布局装配委托给 PlaygroundShell（左右分栏）。
 *
 * 数据流层：@ai-sdk/vue Chat + OpenAIChatTransport（useAiChat）
 * 对话 UI 层：vue-element-plus-x BubbleList + Thinking + XSender（AiChatPanel）
 * 流式渲染层：markstream-vue MarkdownRender（AiChatPanel 内消费）
 */
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { useModelsLoader } from '@/composables/playground/useModelsLoader'
import { useAiChat } from '@/composables/playground/useAiChat'
import { useBubbleList } from '@/composables/playground/useBubbleList'
import type { PlaygroundBubbleItem } from '@/composables/playground/useBubbleList'
import { usePromptTemplates } from '@/composables/playground/usePromptTemplates'
import { usePlaygroundShortcuts, SHORTCUT_TABLE } from '@/composables/playground/usePlaygroundShortcuts'
import { useModelComparison } from '@/composables/playground/useModelComparison'
import PlaygroundShell from '@/components/playground/PlaygroundShell.vue'
import { useAuthStore } from '@/store/modules/auth'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import type { PlaygroundConfig, ParameterEnabled, DebugTab } from '@/api/playground/types'

const router = useRouter()
const authStore = useAuthStore()
const { t } = useI18n()

onMounted(() => {
  if (!authStore.isLoggedIn) {
    router.push('/sign-in')
  }
})

// 图片输入状态
const imageUrls = ref<string[]>([])
const imageEnabled = ref<boolean>(false)

// PG-E02: 编辑消息状态 + PlaygroundShell 模板引用
const editingMessageId = ref<string | null>(null)
const shellRef = ref<InstanceType<typeof PlaygroundShell> | null>(null)

// 核心编排（@ai-sdk/vue Chat + OpenAIChatTransport）
const {
  state,
  custom,
  debug,
  messages,
  error,
  isGenerating,
  usageMap,
  chat,
  sendMessage,
  stopGeneration,
  regenerate,
  clearError,
  clearMessages,
  insertMessage
} = useAiChat({
  imageUrls: () => imageUrls.value,
  imageEnabled: () => imageEnabled.value
})

// S7: 消息版本分支历史
const versionMap = ref<Record<string, PlaygroundBubbleItem[]>>({})
const currentVersionMap = ref<Record<string, number>>({})
let pendingVersion: PlaygroundBubbleItem[] | null = null

// BubbleList 格式转换
const { bubbleItems, clearTimestamps } = useBubbleList(messages, usageMap, versionMap, currentVersionMap)

// 模型/分组加载
const modelsLoader = useModelsLoader()

const loadModelsAndGroups = async (): Promise<void> => {
  await modelsLoader.loadAll(state.setModels, state.setGroups)
  if (modelsLoader.error.value) {
    ElMessage.error(t('playground.error.modelLoadFailedRetry'))
    return
  }
  if (
    state.models.value.length > 0 &&
    !state.models.value.find((m) => m.value === state.config.value.model)
  ) {
    // 默认选最便宜的模型：查 Pricing API 交叉比对 modelRatio
    const cheapest = await pickCheapestModel(state.models.value)
    state.updateConfig('model', cheapest)
  }
  if (
    state.groups.value.length > 0 &&
    !state.groups.value.find((g) => g.value === state.config.value.group)
  ) {
    const first = state.groups.value[0]
    if (first) state.updateConfig('group', first.value)
  }
}

/**
 * 从可用模型列表中选最便宜的对话模型（按 Pricing API 的 modelRatio 升序）。
 * 排除图片/embedding/语音等非对话模型，仅考虑 quotaType=0（按量计费）的文本对话模型。
 * Pricing API 无覆盖或匹配失败时 fallback 到列表第一个。
 */
async function pickCheapestModel(models: { label: string; value: string }[]): Promise<string> {
  const fallback = models[0]?.value ?? ''
  if (models.length === 0) return fallback
  try {
    const { getPricing } = await import('@/api/pricing')
    const data = await getPricing()
    if (!data?.pricing?.length) return fallback
    // 非对话模型标签关键词（tags 或 endpoints 含这些词的排除）
    const EXCLUDE_TAGS = ['embedding', 'image', 'tts', 'stt', 'rerank', 'moderation']
    const EXCLUDE_ENDPOINTS = ['embeddings', 'image-generation', 'audio', 'rerank']
    function isChatModel(p: { tags?: string; supportedEndpointTypes?: string[] }): boolean {
      const tags = (p.tags ?? '').toLowerCase()
      const endpoints = (p.supportedEndpointTypes ?? []).join(' ').toLowerCase()
      const excludeStr = [...EXCLUDE_TAGS, ...EXCLUDE_ENDPOINTS]
      return !excludeStr.some(kw => tags.includes(kw) || endpoints.includes(kw))
    }
    // 构建 modelName → modelRatio 映射（仅按量计费对话模型）
    const ratioMap = new Map<string, number>()
    for (const p of data.pricing) {
      if (
        p.quotaType === 0 &&
        typeof p.modelRatio === 'number' &&
        p.modelRatio > 0 &&
        isChatModel(p)
      ) {
        ratioMap.set(p.modelName, p.modelRatio)
      }
    }
    // 在可用模型中找 modelRatio 最低的
    let best = fallback
    let bestRatio = Infinity
    for (const m of models) {
      const ratio = ratioMap.get(m.value)
      if (ratio !== undefined && ratio < bestRatio) {
        bestRatio = ratio
        best = m.value
      }
    }
    return best
  } catch {
    return fallback
  }
}

onMounted(() => {
  void loadModelsAndGroups()
})

// Drawer 状态
const parameterDrawerOpen = ref<boolean>(false)
const debugDrawerOpen = ref<boolean>(false)

// P3: 预设模板
const { templates, addTemplate, deleteTemplate } = usePromptTemplates()

function handleSaveTemplate(name: string): void {
  addTemplate({
    name,
    config: { ...state.config.value },
    parameterEnabled: { ...state.parameterEnabled.value },
    systemPrompt: state.config.value.systemPrompt
  })
}

function handleLoadTemplate(id: string): void {
  const tpl = templates.value.find((t) => t.id === id)
  if (!tpl) return
  if (tpl.config) {
    state.config.value = { ...state.config.value, ...tpl.config }
  }
  if (tpl.parameterEnabled) {
    state.parameterEnabled.value = { ...state.parameterEnabled.value, ...tpl.parameterEnabled }
  }
}

function handleDeleteTemplate(id: string): void {
  deleteTemplate(id)
}

// P3: 快捷键帮助弹窗
const shortcutHelpOpen = ref<boolean>(false)

// P3: 快捷键体系
async function handleSaveTemplateShortcut(): Promise<void> {
  try {
    const { value } = await ElMessageBox.prompt(
      t('playground.templates.savePrompt'),
      t('playground.templates.saveTitle'),
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        inputPlaceholder: t('playground.templates.namePlaceholder')
      }
    )
    if (value?.trim()) {
      handleSaveTemplate(value.trim())
      ElMessage.success(t('playground.templates.saved'))
    }
  } catch {
    // 用户取消
  }
}

const { setup: setupShortcuts, teardown: teardownShortcuts } = usePlaygroundShortcuts({
  clear: handleClear,
  regenerate: handleRegenerate,
  stop: handleStop,
  saveTemplate: () => void handleSaveTemplateShortcut(),
  setRole: () => { /* TODO: 需 AiChatPanel 暴露 selectedRole */ },
  closeDrawer: () => {
    parameterDrawerOpen.value = false
    debugDrawerOpen.value = false
  },
  showHelp: () => { shortcutHelpOpen.value = true }
})

onMounted(() => {
  setupShortcuts()
})

onUnmounted(() => {
  teardownShortcuts()
})

// P4: 模型对比
const comparison = useModelComparison({
  systemPrompt: () => state.config.value.systemPrompt
})
const mode = ref<'single' | 'comparison'>('single')

// 发送消息（PG-E02: 编辑模式下先删除原消息及后续再重发；S7: 保留版本历史）
async function handleSubmit(text: string): Promise<void> {
  if (editingMessageId.value) {
    const id = editingMessageId.value
    editingMessageId.value = null
    // S7: 保留原消息快照作为版本历史
    const originalBubble = bubbleItems.value.find(b => b.key === id)
    if (originalBubble) {
      pendingVersion = [{ ...originalBubble }]
    }
    const next = [...messages.value]
    const index = next.findIndex((m) => m.id === id)
    if (index !== -1) {
      next.splice(index)
      chat.messages = next
    }
    clearError()
  }
  await sendMessage(text)
  // S7: 关联版本历史到新消息
  if (pendingVersion) {
    const lastMsg = messages.value[messages.value.length - 1]
    if (lastMsg) {
      versionMap.value[lastMsg.id] = pendingVersion
      pendingVersion = null
    }
  }
}

function handleStop(): void {
  void stopGeneration()
}

// PG-E01: 消息级重新生成（指定 assistant 消息）
function handleRegenerateMessage(id: string): void {
  clearError()
  void regenerate(id)
}

// PG-E04: 错误内联重试（重新生成最后一条 assistant）
function handleRetry(): void {
  clearError()
  void regenerate()
}

// PG-E02: 编辑用户消息 — 提取文本填入 XSender 并标记编辑态
function handleEditMessage(id: string): void {
  const msg = messages.value.find((m) => m.id === id)
  if (!msg) return
  const text = msg.parts
    .filter((p): p is { type: 'text'; text: string } => p.type === 'text')
    .map((p) => p.text)
    .join('')
  shellRef.value?.fillEditor(text)
  editingMessageId.value = id
}

// 重新生成最后一条 assistant 消息
function handleRegenerate(): void {
  void regenerate()
}

// 清空对话
function handleClear(): void {
  clearMessages()
  clearTimestamps()
  versionMap.value = {}
  currentVersionMap.value = {}
  ElMessage.info(t('playground.message.cleared'))
}

// 插入指定角色的消息（few-shot 构造，不触发请求）
function handleInsert(payload: { role: 'assistant' | 'system'; text: string }): void {
  insertMessage(payload.role, payload.text)
}

// 当前模型的 max_context（后端 /api/user/models 填充，null/undefined 降级为仅显示已用 tokens）
const currentModelMaxContext = computed(() => {
  const model = state.models.value.find(m => m.value === state.config.value.model)
  return model?.maxContext
})

// context 用量：最后一次请求的 total_tokens = 当前对话总占用
const contextTokens = computed(() => {
  for (let i = messages.value.length - 1; i >= 0; i--) {
    const msg = messages.value[i]
    if (msg.role === 'assistant') {
      const usage = usageMap.value[msg.id]
      if (usage?.totalTokens !== undefined) return usage.totalTokens
    }
  }
  return undefined
})

// S7: 切换消息版本
function handleSwitchVersion(payload: { messageId: string; version: number }): void {
  currentVersionMap.value[payload.messageId] = payload.version
}

// PG-E08: 对话导出（Markdown 格式）
function handleExportChat(): void {
  if (messages.value.length === 0) return
  const ROLE_LABEL: Record<string, string> = {
    user: t('playground.role.user'),
    assistant: t('playground.role.assistant'),
    system: t('playground.role.system')
  }
  const sections = messages.value.map((m) => {
    const label = ROLE_LABEL[m.role] ?? m.role
    const text = m.parts
      .filter((p): p is { type: 'text'; text: string } => p.type === 'text')
      .map((p) => p.text)
      .join('')
    return `## ${label}\n\n${text}`
  })
  const md = `# ${t('playground.exportHeader')}\n\n${sections.join('\n\n---\n\n')}\n`
  const blob = new Blob([md], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  const now = new Date()
  const stamp = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}-${String(now.getHours()).padStart(2, '0')}${String(now.getMinutes()).padStart(2, '0')}`
  a.download = `playground-${stamp}.md`
  a.click()
  URL.revokeObjectURL(url)
}

// 参数/配置导入导出
const defaultPayload = computed(() => debug.previewPayload.value)

function handleConfigImport(value: {
  config: PlaygroundConfig
  parameterEnabled: ParameterEnabled
}): void {
  state.config.value = { ...state.config.value, ...value.config }
  state.parameterEnabled.value = {
    ...state.parameterEnabled.value,
    ...value.parameterEnabled
  }
}

function handleConfigReset(value: { resetMessages: boolean }): void {
  state.resetConfig()
  if (value.resetMessages) {
    clearMessages()
  }
}

const isInputDisabled = computed(
  () => modelsLoader.loading.value && state.models.value.length === 0
)

// PG-E09: 模型切换时对话保留提示（有历史消息时一次性通知）
function handleModelChange(v: string): void {
  const prevModel = state.config.value.model
  state.updateConfig('model', v)
  if (prevModel !== v && messages.value.length > 0) {
    ElNotification.info({
      title: t('playground.error.modelSwitchedTitle'),
      message: t('playground.error.modelSwitchedMsg'),
      duration: 4000,
      position: 'bottom-right'
    })
  }
}

// F10: ConfigManager messages 导出适配 — UIMessage[] → 简单导出格式
const exportableMessages = computed(() =>
  messages.value.map((m) => ({
    key: m.id,
    role: m.role,
    content: m.parts
      .filter((p): p is { type: 'text'; text: string } => p.type === 'text')
      .map((p) => p.text)
      .join(''),
    createdAt: Date.now()
  }))
)

// F06续: AiChatPanel 事件回调
function handleCopy(_id: string): void {
  // 复制已在 AiChatPanel 内部完成，此处无需额外操作
}

function handleDeleteMessage(id: string): void {
  const next = [...messages.value]
  const index = next.findIndex((m) => m.id === id)
  if (index !== -1) {
    next.splice(index, 1)
    chat.messages = next
  }
}

// F02: 错误处理 — 消费 error ref
watch(error, (err) => {
  if (err) {
    ElMessage.error(err.message || t('playground.error.requestFailed'))
  }
})
</script>

<template>
  <div class="ai-playground">
    <PlaygroundShell
      ref="shellRef"
      :config="state.config.value"
      :parameter-enabled="state.parameterEnabled.value"
      :models="state.models.value"
      :groups="state.groups.value"
      :custom-request-mode="custom.customRequestMode.value"
      :custom-request-body="custom.customRequestBody.value"
      :default-payload="defaultPayload"
      :is-input-disabled="isInputDisabled"
      :loading="modelsLoader.loading.value"
      :exportable-messages="exportableMessages"
      :bubble-items="bubbleItems"
      :is-generating="isGenerating"
      :has-error="!!error"
      :error-message="error?.message"
      :image-urls="imageUrls"
      :model-load-error="!!modelsLoader.error.value"
      :has-messages="messages.length > 0"
      :context-tokens="contextTokens"
      :max-context="currentModelMaxContext"
      :parameter-drawer-open="parameterDrawerOpen"
      :debug-drawer-open="debugDrawerOpen"
      :preview-payload="debug.previewPayload.value"
      :actual-request="debug.actualRequest.value"
      :sse-events="debug.sseEvents.value"
      :active-debug-tab="debug.activeDebugTab.value"
      :templates="templates"
      :mode="mode"
      :comparison-columns="comparison.columns.value"
      :comparison-selected-models="comparison.selectedModels.value"
      :comparison-is-comparing="comparison.isComparing.value"
      :comparison-prompt="comparison.prompt.value"
      @update:config="(v: PlaygroundConfig) => (state.config.value = v)"
      @update:parameter-enabled="(v: ParameterEnabled) => (state.parameterEnabled.value = v)"
      @update:model="handleModelChange"
      @update:group="(v: string) => state.updateConfig('group', v)"
      @update:system-prompt="(v: string) => state.updateConfig('systemPrompt', v)"
      @open-custom-request="parameterDrawerOpen = true"
      @open-debug="debugDrawerOpen = true"
      @config-import="handleConfigImport"
      @config-reset="handleConfigReset"
      @clear="handleClear"
      @export="handleExportChat"
      @update:stream="(v: boolean) => state.updateConfig('stream', v)"
      @submit="handleSubmit"
      @stop="handleStop"
      @copy="handleCopy"
      @delete="handleDeleteMessage"
      @regenerate-message="handleRegenerateMessage"
      @edit="handleEditMessage"
      @retry="handleRetry"
      @retry-load="loadModelsAndGroups"
      @paste-image="(url: string) => imageUrls.push(url)"
      @switch-version="handleSwitchVersion"
      @insert="handleInsert"
      @show-shortcuts="shortcutHelpOpen = true"
      @update:parameter-drawer-open="(v: boolean) => (parameterDrawerOpen = v)"
      @update:debug-drawer-open="(v: boolean) => (debugDrawerOpen = v)"
      @update:custom-request="(v: { mode: boolean; body: string }) => { custom.setMode(v.mode); custom.setBody(v.body) }"
      @update:active-debug-tab="(v: DebugTab) => (debug.activeDebugTab.value = v)"
      @clear-sse="debug.clearSseEvents()"
      @reset="state.resetConfig()"
      @save-template="handleSaveTemplate"
      @load-template="handleLoadTemplate"
      @delete-template="handleDeleteTemplate"
      @update:mode="(v: 'single' | 'comparison') => (mode = v)"
      @update:comparison-prompt="(v: string) => (comparison.prompt.value = v)"
      @add-comparison-column="(model: string) => comparison.addColumn(model)"
      @remove-comparison-column="(model: string) => comparison.removeColumn(model)"
      @run-comparison="() => void comparison.runComparison()"
      @abort-comparison="() => comparison.abortAll()"
    />

    <!-- P3: 快捷键帮助弹窗 -->
    <el-dialog
      v-model="shortcutHelpOpen"
      :title="t('playground.shortcuts.title')"
      width="480px"
      append-to-body
    >
      <div class="shortcut-help">
        <div
          v-for="item in SHORTCUT_TABLE"
          :key="item.actionKey"
          class="shortcut-help__row"
        >
          <span class="shortcut-help__keys">{{ item.keys }}</span>
          <span class="shortcut-help__action">{{ t(item.actionKey) }}</span>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.ai-playground {
  // 绝对定位填满 __main-inner 的内容区（减去 mainInner 的 padding 16px）
  // inset:0 会填满 padding-box（包含 padding 区域）导致多出 32px（上下各 16px）
  position: absolute;
  inset: 16px 0;
  background: var(--el-bg-color);
}

.shortcut-help {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-2);

  &__row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: var(--ys-spacing-2) var(--ys-spacing-3);
    background: var(--el-fill-color-light);
    border-radius: var(--el-border-radius-base);
  }

  &__keys {
    font-family: monospace;
    font-size: var(--ys-font-size-sm);
    font-weight: 600;
    color: var(--el-color-primary);
  }

  &__action {
    font-size: var(--ys-font-size-sm);
    color: var(--el-text-color-regular);
  }
}
</style>
