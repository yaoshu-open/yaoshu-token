<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  Connection,
  Histogram,
  VideoCamera,
  Document,
  Memo,
  MagicStick,
  Setting,
  EditPen,
  Place,
  Promotion,
  Box,
  Reading
} from '@element-plus/icons-vue'
import type { ModelCapability } from '@/api/pricing/types'

const props = defineProps<{
  capabilities: ModelCapability[]
}>()

const { t } = useI18n()

const CAPABILITY_META: Record<ModelCapability, { icon: any; labelKey: string; descKey: string }> = {
  function_calling: { icon: Connection, labelKey: 'pricing.cap.functionCalling', descKey: 'pricing.cap.functionCallingDesc' },
  streaming: { icon: Histogram, labelKey: 'pricing.cap.streaming', descKey: 'pricing.cap.streamingDesc' },
  vision: { icon: VideoCamera, labelKey: 'pricing.cap.vision', descKey: 'pricing.cap.visionDesc' },
  json_mode: { icon: Memo, labelKey: 'pricing.cap.jsonMode', descKey: 'pricing.cap.jsonModeDesc' },
  structured_output: { icon: Document, labelKey: 'pricing.cap.structuredOutput', descKey: 'pricing.cap.structuredOutputDesc' },
  reasoning: { icon: MagicStick, labelKey: 'pricing.cap.reasoning', descKey: 'pricing.cap.reasoningDesc' },
  tools: { icon: Setting, labelKey: 'pricing.cap.tools', descKey: 'pricing.cap.toolsDesc' },
  system_prompt: { icon: EditPen, labelKey: 'pricing.cap.systemPrompt', descKey: 'pricing.cap.systemPromptDesc' },
  web_search: { icon: Place, labelKey: 'pricing.cap.webSearch', descKey: 'pricing.cap.webSearchDesc' },
  code_interpreter: { icon: Promotion, labelKey: 'pricing.cap.codeInterpreter', descKey: 'pricing.cap.codeInterpreterDesc' },
  caching: { icon: Box, labelKey: 'pricing.cap.caching', descKey: 'pricing.cap.cachingDesc' },
  embeddings: { icon: Reading, labelKey: 'pricing.cap.embeddings', descKey: 'pricing.cap.embeddingsDesc' }
}

// 引用所有图标确保 TS 不报 unused（通过 :is 动态引用 vue-tsc 无法检测）
const _icons = { Connection, Histogram, VideoCamera, Document, Memo, MagicStick, Setting, EditPen, Place, Promotion, Box, Reading }
void _icons

const CAPABILITY_ORDER: ModelCapability[] = [
  'streaming', 'function_calling', 'tools', 'json_mode', 'structured_output',
  'vision', 'reasoning', 'caching', 'system_prompt', 'web_search',
  'code_interpreter', 'embeddings'
]

const ordered = computed(() => {
  const set = new Set(props.capabilities)
  const result = CAPABILITY_ORDER.filter((c) => set.has(c))
  for (const c of props.capabilities) {
    if (!result.includes(c)) result.push(c)
  }
  return result
})
</script>

<template>
  <p
    v-if="ordered.length === 0"
    class="capabilities-empty"
  >
    {{ t('pricing.noCapabilities') }}
  </p>
  <div
    v-else
    class="capabilities"
  >
    <div
      v-for="capability in ordered"
      :key="capability"
      class="capabilities__item"
    >
      <span class="capabilities__icon">
        <el-icon :size="14"><component :is="CAPABILITY_META[capability].icon" /></el-icon>
      </span>
      <div class="capabilities__content">
        <div class="capabilities__label">
          {{ t(CAPABILITY_META[capability].labelKey) }}
        </div>
        <p class="capabilities__desc">
          {{ t(CAPABILITY_META[capability].descKey) }}
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.capabilities {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--ys-spacing-2);

  @media (width >= 768px) {
    grid-template-columns: repeat(3, 1fr);
  }

  @media (width >= 1280px) {
    grid-template-columns: repeat(4, 1fr);
  }

  &__item {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: flex-start;
    padding: var(--ys-spacing-3);
    border: 1px solid var(--el-border-color);
    border-radius: var(--ys-radius-md);
    transition: background 0.15s;

    &:hover {
      background: var(--el-fill-color-light);
    }
  }

  &__icon {
    display: inline-flex;
    flex-shrink: 0;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    background: var(--el-fill-color);
    border-radius: var(--ys-radius-base);
  }

  &__label {
    font-size: var(--ys-font-size-xs);
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  &__desc {
    margin: 2px 0 0;
    font-size: 11px;
    line-height: 1.4;
    color: var(--el-text-color-secondary);
  }
}

.capabilities-empty {
  font-size: var(--ys-font-size-base);
  color: var(--el-text-color-secondary);
}
</style>
