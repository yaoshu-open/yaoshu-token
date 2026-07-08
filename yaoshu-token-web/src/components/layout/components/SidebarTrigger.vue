<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useThemeStore } from '@/store/modules/theme'
import { useMobile } from '@/composables/useMobile'

interface Props {
  variant?: 'ghost' | 'default'
  size?: 'small' | 'default' | 'large'
}

withDefaults(defineProps<Props>(), {
  variant: 'ghost',
  size: 'default'
})

const { t } = useI18n()
const themeStore = useThemeStore()
const isMobile = useMobile()

// 桌面端：collapsible='none'/'offcanvas' 时禁用 trigger；移动端：始终可触发 drawer
const disabled = computed(() => {
  if (isMobile.value) return false
  return (
    themeStore.sidebarCollapsible === 'none' ||
    themeStore.sidebarCollapsible === 'offcanvas'
  )
})

// 移动端显示菜单图标；桌面端按折叠态切换展开/收起图标
const iconClass = computed(() => {
  if (isMobile.value) return 'i-ep-menu'
  return themeStore.sidebarCollapsed ? 'i-ep-expand' : 'i-ep-fold'
})

const label = computed(() =>
  isMobile.value
    ? t('layout.sidebar.menu')
    : themeStore.sidebarCollapsed
      ? t('layout.sidebar.expand')
      : t('layout.sidebar.collapse')
)

function toggle() {
  if (isMobile.value) {
    themeStore.toggleMobileSidebar()
  } else {
    themeStore.toggleSidebar()
  }
}
</script>

<template>
  <button
    type="button"
    class="sidebar-trigger"
    :class="[
      `sidebar-trigger--variant-${variant}`,
      `sidebar-trigger--size-${size}`
    ]"
    :disabled="disabled"
    :aria-label="label"
    :title="label"
    @click="toggle"
  >
    <i :class="iconClass" />
  </button>
</template>

<style scoped lang="scss">
.sidebar-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-regular);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--el-border-radius-base);
  transition: background-color 0.2s, color 0.2s;

  &:disabled {
    pointer-events: none;
    opacity: 0;
  }

  &:hover:not(:disabled) {
    color: var(--el-text-color-primary);
    background: var(--el-fill-color-light);
  }

  &--variant-ghost {
    background: transparent;

    &:hover:not(:disabled) {
      background: var(--el-fill-color-light);
    }
  }

  &--variant-default {
    background: var(--el-fill-color-light);

    &:hover:not(:disabled) {
      background: var(--el-fill-color);
    }
  }

  &--size-small {
    width: 28px;
    height: 28px;
    font-size: var(--ys-font-size-lg);
  }

  &--size-default {
    width: 32px;
    height: 32px;
    font-size: 18px;
  }

  &--size-large {
    width: 36px;
    height: 36px;
    font-size: var(--ys-font-size-xl);
  }
}
</style>
