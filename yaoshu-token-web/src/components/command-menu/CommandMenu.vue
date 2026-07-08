<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Command } from 'cmdk-vue'
import { useCommandMenu } from '@/composables/useCommandMenu'
import { useSidebarData } from '@/composables/useSidebarData'
import { useThemeStore } from '@/store/modules/theme'
import { getNavGroupsForPath } from '@/components/layout/lib/sidebar-view-registry'
import type { NavGroup, NavItem } from '@/components/layout/types'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { isOpen, close } = useCommandMenu()
const sidebarData = useSidebarData()
const themeStore = useThemeStore()

// 导航数据源：当前嵌套视图优先，否则取 root navGroups
const navGroups = computed<NavGroup[]>(
  () => getNavGroupsForPath(route.path, t) ?? sidebarData.value.navGroups
)

// 主题动作配置
const themeActions = computed(() => [
  { mode: 'light' as const, label: t('command.theme.light'), icon: 'i-ep-sunny' },
  { mode: 'dark' as const, label: t('command.theme.dark'), icon: 'i-ep-moon' },
  { mode: 'system' as const, label: t('command.theme.system'), icon: 'i-ep-monitor' }
])

function runCommand(command: () => unknown): void {
  close()
  command()
}

function navigateTo(url: string): void {
  runCommand(() => router.push(url))
}

function setTheme(mode: 'dark' | 'light' | 'system'): void {
  runCommand(() => themeStore.setMode(mode))
}

// 判断 NavItem 是否为可导航的叶子链接
function isNavLink(item: NavItem): boolean {
  return 'url' in item && typeof item.url === 'string'
}

// 判断 NavItem 是否为折叠组（有子项）
function isNavCollapsible(item: NavItem): boolean {
  return 'items' in item && Array.isArray(item.items)
}
</script>

<template>
  <Command.Dialog
    v-model:open="isOpen"
    :should-filter="true"
    class="cmd-menu"
    overlayClassName="cmd-menu__overlay"
    contentClassName="cmd-menu__content"
  >
    <Command.Input
      class="cmd-menu__input"
      :placeholder="t('command.placeholder')"
    />
    <Command.List class="cmd-menu__list">
      <Command.Empty class="cmd-menu__empty">
        {{ t('command.empty') }}
      </Command.Empty>

      <Command.Group
        v-for="group in navGroups"
        :key="group.id || group.title"
        :heading="group.title"
        class="cmd-menu__group"
      >
        <template
          v-for="(navItem, i) in group.items"
          :key="`${navItem.title}-${i}`"
        >
          <!-- 单链接项 -->
          <Command.Item
            v-if="isNavLink(navItem)"
            :value="navItem.title"
            class="cmd-menu__item"
            @select="() => navigateTo((navItem as { url: string }).url)"
          >
            <i class="i-ep-arrow-right cmd-menu__item-icon" />
            {{ navItem.title }}
          </Command.Item>
          <!-- 折叠组项：扁平化为 "parent > child" -->
          <Command.Item
            v-for="(subItem, si) in (isNavCollapsible(navItem) ? navItem.items : [])"
            :key="`${navItem.title}-${subItem.url}-${si}`"
            :value="`${navItem.title} ${subItem.title}`"
            class="cmd-menu__item"
            @select="() => navigateTo(subItem.url)"
          >
            <i class="i-ep-arrow-right cmd-menu__item-icon" />
            {{ navItem.title }}
            <i class="i-ep-arrow-right cmd-menu__item-sep" />
            {{ subItem.title }}
          </Command.Item>
        </template>
      </Command.Group>

      <Command.Separator class="cmd-menu__separator" />

      <Command.Group
        :heading="t('command.theme.title')"
        class="cmd-menu__group"
      >
        <Command.Item
          v-for="action in themeActions"
          :key="action.mode"
          :value="action.label"
          class="cmd-menu__item"
          @select="() => setTheme(action.mode)"
        >
          <i :class="[action.icon, 'cmd-menu__item-icon']" />
          {{ action.label }}
          <i
            v-if="themeStore.mode === action.mode"
            class="i-ep-check cmd-menu__item-check"
          />
        </Command.Item>
      </Command.Group>
    </Command.List>
  </Command.Dialog>
</template>

<style scoped lang="scss">
// cmdk-vue 为 unstyled，自写 CSS 对齐 Element Plus 主题变量
:global(.cmd-menu__overlay) {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: rgb(0 0 0 / 50%);
  backdrop-filter: blur(4px);
}

:global(.cmd-menu__content) {
  position: fixed;
  top: 10vh;
  left: 50%;
  z-index: 2001;
  width: 90vw;
  max-width: 480px;
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--ys-radius-lg);
  box-shadow: var(--el-box-shadow);
  transform: translateX(-50%);
}

.cmd-menu {
  display: flex;
  flex-direction: column;
}

.cmd-menu__input {
  width: 100%;
  padding: 14px var(--ys-spacing-4);
  font-size: 15px;
  color: var(--el-text-color-primary);
  outline: none;
  background: transparent;
  border: none;
  border-bottom: 1px solid var(--el-border-color-lighter);

  &::placeholder {
    color: var(--el-text-color-placeholder);
  }
}

.cmd-menu__list {
  max-height: 65vh;
  padding: var(--ys-spacing-2) 0;
  overflow-y: auto;
}

.cmd-menu__empty {
  padding: var(--ys-spacing-8) var(--ys-spacing-4);
  font-size: var(--ys-font-size-base);
  color: var(--el-text-color-secondary);
  text-align: center;
}

.cmd-menu__group {
  padding: var(--ys-spacing-1) 0;
}

:global(.cmd-menu__group [cmdk-group-heading]) {
  padding: var(--ys-spacing-2) var(--ys-spacing-4) var(--ys-spacing-1);
  font-size: 11px;
  font-weight: 600;
  color: var(--el-text-color-disabled);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.cmd-menu__item {
  display: flex;
  gap: var(--ys-spacing-2);
  align-items: center;
  padding: var(--ys-spacing-2) var(--ys-spacing-4);
  font-size: var(--ys-font-size-base);
  color: var(--el-text-color-primary);
  cursor: pointer;
  transition: background 0.15s;

  &:hover,
  &[data-selected='true'] {
    background: var(--el-fill-color-light);
  }

  &[aria-disabled='true'] {
    pointer-events: none;
    opacity: 0.5;
  }
}

.cmd-menu__item-icon {
  font-size: var(--ys-font-size-base);
  color: var(--el-text-color-disabled);
}

.cmd-menu__item-sep {
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-disabled);
}

.cmd-menu__item-check {
  margin-left: auto;
  font-size: var(--ys-font-size-base);
  color: var(--el-color-primary);
}

.cmd-menu__separator {
  height: 1px;
  margin: var(--ys-spacing-1) 0;
  background: var(--el-border-color-lighter);
}
</style>
