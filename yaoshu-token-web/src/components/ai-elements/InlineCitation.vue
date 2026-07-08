<script setup lang="ts">
/**
 * InlineCitation — 行内引用标记（上标数字 + hover 弹窗显示来源）。
 * 用于 AI 回复中标注信息来源链接。
 */
import { ref } from 'vue'
import type { SourceRef } from './SourcesPanel.vue'

defineProps<{
  /** 引用序号 */
  index: number
  /** 来源信息 */
  source: SourceRef
}>()

const showPopover = ref(false)
</script>

<template>
  <span
    class="inline-citation"
    @mouseenter="showPopover = true"
    @mouseleave="showPopover = false"
  >
    <sup class="inline-citation__badge">{{ index + 1 }}</sup>
    <Transition name="inline-citation-pop">
      <span
        v-if="showPopover"
        class="inline-citation__popover"
      >
        <a
          :href="source.href"
          target="_blank"
          rel="noopener noreferrer"
          class="inline-citation__link"
        >
          {{ source.title || source.href }}
        </a>
      </span>
    </Transition>
  </span>
</template>

<style scoped lang="scss">
.inline-citation {
  position: relative;
  display: inline-flex;
  cursor: pointer;

  &__badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 16px;
    height: 16px;
    padding: 0 var(--ys-spacing-1);
    margin: 0 1px;
    font-size: 10px;
    font-weight: 600;
    color: var(--el-color-primary);
    cursor: pointer;
    background: var(--el-color-primary-light-9);
    border-radius: var(--ys-radius-sm);
    transition: background 0.2s;

    &:hover {
      background: var(--el-color-primary-light-7);
    }
  }

  &__popover {
    position: absolute;
    bottom: calc(100% + 4px);
    left: 50%;
    z-index: 10;
    max-width: 280px;
    padding: 6px 10px;
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: var(--ys-font-size-xs);
    white-space: nowrap;
    background: var(--el-bg-color-overlay);
    border: 1px solid var(--el-border-color);
    border-radius: var(--ys-radius-base);
    box-shadow: var(--el-box-shadow-light);
    transform: translateX(-50%);
  }

  &__link {
    color: var(--el-color-primary);
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }
}

.inline-citation-pop-enter-active,
.inline-citation-pop-leave-active {
  transition: opacity 0.15s, transform 0.15s;
}

.inline-citation-pop-enter-from,
.inline-citation-pop-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(4px);
}
</style>
