<script setup lang="ts">
/**
 * ComparisonView — 多模型并行对比视图。
 *
 * N 列并排流式输出（max 4），每列独立状态/内容/usage。
 * 底部共用 prompt 输入 + 运行/停止按钮。
 */
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElNotification } from 'element-plus'
import { MarkdownRender } from 'markstream-vue'
import { Close, Plus, Promotion, VideoPause } from '@element-plus/icons-vue'
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

function formatUsage(usage: ComparisonColumn['usage']): string {
  if (!usage) return ''
  return `${usage.total_tokens} tokens`
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
          <!-- 流式中 -->
          <template v-if="col.status === 'streaming' || col.status === 'done'">
            <MarkdownRender
              v-if="col.content"
              :content="col.content"
              :final="col.status === 'done'"
              mode="chat"
              class="comparison-view__markdown"
            />
            <div
              v-else-if="col.status === 'streaming'"
              class="comparison-view__loading"
            >
              <span class="comparison-view__loading-dot" />
              <span class="comparison-view__loading-dot" />
              <span class="comparison-view__loading-dot" />
            </div>
          </template>

          <!-- 错误态 -->
          <div
            v-else-if="col.status === 'error'"
            class="comparison-view__error"
          >
            <i class="i-ep-warning-filled" />
            <span>{{ col.error }}</span>
          </div>

          <!-- 空闲态 -->
          <div
            v-else
            class="comparison-view__idle"
          >
            {{ t('playground.comparison.waiting') }}
          </div>
        </div>

        <!-- 底部 usage -->
        <div
          v-if="col.usage && col.status === 'done'"
          class="comparison-view__usage"
        >
          {{ formatUsage(col.usage) }}
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
    gap: var(--ys-spacing-1);
    align-items: center;
    font-size: var(--ys-font-size-sm);
    color: var(--el-color-danger);
  }

  &__idle {
    font-size: var(--ys-font-size-sm);
    color: var(--el-text-color-placeholder);
  }

  &__usage {
    padding: var(--ys-spacing-1) var(--ys-spacing-3) var(--ys-spacing-2);
    font-size: 11px;
    color: var(--el-text-color-placeholder);
    border-top: 1px solid var(--el-border-color-lighter);
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
}

@keyframes comparison-loading-bounce {
  0%, 80%, 100% {
    transform: scale(0);
  }

  40% {
    transform: scale(1);
  }
}
</style>
