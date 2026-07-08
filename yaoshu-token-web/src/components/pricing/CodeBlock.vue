<script setup lang="ts">
// 代码块组件（markstream-vue 升级版）。
// 使用 markstream-vue MarkdownRender 渲染代码块，获得 Shiki 语法高亮。
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { CopyDocument } from '@element-plus/icons-vue'
import { MarkdownRender } from 'markstream-vue'

const props = withDefaults(defineProps<{
  code: string
  language?: string
}>(), {
  language: 'bash'
})

const { t } = useI18n()

// 包装为 Markdown 代码块格式，供 MarkdownRender 渲染
const markdownContent = computed(() => {
  return '```' + props.language + '\n' + props.code + '\n```'
})

async function handleCopy() {
  try {
    await navigator.clipboard.writeText(props.code)
    ElMessage.success(t('common.copySuccess'))
  } catch {
    ElMessage.error(t('common.copyFailed'))
  }
}
</script>

<template>
  <div class="code-block">
    <div class="code-block__header">
      <span class="code-block__lang">{{ language }}</span>
      <button
        class="code-block__copy"
        title="Copy"
        @click="handleCopy"
      >
        <el-icon><CopyDocument /></el-icon>
      </button>
    </div>
    <div class="code-block__body">
      <MarkdownRender
        :content="markdownContent"
        :final="true"
        mode="docs"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.code-block {
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  border-radius: var(--ys-radius-md);

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 6px var(--ys-spacing-3);
    background: var(--el-fill-color-light);
    border-bottom: 1px solid var(--el-border-color);
  }

  &__lang {
    font-size: 11px;
    font-weight: 500;
    color: var(--el-text-color-secondary);
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }

  &__copy {
    display: inline-flex;
    align-items: center;
    padding: 2px;
    color: var(--el-text-color-secondary);
    cursor: pointer;
    background: transparent;
    border: none;

    &:hover {
      color: var(--el-color-primary);
    }
  }

  &__body {
    :deep(.shiki) {
      padding: var(--ys-spacing-3);
      margin: 0;
      overflow-x: auto;
      font-family: 'SF Mono', 'Fira Code', Consolas, monospace;
      font-size: var(--ys-font-size-sm);
      line-height: 1.5;
      background: var(--el-bg-color-page) !important;
    }

    :deep(pre) {
      margin: 0;
    }
  }
}
</style>
