<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUserDisplay } from '@/composables/useUserDisplay'
import { useSystemConfig } from '@/composables/useSystemConfig'
import { MOBILE_DRAWER_CONFIG } from './constants'
import type { TopNavLink } from './types'
import type { UserInfo } from '@/api/user/types'

interface Props {
  isOpen: boolean
  homeUrl?: string
  mobileLinks?: TopNavLink[]
  showAuthButtons?: boolean
  user?: UserInfo | null
}

const props = withDefaults(defineProps<Props>(), {
  homeUrl: '/',
  mobileLinks: () => [],
  showAuthButtons: true,
  user: null
})

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'navigate', path: string): void
}>()

const { t } = useI18n()
const { systemName, logo, loading } = useSystemConfig()

// 用户显示信息（M1-D useUserDisplay）
const display = useUserDisplay(() => props.user)

const visibleLinks = computed<TopNavLink[]>(() => props.mobileLinks)

// 路径跳转：emit + 路由 push
function goPath(path: string) {
  emit('navigate', path)
}

function goSignIn() {
  emit('navigate', '/sign-in')
}

function goProfile() {
  emit('navigate', '/profile')
}

function goWallet() {
  emit('navigate', '/wallet')
}

function signOut() {
  // M1-B 接 authStore.clearAuthToken + 调用后端 logout API
  // M1-A 占位：直接跳 sign-in（待 M1-B 增强为登出 + 重定向）
  emit('navigate', '/sign-in')
}
</script>

<template>
  <transition name="drawer-fade">
    <div
      v-if="isOpen"
      class="mobile-drawer"
    >
      <!-- 遮罩 -->
      <div
        :class="MOBILE_DRAWER_CONFIG.overlayClassName"
        @click="emit('close')"
      />

      <!-- 抽屉主体（底部上滑，） -->
      <div :class="MOBILE_DRAWER_CONFIG.drawerClassName">
        <div class="mobile-drawer__inner">
          <!-- 头部：品牌 + 关闭 -->
          <div class="mobile-drawer__header">
            <RouterLink
              :to="homeUrl"
              class="mobile-drawer__brand"
              @click="emit('close')"
            >
              <div class="mobile-drawer__logo">
                <img
                  v-if="!loading"
                  :src="logo"
                  :alt="t('layout.auth.logoAlt')"
                >
              </div>
              <span class="mobile-drawer__site-name">{{ systemName }}</span>
            </RouterLink>
            <button
              type="button"
              class="mobile-drawer__close"
              :aria-label="t('common.cancel')"
              @click="emit('close')"
            >
              <i class="i-ep-close" />
            </button>
          </div>

          <!-- 导航链接 -->
          <nav class="mobile-drawer__nav">
            <a
              v-for="(link, idx) in visibleLinks"
              :key="`${link.href}-${idx}`"
              class="mobile-drawer__link"
              :href="link.href"
              @click.prevent="goPath(link.href)"
            >{{ link.title }}</a>
          </nav>

          <!-- 用户区 -->
          <div
            v-if="showAuthButtons"
            class="mobile-drawer__user"
          >
            <template v-if="user">
              <div class="mobile-drawer__user-info">
                <div class="mobile-drawer__avatar">
                  {{ display.initials }}
                </div>
                <div>
                  <div class="mobile-drawer__user-name">
                    {{ display.displayName }}
                  </div>
                  <div
                    v-if="display.roleLabel"
                    class="mobile-drawer__user-role"
                  >
                    {{ display.roleLabel }}
                  </div>
                </div>
              </div>
              <button
                type="button"
                class="mobile-drawer__user-link"
                @click="goProfile"
              >
                <i class="i-ep-user" />{{ t('user.menu.profile') }}
              </button>
              <button
                type="button"
                class="mobile-drawer__user-link"
                @click="goWallet"
              >
                <i class="i-ep-wallet" />{{ t('user.menu.wallet') }}
              </button>
              <button
                type="button"
                class="mobile-drawer__user-link mobile-drawer__user-link--danger"
                @click="signOut"
              >
                <i class="i-ep-switch-button" />{{ t('user.menu.signOut') }}
              </button>
            </template>
            <button
              v-else
              type="button"
              class="mobile-drawer__sign-in"
              @click="goSignIn"
            >
              {{ t('nav.signIn') }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </transition>
</template>

<style scoped lang="scss">
.mobile-drawer {
  &__inner {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-4);
  }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__brand {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
    font-weight: 700;
    color: inherit;
    text-decoration: none;
  }

  &__logo {
    width: 24px;
    height: 24px;

    img {
      width: 100%;
      height: 100%;
      object-fit: contain;
    }
  }

  &__close {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    color: var(--el-text-color-primary);
    cursor: pointer;
    background: transparent;
    border: 0;
    border-radius: var(--el-border-radius-base);

    &:hover {
      background: var(--el-fill-color-light);
    }
  }

  &__nav {
    display: flex;
    flex-direction: column;
    overflow: hidden;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: var(--el-border-radius-base);
  }

  &__link {
    display: block;
    padding: 10px;
    color: var(--el-text-color-regular);
    text-decoration: none;
    border-bottom: 1px solid var(--el-border-color-lighter);
    transition: color 0.2s;

    &:last-child {
      border-bottom: 0;
    }

    &:hover {
      color: var(--el-color-primary);
    }
  }

  &__user {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-1);
    font-size: var(--el-font-size-small);
  }

  &__user-info {
    display: flex;
    gap: 10px;
    align-items: center;
    padding: 10px;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  &__avatar {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    font-size: var(--el-font-size-small);
    font-weight: 500;
    color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
    border-radius: 50%;
  }

  &__user-name {
    font-weight: 500;
    color: var(--el-text-color-primary);
  }

  &__user-role {
    font-size: var(--el-font-size-extra-small);
    color: var(--el-text-color-secondary);
  }

  &__user-link {
    display: flex;
    gap: 10px;
    align-items: center;
    width: 100%;
    padding: 10px;
    color: var(--el-text-color-regular);
    text-align: left;
    cursor: pointer;
    background: transparent;
    border: 0;
    border-bottom: 1px solid var(--el-border-color-lighter);

    &--danger {
      color: var(--el-color-danger);
    }

    &:hover {
      background: var(--el-fill-color-light);
    }
  }

  &__sign-in {
    width: 100%;
    padding: 10px;
    font-size: var(--el-font-size-base);
    color: white;
    cursor: pointer;
    background: var(--el-color-primary);
    border: 0;
    border-radius: var(--el-border-radius-base);

    &:hover {
      opacity: 0.9;
    }
  }
}

// 抽屉进出动画
.drawer-fade-enter-active,
.drawer-fade-leave-active {
  transition: opacity 0.2s;
}

.drawer-fade-enter-from,
.drawer-fade-leave-to {
  opacity: 0;
}
</style>
