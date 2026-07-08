<script setup lang="ts">
import { computed, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useMobile } from '@/composables/useMobile'
import { useSidebarView } from '@/composables/useSidebarView'
import { useThemeStore } from '@/store/modules/theme'
import { SIDEBAR_WIDTH_COLLAPSED, SIDEBAR_WIDTH_EXPANDED } from './constants'
import NavGroup from './NavGroup.vue'
import SidebarViewHeader from './SidebarViewHeader.vue'

interface Emits {
  (e: 'view-change', key: string): void
}

const emit = defineEmits<Emits>()

const themeStore = useThemeStore()
const { sidebarCollapsed, sidebarVariant, sidebarCollapsible } =
  storeToRefs(themeStore)
const isMobile = useMobile()
const sidebarView = useSidebarView()

// 触发 view-change emit：watch sidebarView.key 变化（drill-in 切换/路由变化时触发）
watch(
  () => sidebarView.value.key,
  (key) => emit('view-change', key),
  { immediate: true }
)

// 桌面端侧边栏宽度：collapsible=icon 时根据 collapsed 切换；offcanvas 时 0；none 时固定
const desktopWidth = computed<number>(() => {
  if (sidebarCollapsible.value === 'offcanvas') {
    return sidebarCollapsed.value ? 0 : SIDEBAR_WIDTH_EXPANDED
  }
  if (sidebarCollapsible.value === 'none') return SIDEBAR_WIDTH_EXPANDED
  // 'icon'
  return sidebarCollapsed.value
    ? SIDEBAR_WIDTH_COLLAPSED
    : SIDEBAR_WIDTH_EXPANDED
})

// 是否渲染桌面 aside（移动端改用 drawer）
const showDesktopAside = computed(() => !isMobile.value)

// 是否显示移动 drawer
const showMobileDrawer = computed(() => isMobile.value)

// 是否隐藏 nav 标题（折叠态 + collapsible=icon）
const isIconOnly = computed(
  () => sidebarCollapsed.value && sidebarCollapsible.value === 'icon'
)
</script>

<template>
  <!-- 桌面端 aside：宽度过渡 -->
  <aside
    v-if="showDesktopAside"
    class="app-sidebar"
    :class="[
      `app-sidebar--${sidebarVariant}`,
      { 'app-sidebar--collapsed': isIconOnly }
    ]"
    :style="{ width: `${desktopWidth}px` }"
  >
    <!-- drill-in 视图头部 -->
    <SidebarViewHeader
      v-if="sidebarView.view"
      :view="sidebarView.view"
    />

    <div class="app-sidebar__content">
      <transition
        name="sidebar-slide"
        mode="out-in"
      >
        <div
          :key="sidebarView.key"
          class="app-sidebar__groups"
        >
          <template v-if="sidebarView.navGroups.length">
            <NavGroup
              v-for="group in sidebarView.navGroups"
              :id="group.id"
              :key="group.id || group.title"
              :title="group.title"
              :items="group.items"
            />
          </template>
          <div
            v-else
            class="app-sidebar__empty"
          >
            <i class="i-ep-data-line" />
            <span>{{ $t('common.empty.title') }}</span>
          </div>
        </div>
      </transition>
    </div>
  </aside>

  <!-- 移动端 drawer：从左滑入（user_input 提示移动端用 el-drawer） -->
  <el-drawer
    v-if="showMobileDrawer"
    v-model="themeStore.mobileSidebarOpen"
    direction="ltr"
    :size="SIDEBAR_WIDTH_EXPANDED + 'px'"
    :with-header="false"
    class="app-sidebar__drawer"
  >
    <SidebarViewHeader
      v-if="sidebarView.view"
      :view="sidebarView.view"
    />
    <div class="app-sidebar__content">
      <NavGroup
        v-for="group in sidebarView.navGroups"
        :id="group.id"
        :key="group.id || group.title"
        :title="group.title"
        :items="group.items"
      />
    </div>
  </el-drawer>
</template>

<style scoped lang="scss">
.app-sidebar {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--ys-bg-base);
  border-right: 1px solid var(--ys-border-lighter);
  transition: width 0.25s cubic-bezier(0.4, 0, 0.2, 1);

  &--floating {
    margin: var(--ys-spacing-3) 0 var(--ys-spacing-3) var(--ys-spacing-3);
    border: 1px solid var(--ys-border-lighter);
    border-radius: var(--ys-radius-lg);
    box-shadow: var(--ys-shadow-md);
  }

  &--inset {
    margin: 0;
    border-right: none;
    border-radius: var(--ys-radius-lg);

    .app-sidebar__content {
      padding: 0;
    }
  }

  &--collapsed {
    :deep(.nav-group__label),
    :deep(.nav-group__title),
    :deep(.nav-group__badge),
    :deep(.nav-group__chevron) {
      display: none;
    }

    :deep(.nav-group__link) {
      justify-content: center;
    }

    :deep(.system-brand__meta) {
      display: none;
    }
  }

  &__content {
    flex: 1;
    min-height: 0;
    padding: var(--ys-spacing-2) 0;
    overflow-y: auto;
    scrollbar-width: thin;
    scrollbar-color: transparent transparent;

    &:hover {
      scrollbar-color: var(--ys-border-darker) transparent;
    }

    &::-webkit-scrollbar {
      width: 4px;
    }

    &::-webkit-scrollbar-track {
      background: transparent;
    }

    &::-webkit-scrollbar-thumb {
      background-color: transparent;
      border-radius: var(--ys-radius-full);
      transition: background-color 0.2s ease;
    }

    &:hover::-webkit-scrollbar-thumb {
      background-color: var(--ys-border-darker);
    }
  }

  &__groups {
    display: flex;
    flex-direction: column;
  }

  &__empty {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-2);
    align-items: center;
    padding: var(--ys-spacing-6) var(--ys-spacing-3);
    font-size: var(--el-font-size-small);
    color: var(--el-text-color-secondary);
  }
}

// 切换动画
.sidebar-slide-enter-active,
.sidebar-slide-leave-active {
  transition: opacity 0.2s, transform 0.2s;
}

.sidebar-slide-enter-from {
  opacity: 0;
  transform: translateX(8px);
}

.sidebar-slide-leave-to {
  opacity: 0;
  transform: translateX(-8px);
}
</style>
