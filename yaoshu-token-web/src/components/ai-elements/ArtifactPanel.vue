<script setup lang="ts">
/**
 * ArtifactPanel — 代码制品面板（代码 + 预览双栏）。
 * 用于 AI 生成的 HTML/SVG 制品的预览展示。
 */
import { ref, computed } from 'vue'
import { MarkdownRender } from 'markstream-vue'
import { Monitor, Document } from '@element-plus/icons-vue'

const props = defineProps<{
  /** 制品内容（代码文本） */
  content: string
  /** 制品类型 */
  type?: 'html' | 'svg' | 'code'
  /** 语言标签 */
  language?: string
  /** 标题 */
  title?: string
}>()

type Tab = 'preview' | 'code'
const activeTab = ref<Tab>('preview')

const isHtml = computed(() => props.type === 'html' || props.language === 'html')
const isSvg = computed(() => props.type === 'svg' || props.language === 'svg')

const previewSrc = computed(() => {
  if (isHtml.value) {
    return props.content
  }
  if (isSvg.value) {
    return props.content
  }
  return ''
})

const canPreview = computed(() => isHtml.value || isSvg.value)

const markdownContent = computed(() => {
  const lang = props.language || 'html'
  return '```' + lang + '\n' + props.content + '\n```'
})
</script>

<template>
  <div class="artifact-panel">
    <div class="artifact-panel__header">
      <span class="artifact-panel__title">{{ title || 'Artifact' }}</span>
      <div
        v-if="canPreview"
        class="artifact-panel__tabs"
      >
        <button
          class="artifact-panel__tab"
          :class="{ 'artifact-panel__tab--active': activeTab === 'preview' }"
          @click="activeTab = 'preview'"
        >
          <el-icon><Monitor /></el-icon>
          预览
        </button>
        <button
          class="artifact-panel__tab"
          :class="{ 'artifact-panel__tab--active': activeTab === 'code' }"
          @click="activeTab = 'code'"
        >
          <el-icon><Document /></el-icon>
          代码
        </button>
      </div>
    </div>

    <div class="artifact-panel__body">
      <!-- HTML 预览（iframe 沙箱） -->
      <iframe
        v-if="canPreview && activeTab === 'preview' && isHtml"
        class="artifact-panel__preview"
        :srcdoc="previewSrc"
        sandbox="allow-scripts"
      />
      <!-- SVG 预览 -->
      <div
        v-else-if="canPreview && activeTab === 'preview' && isSvg"
        class="artifact-panel__svg"
        v-html="previewSrc"
      />
      <!-- 代码视图 -->
      <div
        v-else
        class="artifact-panel__code"
      >
        <MarkdownRender
          :content="markdownContent"
          :final="true"
          mode="docs"
        />
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.artifact-panel {
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  border-radius: var(--ys-radius-md);

  &__header {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
    justify-content: space-between;
    padding: 6px var(--ys-spacing-3);
    background: var(--el-fill-color-light);
    border-bottom: 1px solid var(--el-border-color);
  }

  &__title {
    font-size: var(--ys-font-size-xs);
    font-weight: 600;
    color: var(--el-text-color-secondary);
  }

  &__tabs {
    display: flex;
    gap: 2px;
  }

  &__tab {
    display: inline-flex;
    gap: var(--ys-spacing-1);
    align-items: center;
    padding: 2px var(--ys-spacing-2);
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-secondary);
    cursor: pointer;
    background: transparent;
    border: 1px solid transparent;
    border-radius: var(--ys-radius-sm);
    transition: all 0.2s;

    &--active {
      color: var(--el-color-primary);
      background: var(--el-color-primary-light-9);
      border-color: var(--el-color-primary-light-7);
    }
  }

  &__body {
    min-height: 200px;
    max-height: 500px;
    overflow: auto;
  }

  &__preview {
    width: 100%;
    min-height: 200px;
    border: none;
  }

  &__svg {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: var(--ys-spacing-4);
  }

  &__code {
    :deep(.shiki) {
      padding: var(--ys-spacing-3);
      margin: 0;
      overflow-x: auto;
      font-size: var(--ys-font-size-sm);
      line-height: 1.5;
      background: var(--el-bg-color-page) !important;
    }
  }
}
</style>
