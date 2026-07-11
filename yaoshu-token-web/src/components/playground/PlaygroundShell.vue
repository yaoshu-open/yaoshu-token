<script setup lang="ts">
/**
 * PlaygroundShell — Playground 布局容器。
 *
 * 左右分栏：左侧常驻设置面板 + 右侧对话区（操作栏 + 对话面板）。
 * 从 ai-playground.vue 接管布局装配职责。
 */
import { ref, computed } from 'vue'
import type {
  PlaygroundConfig,
  ParameterEnabled,
  ModelOption,
  GroupOption,
  ChatCompletionRequest,
  SseEventRecord,
  DebugTab
} from '@/api/playground/types'
import type { PlaygroundBubbleItem } from '@/composables/playground/useBubbleList'
import type { PromptTemplate } from '@/composables/playground/usePromptTemplates'
import type { ComparisonColumn } from '@/composables/playground/useModelComparison'
import PlaygroundSettingsPanel from './PlaygroundSettingsPanel.vue'
import ConversationToolbar from './ConversationToolbar.vue'
import AiChatPanel from './AiChatPanel.vue'
import ComparisonView from './ComparisonView.vue'
import ParameterDrawer from './ParameterDrawer.vue'
import DebugDrawer from './DebugDrawer.vue'

interface Props {
  // Settings panel
  config: PlaygroundConfig
  parameterEnabled: ParameterEnabled
  models: ModelOption[]
  groups: GroupOption[]
  customRequestMode: boolean
  customRequestBody: string
  defaultPayload?: ChatCompletionRequest | null
  isInputDisabled: boolean
  loading: boolean
  exportableMessages: Record<string, unknown>[]
  templates: PromptTemplate[]

  // Comparison mode
  mode: 'single' | 'comparison'
  comparisonColumns: ComparisonColumn[]
  comparisonSelectedModels: string[]
  comparisonIsComparing: boolean
  comparisonPrompt: string

  // Conversation
  bubbleItems: PlaygroundBubbleItem[]
  isGenerating: boolean
  hasError: boolean
  errorMessage?: string
  imageUrls: string[]
  modelLoadError: boolean
  hasMessages: boolean

  // Context
  contextTokens?: number
  maxContext?: number

  // Drawers
  parameterDrawerOpen: boolean
  debugDrawerOpen: boolean

  // Debug
  previewPayload: ChatCompletionRequest | null
  actualRequest: ChatCompletionRequest | null
  sseEvents: SseEventRecord[]
  activeDebugTab: DebugTab
}

const props = defineProps<Props>()
const emit = defineEmits<{
  // Settings
  'update:config': [value: PlaygroundConfig]
  'update:parameterEnabled': [value: ParameterEnabled]
  'update:model': [value: string]
  'update:group': [value: string]
  'update:systemPrompt': [value: string]
  'open-custom-request': []
  'open-debug': []
  'config-import': [value: { config: PlaygroundConfig; parameterEnabled: ParameterEnabled }]
  'config-reset': [value: { resetMessages: boolean }]
  'save-template': [name: string]
  'load-template': [id: string]
  'delete-template': [id: string]

  // Comparison mode
  'update:mode': [value: 'single' | 'comparison']
  'update:comparisonPrompt': [value: string]
  'add-comparison-column': [model: string]
  'remove-comparison-column': [model: string]
  'run-comparison': []
  'abort-comparison': []

  // Conversation
  'clear': []
  'export': []
  'update:stream': [value: boolean]

  // AiChatPanel
  'submit': [text: string]
  'stop': []
  'copy': [id: string]
  'delete': [id: string]
  'regenerate-message': [id: string]
  'edit': [id: string]
  'retry': []
  'retry-load': []
  'paste-image': [dataUrl: string]
  'switch-version': [payload: { messageId: string; version: number }]
  'insert': [payload: { role: 'assistant' | 'system'; text: string }]
  'show-shortcuts': []

  // Drawers
  'update:parameterDrawerOpen': [value: boolean]
  'update:debugDrawerOpen': [value: boolean]
  'update:customRequest': [value: { mode: boolean; body: string }]
  'update:activeDebugTab': [value: DebugTab]
  'clear-sse': []
  'reset': []
}>()

// 左侧面板折叠
const sidebarCollapsed = ref(false)
const sidebarClass = computed(() => [
  'playground-shell__sidebar',
  { 'playground-shell__sidebar--collapsed': sidebarCollapsed.value }
])

// AiChatPanel 模板引用（转发 fillEditor 供父组件调用）
const chatPanelRef = ref<InstanceType<typeof AiChatPanel> | null>(null)

function fillEditor(text: string): void {
  chatPanelRef.value?.fillEditor(text)
}

defineExpose({ fillEditor })
</script>

<template>
  <div class="playground-shell">
    <div class="playground-shell__body">
      <!-- 左侧设置面板（可折叠） -->
      <aside :class="sidebarClass">
        <div
          v-if="!sidebarCollapsed"
          class="playground-shell__sidebar-inner"
        >
          <PlaygroundSettingsPanel
          :config="props.config"
          :parameter-enabled="props.parameterEnabled"
          :models="props.models"
          :groups="props.groups"
          :custom-request-mode="props.customRequestMode"
          :disabled="props.isInputDisabled"
          :loading="props.loading"
          :exportable-messages="props.exportableMessages"
          :templates="props.templates"
          @update:config="(v: PlaygroundConfig) => emit('update:config', v)"
          @update:parameter-enabled="(v: ParameterEnabled) => emit('update:parameterEnabled', v)"
          @update:model="(v: string) => emit('update:model', v)"
          @update:group="(v: string) => emit('update:group', v)"
          @update:system-prompt="(v: string) => emit('update:systemPrompt', v)"
          @open-custom-request="emit('open-custom-request')"
          @open-debug="emit('open-debug')"
          @config-import="(v) => emit('config-import', v)"
          @config-reset="(v) => emit('config-reset', v)"
          @save-template="(name: string) => emit('save-template', name)"
          @load-template="(id: string) => emit('load-template', id)"
          @delete-template="(id: string) => emit('delete-template', id)"
          />
        </div>
      </aside>

      <!-- 折叠/展开按钮（在 body 层级，不受 sidebar overflow 裁剪） -->
      <button
        type="button"
        class="playground-shell__sidebar-toggle"
        :class="{ 'playground-shell__sidebar-toggle--collapsed': sidebarCollapsed }"
        :title="sidebarCollapsed ? '展开设置面板' : '折叠设置面板'"
        @click="sidebarCollapsed = !sidebarCollapsed"
      >
        <i :class="sidebarCollapsed ? 'i-ep-arrow-right' : 'i-ep-arrow-left'" />
      </button>

      <!-- 右侧对话区 -->
      <main class="playground-shell__main">
        <!-- 模式切换 -->
        <div class="playground-shell__mode-bar">
          <el-radio-group
            :model-value="props.mode"
            size="small"
            @update:model-value="(v: string | number | boolean | undefined) => emit('update:mode', (v ?? 'single') as 'single' | 'comparison')"
          >
            <el-radio-button value="single">
              {{ $t('playground.comparison.modeSingle') }}
            </el-radio-button>
            <el-radio-button value="comparison">
              {{ $t('playground.comparison.modeComparison') }}
            </el-radio-button>
          </el-radio-group>
        </div>

        <!-- 单对话模式 -->
        <template v-if="props.mode === 'single'">
          <ConversationToolbar
            :is-generating="props.isGenerating"
            :has-messages="props.hasMessages"
            :stream-enabled="props.config.stream"
            :context-tokens="props.contextTokens"
            :max-context="props.maxContext"
            @clear="emit('clear')"
            @export="emit('export')"
            @update:stream="(v: boolean) => emit('update:stream', v)"
          />
          <AiChatPanel
            ref="chatPanelRef"
            :items="props.bubbleItems"
            :is-generating="props.isGenerating"
            :is-input-disabled="props.isInputDisabled"
            :has-error="props.hasError"
            :error-message="props.errorMessage"
            :image-urls="props.imageUrls"
            :model-load-error="props.modelLoadError"
            @submit="(text: string) => emit('submit', text)"
            @stop="emit('stop')"
            @copy="(id: string) => emit('copy', id)"
            @delete="(id: string) => emit('delete', id)"
            @regenerate="(id: string) => emit('regenerate-message', id)"
            @edit="(id: string) => emit('edit', id)"
            @retry="emit('retry')"
            @retry-load="emit('retry-load')"
            @paste-image="(url: string) => emit('paste-image', url)"
            @switch-version="(payload) => emit('switch-version', payload)"
          @insert="(payload) => emit('insert', payload)"
          @show-shortcuts="emit('show-shortcuts')"
          />
        </template>

        <!-- 模型对比模式 -->
        <ComparisonView
          v-else
          :models="props.models"
          :columns="props.comparisonColumns"
          :selected-models="props.comparisonSelectedModels"
          :is-comparing="props.comparisonIsComparing"
          :prompt="props.comparisonPrompt"
          @update:prompt="(v: string) => emit('update:comparisonPrompt', v)"
          @add-column="(model: string) => emit('add-comparison-column', model)"
          @remove-column="(model: string) => emit('remove-comparison-column', model)"
          @run="emit('run-comparison')"
          @abort="emit('abort-comparison')"
        />
      </main>
    </div>

    <!-- 参数抽屉 -->
    <ParameterDrawer
      :model-value="props.parameterDrawerOpen"
      :config="props.config"
      :parameter-enabled="props.parameterEnabled"
      :custom-request-mode="props.customRequestMode"
      :custom-request-body="props.customRequestBody"
      :default-payload="props.defaultPayload"
      @update:model-value="(v: boolean) => emit('update:parameterDrawerOpen', v)"
      @update:config="(v: PlaygroundConfig) => emit('update:config', v)"
      @update:parameter-enabled="(v: ParameterEnabled) => emit('update:parameterEnabled', v)"
      @update:custom-request="(v: { mode: boolean; body: string }) => emit('update:customRequest', v)"
      @reset="emit('reset')"
    />

    <!-- 调试抽屉 -->
    <DebugDrawer
      :model-value="props.debugDrawerOpen"
      :preview-payload="props.previewPayload"
      :actual-request="props.actualRequest"
      :sse-events="props.sseEvents"
      :active-tab="props.activeDebugTab"
      :custom-request-mode="props.customRequestMode"
      @update:model-value="(v: boolean) => emit('update:debugDrawerOpen', v)"
      @update:active-tab="(v: DebugTab) => emit('update:activeDebugTab', v)"
      @clear-sse="emit('clear-sse')"
    />
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens' as *;

.playground-shell {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--el-bg-color);

  &__body {
    position: relative;
    display: flex;
    flex: 1;
    min-height: 0;
  }

  &__sidebar {
    flex-shrink: 0;
    width: 320px;
    overflow: hidden;
    background: var(--el-bg-color);
    border-right: 1px solid var(--el-border-color-lighter);
    transition: width 0.25s var(--el-transition-function-ease-in-out-bezier);

    &--collapsed {
      width: 0;
      border-right: 0;
    }
  }

  &__sidebar-inner {
    width: 320px;
    height: 100%;
    overflow: hidden;
  }

  // 折叠/展开按钮 — 在 body 层级绝对定位，贴在 sidebar 右边缘
  &__sidebar-toggle {
    position: absolute;
    top: 50%;
    left: 306px; // 320px(sidebar width) - 14px(按钮半宽) = 306px
    z-index: 10;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-secondary);
    cursor: pointer;
    background: var(--el-bg-color);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 50%;
    box-shadow: var(--ys-shadow-sm);
    transform: translateY(-50%);
    transition: left 0.25s var(--el-transition-function-ease-in-out-bezier),
      color 0.2s, border-color 0.2s;

    &:hover {
      color: var(--el-color-primary);
      border-color: var(--el-color-primary);
    }

    &--collapsed {
      left: -14px; // sidebar 折叠后 width:0，按钮贴在左边缘
    }
  }

  &__main {
    display: flex;
    flex: 1;
    flex-direction: column;
    min-width: 0;
    overflow: hidden;
  }

  &__mode-bar {
    display: flex;
    flex-shrink: 0;
    align-items: center;
    padding: var(--ys-spacing-2) var(--ys-spacing-4);
    border-bottom: 1px solid var(--el-border-color-lighter);
  }
}
</style>
