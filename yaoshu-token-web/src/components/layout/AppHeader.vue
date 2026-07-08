<script setup lang="ts">
import { computed, ref } from 'vue'
import { useTopNavLinks } from '@/composables/useTopNavLinks'
import { useNotifications } from '@/composables/useNotifications'
import SystemBrand from './SystemBrand.vue'
import SpiSlot from '@/plugins/spi/SpiSlot.vue'
import TopNav from './TopNav.vue'
import SidebarTrigger from './components/SidebarTrigger.vue'
import NotificationPopover from '@/components/NotificationPopover.vue'
import ProfileDropdown from '@/components/ProfileDropdown.vue'
import ConfigDrawer from '@/components/ConfigDrawer.vue'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import ThemeSwitch from '@/components/ThemeSwitch.vue'
import Search from '@/components/Search.vue'
import NewYearButton from './NewYearButton.vue'
import { defaultTopNavLinks } from './config/top-nav-config'
import type { TopNavLink } from './types'

interface Props {
  navLinks?: TopNavLink[]
  showTopNav?: boolean
  showSearch?: boolean
  showNotifications?: boolean
  showConfigDrawer?: boolean
  showProfileDropdown?: boolean
  showLanguageSwitcher?: boolean
  showThemeSwitch?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  navLinks: () => defaultTopNavLinks,
  showTopNav: true,
  showSearch: true,
  showNotifications: true,
  showConfigDrawer: true,
  showProfileDropdown: true,
  showLanguageSwitcher: true,
  showThemeSwitch: false
})

// 后端动态链接优先
const dynamicLinksRef = useTopNavLinks()
const links = computed<TopNavLink[]>(() => {
  const dyn = dynamicLinksRef.value
  return dyn.length > 0 ? dyn : props.navLinks
})

// 通知聚合（解构 refs 以便模板自动解包）
const {
  popoverOpen,
  announcements,
  unreadCount,
  markAllAnnouncementsRead
} = useNotifications()

// ConfigDrawer 开合
const configDrawerOpen = ref(false)

// 全局搜索：转发 emit（M1-A 仅占位入口，业务联动走 C 类待办 M1-A-T1）
const emit = defineEmits<{
  (e: 'search', keyword: string): void
}>()
function handleSearch(keyword: string) {
  emit('search', keyword)
}
</script>

<template>
  <header class="app-header">
    <div class="app-header__inner">
      <div class="app-header__left">
        <SidebarTrigger variant="ghost" />
        <SpiSlot
          name="brand-logo"
          :fallback="SystemBrand"
          variant="inline"
        />
      </div>

      <div class="app-header__right">
        <!-- 桌面端顶部导航 -->
        <TopNav
          v-if="showTopNav"
          :links="links"
          class="app-header__nav"
        />

        <!-- 全局搜索 -->
        <Search
          v-if="showSearch"
          @search="handleSearch"
        />

        <!-- 新年烟花彩蛋（仅在农历新年期间显示） -->
        <NewYearButton />

        <!-- 通知 -->
        <NotificationPopover
          v-if="showNotifications"
          v-model="popoverOpen"
          :unread-count="unreadCount"
          :announcements="announcements"
          @mark-all-announcements-read="markAllAnnouncementsRead"
        />

        <LanguageSwitcher v-if="showLanguageSwitcher" />
        <ThemeSwitch v-if="showThemeSwitch" />

        <!-- 设置抽屉 -->
        <button
          v-if="showConfigDrawer"
          type="button"
          class="app-header__icon-btn"
          :aria-label="$t('layout.header.settings')"
          @click="configDrawerOpen = true"
        >
          <i class="i-ep-setting" />
        </button>

        <ProfileDropdown v-if="showProfileDropdown" />
      </div>
    </div>

    <ConfigDrawer v-model="configDrawerOpen" />
  </header>
</template>

<style scoped lang="scss">
.app-header {
  position: sticky;
  top: 0;
  z-index: 40;
  height: 48px;

  // 玻璃质感：半透明背景 + backdrop-filter（官网轻量科技感）
  background-color: color-mix(in srgb, var(--el-bg-color) 85%, transparent);
  border-bottom: 1px solid var(--ys-border-lighter);
  backdrop-filter: blur(12px);

  &__inner {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
    justify-content: space-between;
    height: 100%;
    padding: 0 var(--ys-spacing-3);
  }

  &__left {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
  }

  &__right {
    display: flex;
    gap: var(--ys-spacing-1);
    align-items: center;
    margin-left: auto;

    @media (width >= 768px) {
      gap: var(--ys-spacing-2);
    }
  }

  &__nav {
    margin-right: 8px;
  }

  &__icon-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    font-size: 18px;
    color: var(--el-text-color-regular);
    cursor: pointer;
    background: transparent;
    border: 0;
    border-radius: var(--el-border-radius-base);
    transition: background-color 0.2s, color 0.2s;

    &:hover {
      color: var(--el-text-color-primary);
      background: var(--el-fill-color-light);
    }
  }
}
</style>
