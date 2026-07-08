<script setup lang="ts">
/**
 * ConversationToolbar — 对话操作栏（重新生成 / 清空 / 导出 / 流式开关）。
 * 从原 ai-playground.vue 工具栏拆出对话相关操作。
 */
import { RefreshRight, Delete, Download } from '@element-plus/icons-vue'
import ContextUsageIndicator from './ContextUsageIndicator.vue'

interface Props {
  isGenerating: boolean
  hasMessages: boolean
  streamEnabled: boolean
  contextTokens?: number
  maxContext?: number
}

defineProps<Props>()
const emit = defineEmits<{
  regenerate: []
  clear: []
  export: []
  'update:stream': [value: boolean]
}>()
</script>

<template>
  <div class="conversation-toolbar">
    <div class="conversation-toolbar__actions">
      <el-tooltip
        :content="$t('playground.actions.regenerate')"
        placement="bottom"
      >
        <el-button
          size="small"
          :icon="RefreshRight"
          :disabled="isGenerating || !hasMessages"
          @click="emit('regenerate')"
        />
      </el-tooltip>

      <el-tooltip
        :content="$t('playground.toolbar.clear')"
        placement="bottom"
      >
        <el-button
          size="small"
          type="danger"
          plain
          :icon="Delete"
          :disabled="isGenerating || !hasMessages"
          @click="emit('clear')"
        />
      </el-tooltip>

      <el-tooltip
        :content="$t('playground.toolbar.export')"
        placement="bottom"
      >
        <el-button
          size="small"
          :icon="Download"
          :disabled="!hasMessages"
          @click="emit('export')"
        />
      </el-tooltip>
    </div>

    <div class="conversation-toolbar__meta">
      <el-tooltip
        :content="streamEnabled ? $t('playground.toolbar.streamOutput') : $t('playground.toolbar.standard')"
        placement="bottom"
      >
        <el-switch
          :model-value="streamEnabled"
          size="small"
          inline-prompt
          :active-text="$t('playground.toolbar.streamOutput')"
          :inactive-text="$t('playground.toolbar.standard')"
          @update:model-value="(v: string | number | boolean) => emit('update:stream', !!v)"
        />
      </el-tooltip>
      <ContextUsageIndicator
        :tokens="contextTokens"
        :max-context="maxContext"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.conversation-toolbar {
  display: flex;
  flex-shrink: 0;
  gap: var(--ys-spacing-2);
  align-items: center;
  justify-content: space-between;
  padding: var(--ys-spacing-2) var(--ys-spacing-4);
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);

  &__actions {
    display: flex;
    gap: var(--ys-spacing-1);
    align-items: center;
  }

  &__meta {
    display: flex;
    gap: var(--ys-spacing-3);
    align-items: center;
  }
}
</style>
