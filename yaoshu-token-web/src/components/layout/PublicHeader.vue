<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/store/modules/auth'
import { useSystemConfig } from '@/composables/useSystemConfig'
import { useNotifications } from '@/composables/useNotifications'
import { useTopNavLinks } from '@/composables/useTopNavLinks'
import { defaultTopNavLinks } from './config/top-nav-config'
import { AUTH_PROMPT_SECONDS } from './constants'
import ThemeSwitch from '@/components/ThemeSwitch.vue'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import NotificationPopover from '@/components/NotificationPopover.vue'
import ProfileDropdown from '@/components/ProfileDropdown.vue'
import Dialog from '@/components/Dialog.vue'
import { ElButton } from 'element-plus'
import PublicNavigation from './PublicNavigation.vue'
import MobileDrawer from './MobileDrawer.vue'
import type { TopNavLink } from './types'

interface Props {
  navLinks?: TopNavLink[]
  showThemeSwitch?: boolean
  showLanguageSwitcher?: boolean
  showAuthButtons?: boolean
  showNotifications?: boolean
  homeUrl?: string
}

const props = withDefaults(defineProps<Props>(), {
  navLinks: () => defaultTopNavLinks,
  showThemeSwitch: true,
  showLanguageSwitcher: true,
  showAuthButtons: true,
  showNotifications: true,
  homeUrl: '/'
})

const { t } = useI18n()
const router = useRouter()
const authStore = useAuthStore()
const { userInfo } = storeToRefs(authStore)
const { systemName, logo, loading } = useSystemConfig()
const dynamicLinks = useTopNavLinks()
// 解构通知 refs 以便模板自动解包
const {
  popoverOpen,
  announcements,
  unreadCount,
  markAllAnnouncementsRead
} = useNotifications()

const links = computed<TopNavLink[]>(() => {
  if (dynamicLinks.value.length > 0) return dynamicLinks.value
  return props.navLinks
})

const isAuthed = computed(() => !!userInfo.value)
const displaySiteName = computed(() => systemName.value)
const scrolled = ref(false)
function onScroll() {
  scrolled.value = window.scrollY > 20
}
onMounted(() => {
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })
})
onUnmounted(() => window.removeEventListener('scroll', onScroll))

// 移动端菜单抽屉
const mobileOpen = ref(false)
onMounted(() => {
  document.body.style.overflow = ''
})
watch(mobileOpen, (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
})
const authPromptTarget = ref<{ title: string; href: string } | null>(null)
const authPromptSecondsLeft = ref(AUTH_PROMPT_SECONDS)

let promptIntervalId: number | undefined
let promptTimeoutId: number | undefined

function closeAuthPrompt() {
  authPromptTarget.value = null
  authPromptSecondsLeft.value = AUTH_PROMPT_SECONDS
  if (promptIntervalId) window.clearInterval(promptIntervalId)
  if (promptTimeoutId) window.clearTimeout(promptTimeoutId)
}

function navigateToSignIn(redirectHref: string) {
  closeAuthPrompt()
  router.push({ path: '/sign-in', query: { redirect: redirectHref } })
}

function showAuthPrompt(link: { title: string; href: string }) {
  authPromptTarget.value = link
}

watch(authPromptTarget, (target) => {
  if (!target) return
  authPromptSecondsLeft.value = AUTH_PROMPT_SECONDS
  promptIntervalId = window.setInterval(() => {
    authPromptSecondsLeft.value = Math.max(authPromptSecondsLeft.value - 1, 0)
  }, 1000)
  promptTimeoutId = window.setTimeout(() => {
    const href = target.href
    closeAuthPrompt()
    navigateToSignIn(href)
  }, AUTH_PROMPT_SECONDS * 1000)
})

function goSignIn() {
  router.push('/sign-in')
}

function handleSignOut() {
  authStore.clearAuthToken()
  router.push('/')
}
</script>

<template>
  <header class="public-header">
    <div
      class="public-header__bar"
      :class="{ 'public-header__bar--scrolled': scrolled }"
    >
      <!-- Logo + 站点名 -->
      <RouterLink
        :to="homeUrl"
        class="public-header__brand"
      >
        <div class="public-header__logo">
          <img
            v-if="!loading"
            :src="logo"
            :alt="t('layout.auth.logoAlt')"
          >
        </div>
        <span class="public-header__site-name">{{ displaySiteName }}</span>
      </RouterLink>

      <!-- 桌面端导航 -->
      <div class="public-header__nav">
        <PublicNavigation :links="links" @auth-required="showAuthPrompt" />

        <div class="public-header__divider" />

        <LanguageSwitcher v-if="showLanguageSwitcher" />
        <ThemeSwitch v-if="showThemeSwitch" />

        <NotificationPopover
          v-if="showNotifications"
          v-model="popoverOpen"
          :unread-count="unreadCount"
          :announcements="announcements"
          @mark-all-announcements-read="markAllAnnouncementsRead"
        />

        <template v-if="showAuthButtons">
          <div class="public-header__divider" />
          <ProfileDropdown
            v-if="isAuthed"
            @sign-out="handleSignOut"
          />
          <ElButton
            v-else
            size="small"
            @click="goSignIn"
          >
            {{ t('nav.signIn') }}
          </ElButton>
        </template>
      </div>

      <!-- 移动端汉堡按钮 -->
      <button
        type="button"
        class="public-header__mobile-toggle"
        :aria-label="t('layout.header.toggleSidebar')"
        @click="mobileOpen = !mobileOpen"
      >
        <i class="i-ep-menu" />
      </button>
    </div>

    <!-- 移动端抽屉 -->
    <MobileDrawer
      :is-open="mobileOpen"
      :mobile-links="links"
      :user="userInfo"
      :show-auth-buttons="showAuthButtons"
      :home-url="homeUrl"
      @close="mobileOpen = false"
      @navigate="(path: string) => { router.push(path); mobileOpen = false }"
    />

    <!-- requiresAuth 提示对话框 -->
    <Dialog
      :model-value="!!authPromptTarget"
      :title="t('layout.header.signIn')"
      :description="t('notification.viewDetail')"
      width="420px"
      append-to-body
      @update:model-value="(v: boolean) => !v && closeAuthPrompt()"
    >
      <p>{{ t('common.continue') }} - {{ authPromptSecondsLeft }}s</p>
      <template #footer>
        <ElButton @click="closeAuthPrompt">
          {{ t('common.cancel') }}
        </ElButton>
        <ElButton
          type="primary"
          @click="navigateToSignIn(authPromptTarget?.href || '/')"
        >
          {{ t('nav.signIn') }}
        </ElButton>
      </template>
    </Dialog>
  </header>
</template>

<style scoped lang="scss">
.public-header {
  position: fixed;
  inset: 0 0 auto;
  z-index: 50;
  pointer-events: none;

  &__bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 64px;
    padding: 0 var(--ys-spacing-4);
    margin: 0 auto;
    pointer-events: auto;
    transition: all 0.5s cubic-bezier(0.16, 1, 0.3, 1);

    @media (width >= 768px) {
      padding: 0 var(--ys-spacing-6);
    }

    &--scrolled {
      max-width: 52rem;
      height: 48px;
      padding: 0 var(--ys-spacing-3);
      margin: var(--ys-spacing-3) auto 0;
      background: var(--el-bg-color);
      border-radius: var(--ys-radius-full);
      box-shadow: var(--ys-shadow-md);
      backdrop-filter: blur(20px);
    }
  }

  &__brand {
    display: flex;
    gap: 10px;
    align-items: center;
    color: inherit;
    text-decoration: none;
  }

  &__logo {
    width: 28px;
    height: 28px;
    overflow: hidden;

    img {
      width: 100%;
      height: 100%;
      object-fit: contain;
    }
  }

  &__site-name {
    font-size: var(--el-font-size-base);
    font-weight: 600;
  }

  &__nav {
    display: none;
    gap: var(--ys-spacing-2);
    align-items: center;

    @media (width >= 768px) {
      display: flex;
    }
  }

  &__divider {
    width: 1px;
    height: 16px;
    margin: 0 var(--ys-spacing-1);
    background: var(--el-border-color);
  }

  &__mobile-toggle {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    font-size: var(--ys-font-size-xl);
    color: var(--el-text-color-regular);
    cursor: pointer;
    background: transparent;
    border: 0;
    border-radius: var(--el-border-radius-base);

    @media (width >= 768px) {
      display: none;
    }

    &:hover {
      background: var(--el-fill-color-light);
    }
  }
}
</style>
