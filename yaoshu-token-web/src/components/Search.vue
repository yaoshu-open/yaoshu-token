<script setup lang="ts">
import { ElTooltip, ElInput } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useCommandMenu } from '@/composables/useCommandMenu'

defineProps<{
  placeholder?: string
}>()

const emit = defineEmits<{
  (e: 'search', keyword: string): void
}>()

const { t } = useI18n()
const { open } = useCommandMenu()

// 命令面板接管 Cmd/K 后，Search 降级为触发器：点击/回车唤起命令面板
function handleTrigger(): void {
  open()
  // 保留 search emit 契约（空串占位，不破坏 AppHeader 接线）
  emit('search', '')
}
</script>

<template>
  <ElTooltip
    :content="t('layout.header.search')"
    placement="bottom"
  >
    <div
      class="search"
      @click="handleTrigger"
    >
      <ElInput
        class="search__input"
        :placeholder="t('layout.header.searchPlaceholder')"
        size="small"
        readonly
        @keyup.enter="handleTrigger"
      >
        <template #prefix>
          <i class="i-ep-search" />
        </template>
      </ElInput>
      <!-- 快捷键提示 -->
      <kbd class="search__kbd">⌘K</kbd>
    </div>
  </ElTooltip>
</template>

<style scoped lang="scss">
.search {
  display: inline-flex;
  gap: var(--ys-spacing-1);
  align-items: center;
  width: 200px;
  cursor: pointer;

  @media (width <= 768px) {
    display: none;
  }

  &__input {
    width: 100%;
    pointer-events: none;
  }

  &__kbd {
    padding: 2px 6px;
    font-size: var(--el-font-size-extra-small);
    color: var(--el-text-color-secondary);
    pointer-events: none;
    background: var(--el-fill-color-light);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: var(--el-border-radius-small);
  }
}
</style>
