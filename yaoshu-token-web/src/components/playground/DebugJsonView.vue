<script setup lang="ts">
/**
 * DebugJsonView - 通用 JSON 展示（Props.mode='preview'|'actual'）。
 * 简单格式化 + 等宽字体 + 横向滚动。
 */
import { computed } from 'vue'
import { Document } from '@element-plus/icons-vue'

interface Props {
  data: unknown
  mode: 'preview' | 'actual'
}

const props = defineProps<Props>()

const formatted = computed<string>(() => {
  if (props.data === null || props.data === undefined) return '（空）'
  try {
    return JSON.stringify(props.data, null, 2)
  } catch {
    return String(props.data)
  }
})

const hasData = computed(() => {
  if (props.data === null || props.data === undefined) return false
  if (typeof props.data === 'object' && Object.keys(props.data).length === 0) return false
  return true
})
</script>

<template>
  <div class="debug-json-view">
    <div
      v-if="!hasData"
      class="debug-json-view__empty"
    >
      <el-icon><Document /></el-icon>
      <span>{{ $t('playground.debug.empty') }}</span>
    </div>
    <pre
      v-else
      class="debug-json-view__pre"
    ><code>{{ formatted }}</code></pre>
  </div>
</template>

<style scoped lang="scss">
.debug-json-view {
  height: 100%;

  &__empty {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-2);
    align-items: center;
    justify-content: center;
    height: 100%;
    font-size: var(--ys-font-size-sm);
    color: var(--el-text-color-secondary);
  }

  &__pre {
    height: 100%;
    padding: var(--ys-spacing-3) var(--ys-spacing-4);
    margin: 0;
    overflow: auto;
    font-family: SFMono-Regular, Consolas, 'Liberation Mono', Menlo, monospace;
    font-size: var(--ys-font-size-xs);
    line-height: 1.5;
    color: #d4d4d4;
    background: #1e1e1e;
    border-radius: var(--ys-radius-base);
  }
}
</style>
