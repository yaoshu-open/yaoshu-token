<script setup lang="ts">
/**
 * SourcesPanel — 来源列表面板（消息底部展示所有引用来源）。
 * 与 InlineCitation 配合使用，展示完整来源列表。
 */
import { Link } from '@element-plus/icons-vue'

export interface SourceRef {
  href: string
  title?: string
}

defineProps<{
  sources: SourceRef[]
}>()
</script>

<template>
  <div
    v-if="sources.length > 0"
    class="sources-panel"
  >
    <div class="sources-panel__title">
      来源
    </div>
    <div class="sources-panel__list">
      <a
        v-for="(source, index) in sources"
        :key="index"
        :href="source.href"
        target="_blank"
        rel="noopener noreferrer"
        class="sources-panel__item"
      >
        <span class="sources-panel__index">{{ index + 1 }}</span>
        <span class="sources-panel__text">{{ source.title || source.href }}</span>
        <el-icon class="sources-panel__icon"><Link /></el-icon>
      </a>
    </div>
  </div>
</template>

<style scoped lang="scss">
.sources-panel {
  padding: var(--ys-spacing-2) var(--ys-spacing-3);
  margin-top: 8px;
  background: var(--el-fill-color-light);
  border-radius: var(--ys-radius-md);

  &__title {
    margin-bottom: 6px;
    font-size: var(--ys-font-size-xs);
    font-weight: 600;
    color: var(--el-text-color-secondary);
  }

  &__list {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-1);
  }

  &__item {
    display: flex;
    gap: 6px;
    align-items: center;
    padding: var(--ys-spacing-1) 6px;
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-primary);
    text-decoration: none;
    border-radius: var(--ys-radius-sm);
    transition: background 0.2s;

    &:hover {
      background: var(--el-fill-color);
    }
  }

  &__index {
    flex-shrink: 0;
    min-width: 16px;
    height: 16px;
    font-size: 10px;
    font-weight: 600;
    line-height: 16px;
    color: var(--el-color-primary);
    text-align: center;
    background: var(--el-color-primary-light-9);
    border-radius: var(--ys-radius-sm);
  }

  &__text {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__icon {
    flex-shrink: 0;
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-placeholder);
  }
}
</style>
