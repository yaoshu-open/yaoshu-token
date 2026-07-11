<script setup lang="ts">
/**
 * ComparisonView — 多模型并行对比视图。
 *
 * N 列并排流式输出（max 4），每列独立状态/内容/usage。
 * 底部共用 prompt 输入 + 运行/停止按钮。
 */
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElNotification, ElMessage } from 'element-plus'
import { Thinking } from 'vue-element-plus-x'
import { MarkdownRender } from 'markstream-vue'
import { Close, CopyDocument, Plus, Promotion, RefreshRight, VideoPause } from '@element-plus/icons-vue'
import type { ModelOption } from '@/api/playground/types'
import type { ComparisonColumn } from '@/composables/playground/useModelComparison'

interface Props {
  models: ModelOption[]
  columns: ComparisonColumn[]
  selectedModels: string[]
  isComparing: boolean
  prompt: string
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:prompt': [value: string]
  'add-column': [model: string]
  'remove-column': [model: string]
  'run': []
  'abort': []
  'retry-column': [id: string]
}>()

const { t } = useI18n()

// 可选模型（排除已选）
const availableModels = computed(() =>
  props.models.filter((m) => !props.selectedModels.includes(m.value))
)

const addModelValue = ref('')

function handleAddModel(): void {
  if (!addModelValue.value) return
  emit('add-column', addModelValue.value)
  addModelValue.value = ''
}

function handleRun(): void {
  if (props.selectedModels.length === 0) return
  ElNotification.info({
    title: t('playground.comparison.costTitle'),
    message: t('playground.comparison.costMsg', { n: props.selectedModels.length }),
    duration: 3000,
    position: 'bottom-right'
  })
  emit('run')
}

async function handleCopy(content: string): Promise<void> {
  if (!content) return
  try {
    await navigator.clipboard.writeText(content)
    ElMessage.success(t('playground.message.copied'))
  } catch {
    ElMessage.error(t('playground.message.copyFailed'))
  }
}

function formatUsage(usage: ComparisonColumn['usage']): string {
  if (!usage) return ''
  const parts: string[] = [`${usage.total_tokens} tokens`]
  if (usage.prompt_tokens && usage.completion_tokens) {
    parts.push(`${usage.prompt_tokens}/${usage.completion_tokens}`)
  }
  return parts.join(' · ')
}
</script>

<template>
  <div class="comparison-view">
    <!-- 模型选择栏 -->
    <div class="comparison-view__header">
      <el-select
        v-model="addModelValue"
        :placeholder="t('playground.comparison.selectModel')"
        size="small"
        filterable
        class="comparison-view__select"
        :disabled="selectedModels.length >= 4"
      >
        <el-option
          v-for="m in availableModels"
          :key="m.value"
          :label="m.label"
          :value="m.value"
        />
      </el-select>
      <el-button
        size="small"
        :icon="Plus"
        :disabled="!addModelValue || selectedModels.length >= 4"
        @click="handleAddModel"
      >
        {{ t('playground.comparison.add') }}
      </el-button>
    </div>

    <!-- 对比列 -->
    <div
      v-if="columns.length > 0"
      class="comparison-view__columns"
    >
      <div
        v-for="col in columns"
        :key="col.id"
        class="comparison-view__column"
      >
        <div class="comparison-view__column-header">
          <span class="comparison-view__column-name">{{ col.model }}</span>
          <button
            type="button"
            class="comparison-view__column-close"
            :disabled="isComparing"
            @click="emit('remove-column', col.model)"
          >
            <el-icon><Close /></el-icon>
          </button>
        </div>

        <div class="comparison-view__column-body">
          <!-- 推理过程（流式 thinking / 完成 end） -->
          <Thinking
            v-if="col.reasoning && (col.status === 'streaming' || col.status === 'done')"
            :content="col.reasoning"
            :status="col.status === 'streaming' ? 'thinking' : 'end'"
            :auto-collapse="col.status === 'done'"
          />

          <!-- 正文 / 错误 / 空闲 -->
          <template v-if="col.status === 'streaming' || col.status === 'done'">
            <MarkdownRender
              v-if="col.content"
              :content="col.content"
              :final="col.status === 'done'"
              mode="chat"
              :code-block-props="{ showCopyButton: true, showHeader: true }"
              class="comparison-view__markdown"
            />
            <!-- 流式光标 -->
            <span
              v-if="col.status === 'streaming' && col.content"
              class="comparison-view__cursor"
            >▍</span>
            <!-- 无内容 loading -->
            <div
              v-if="col.status === 'streaming' && !col.content && !col.reasoning"
              class="comparison-view__loading"
            >
              <span class="comparison-view__loading-dot" />
              <span class="comparison-view__loading-dot" />
              <span class="comparison-view__loading-dot" />
            </div>
          </template>

          <!-- 错误态 + 重试 -->
          <div
            v-else-if="col.status === 'error'"
            class="comparison-view__error"
          >
            <i class="i-ep-warning-filled" />
            <span class="comparison-view__error-text">{{ col.error }}</span>
            <el-button
              size="small"
              text
              :icon="RefreshRight"
              @click="emit('retry-column', col.id)"
            >
              {{ t('common.retry') }}
            </el-button>
          </div>

          <!-- 空闲态 -->
          <div
            v-else
            class="comparison-view__idle"
          >
            {{ t('playground.comparison.waiting') }}
          </div>
        </div>

        <!-- 底部工具栏：usage + 复制 -->
        <div
          v-if="col.status === 'done'"
          class="comparison-view__column-footer"
        >
          <span
            v-if="col.usage"
            class="comparison-view__usage"
          >
            {{ formatUsage(col.usage) }}
          </span>
          <button
            type="button"
            class="comparison-view__action-btn"
            :title="t('playground.actions.copy')"
            @click="handleCopy(col.content)"
          >
            <el-icon><CopyDocument /></el-icon>
          </button>
        </div>
      </div>
    </div>

    <!-- 空态 -->
    <div
      v-else
      class="comparison-view__empty"
    >
      <i class="i-ep-data-analysis comparison-view__empty-icon" />
      <p class="comparison-view__empty-text">
        {{ t('playground.comparison.empty') }}
      </p>
    </div>

    <!-- 底部输入 -->
    <div class="comparison-view__footer">
      <el-input
        :model-value="prompt"
        type="textarea"
        :rows="2"
        :placeholder="t('playground.comparison.promptPlaceholder')"
        resize="none"
        :disabled="isComparing"
        @update:model-value="(v: string) => emit('update:prompt', v)"
      />
      <div class="comparison-view__footer-actions">
        <el-button
          v-if="!isComparing"
          type="primary"
          size="small"
          :icon="Promotion"
          :disabled="!prompt.trim() || selectedModels.length === 0"
          @click="handleRun"
        >
          {{ t('playground.comparison.run') }}
        </el-button>
        <el-button
          v-else
          type="danger"
          size="small"
          :icon="VideoPause"
          @click="emit('abort')"
        >
          {{ t('playground.actions.stop') }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.comparison-view {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;

  &__header {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
    padding: var(--ys-spacing-2) var(--ys-spacing-4);
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  &__select {
    width: 240px;
  }

  &__columns {
    display: flex;
    flex: 1;
    gap: var(--ys-spacing-2);
    min-height: 0;
    padding: var(--ys-spacing-2);
    overflow-x: auto;
  }

  &__column {
    display: flex;
    flex: 1;
    flex-direction: column;
    min-width: 240px;
    max-width: 50%;
    background: var(--el-fill-color-blank);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: var(--ys-radius-md);
  }

  &__column-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: var(--ys-spacing-2) var(--ys-spacing-3);
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  &__column-name {
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: var(--ys-font-size-sm);
    font-weight: 600;
    color: var(--el-text-color-primary);
    white-space: nowrap;
  }

  &__column-close {
    display: flex;
    flex-shrink: 0;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    padding: 0;
    color: var(--el-text-color-placeholder);
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
      color: var(--el-color-danger);
      background: var(--el-color-danger-light-9);
    }
  }

  &__column-body {
    flex: 1;
    min-height: 0;
    padding: var(--ys-spacing-3);
    overflow-y: auto;
  }

  &__markdown {
    font-size: var(--ys-font-size-sm);
    line-height: 1.6;
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
    animation: comparison-loading-bounce 1.4s ease-in-out infinite both;

    &:nth-child(1) { animation-delay: -0.32s; }
    &:nth-child(2) { animation-delay: -0.16s; }
  }

  &__error {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-1);
    align-items: center;
    font-size: var(--ys-font-size-sm);
    color: var(--el-color-danger);
  }

  &__error-text {
    flex: 1;
    min-width: 0;
    word-break: break-word;
  }

  &__idle {
    font-size: var(--ys-font-size-sm);
    color: var(--el-text-color-placeholder);
  }

  &__cursor {
    display: inline-block;
    color: var(--el-color-primary);
    animation: comparison-cursor-blink 1s step-end infinite;
  }

  &__column-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: var(--ys-spacing-1) var(--ys-spacing-3) var(--ys-spacing-2);
    border-top: 1px solid var(--el-border-color-lighter);
  }

  &__usage {
    font-size: 11px;
    color: var(--el-text-color-placeholder);
  }

  &__action-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    padding: 0;
    color: var(--el-text-color-placeholder);
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

  &__empty {
    display: flex;
    flex: 1;
    flex-direction: column;
    gap: var(--ys-spacing-2);
    align-items: center;
    justify-content: center;
  }

  &__empty-icon {
    font-size: 48px;
    color: var(--el-text-color-placeholder);
  }

  &__empty-text {
    margin: 0;
    font-size: var(--ys-font-size-base);
    color: var(--el-text-color-secondary);
  }

  &__footer {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-2);
    padding: var(--ys-spacing-2) var(--ys-spacing-4) var(--ys-spacing-4);
    border-top: 1px solid var(--el-border-color-lighter);
  }

  &__footer-actions {
    display: flex;
    justify-content: flex-end;
  }

  // Thinking 组件视觉与单对话模式一致
  :deep(.elx-thinking) {
    --elx-thinking-content-wrapper-width: 100%;
    --elx-thinking-content-wrapper-background-color: color-mix(in srgb, var(--ys-color-secondary) 6%, transparent);
    --elx-thinking-content-wrapper-color: var(--el-text-color-regular);
  }

  :deep(.elx-thinking__content pre) {
    white-space: pre-wrap;
    font-style: italic;
  }
}

@keyframes comparison-loading-bounce {
  0%, 80%, 100% {
    transform: scale(0);
  }

  40% {
    transform: scale(1);
  }
}

@keyframes comparison-cursor-blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}
</style>
