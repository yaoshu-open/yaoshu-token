<script setup lang="ts">
/**
 * MJ Prompt / PromptEn / 失败原因全文查看对话框。
 */
import { ElDialog } from 'element-plus'

defineProps<{
  modelValue: boolean
  content: string
  title: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    :title="title"
    width="600px"
    align-center
    @update:model-value="emit('update:modelValue', $event)"
  >
    <pre class="mj-prompt-dialog__content">{{ content }}</pre>
  </ElDialog>
</template>

<style scoped>
.mj-prompt-dialog__content {
  max-height: 50vh;
  padding: var(--ys-spacing-3);
  margin: 0;
  overflow: auto;
  font-family: var(--el-font-family-mono, monospace);
  font-size: var(--ys-font-size-sm);
  line-height: 1.6;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  background: var(--el-fill-color-light);
  border-radius: var(--ys-radius-base);
}
</style>
