<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/store/modules/auth'
import { useSystemConfigStore } from '@/store/modules/system-config'
import HeroTerminalDemo from '@/components/hero/HeroTerminalDemo.vue'
import { resolveComponent } from '@/plugins/spi/registry'

const router = useRouter()
const { t } = useI18n()
const authStore = useAuthStore()
const { isLoggedIn } = storeToRefs(authStore)
const systemConfigStore = useSystemConfigStore()

// CTA 目标：指向仪表盘
const dashboardUrl = '/dashboard'
const docsUrl = computed(() => systemConfigStore.rawStatus?.docsLink || 'https://docs.token.yaoshu.cc')
const isExternalDocs = computed(() => docsUrl.value.startsWith('http'))

function goToSignUp(): void {
  router.push('/sign-in')
}

function goToPricing(): void {
  router.push('/pricing')
}

function goToDashboard(): void {
  router.push(dashboardUrl)
}

// SPI 扩展点：定制实现可覆盖首页
const landingOverride = resolveComponent('landing-page')
</script>

<template>
  <component
    :is="landingOverride"
    v-if="landingOverride"
  />
  <section
    v-else
    class="landing-hero"
  >
    <!-- 径向渐变背景 -->
    <div
      aria-hidden
      class="landing-hero__bg-gradient"
    />
    <!-- 网格遮罩 -->
    <div
      aria-hidden
      class="landing-hero__bg-grid"
    />

    <div class="landing-hero__grid">
      <!-- 左栏：标题/描述/CTA/支持应用 -->
      <div class="landing-hero__left">
        <!-- 顶部 Pill 徽标 -->
        <div class="landing-hero__badge">
          <span class="landing-hero__badge-dot" />
          <span>{{ t('landing.badge') }}</span>
        </div>

        <h1 class="landing-hero__title">
          {{ t('landing.titleLine1') }}
          <br>
          <span class="landing-hero__title-gradient">{{ t('landing.titleLine2') }}</span>
        </h1>

        <p class="landing-hero__desc">
          {{ t('landing.description') }}
        </p>

        <!-- CTA 按钮组 -->
        <div class="landing-hero__cta">
          <template v-if="isLoggedIn">
            <button
              class="landing-hero__btn landing-hero__btn--primary"
              type="button"
              @click="goToDashboard"
            >
              {{ t('landing.cta.goToDashboard') }}
              <i class="i-ep-arrow-right" />
            </button>
          </template>
          <template v-else>
            <button
              class="landing-hero__btn landing-hero__btn--primary"
              type="button"
              @click="goToSignUp"
            >
              {{ t('landing.cta.getStarted') }}
              <i class="i-ep-arrow-right" />
            </button>
            <button
              class="landing-hero__btn landing-hero__btn--outline"
              type="button"
              @click="goToPricing"
            >
              {{ t('landing.cta.viewPricing') }}
            </button>
          </template>
          <!-- 文档按钮 -->
          <a
            v-if="isExternalDocs"
            class="landing-hero__btn landing-hero__btn--outline"
            :href="docsUrl"
            target="_blank"
            rel="noopener noreferrer"
          >
            <i class="i-ep-document" />
            {{ t('landing.cta.docs') }}
          </a>
          <button
            v-else
            class="landing-hero__btn landing-hero__btn--outline"
            type="button"
            @click="router.push(docsUrl)"
          >
            <i class="i-ep-document" />
            {{ t('landing.cta.docs') }}
          </button>
        </div>

        <!-- 支持应用 -->
        <div class="landing-hero__apps">
          <div class="landing-hero__apps-header">
            <span class="landing-hero__apps-label">{{ t('landing.supportedApps') }}</span>
            <p class="landing-hero__apps-desc">
              {{ t('landing.supportedAppsDesc') }}
            </p>
          </div>
          <div class="landing-hero__apps-list">
            <a
              href="https://cherry-ai.com"
              target="_blank"
              rel="noopener noreferrer"
              class="landing-hero__app-card"
            >
              <span class="landing-hero__app-icon landing-hero__app-icon--cherry">CS</span>
              <span>Cherry Studio</span>
            </a>
            <a
              href="https://ccswitch.io"
              target="_blank"
              rel="noopener noreferrer"
              class="landing-hero__app-card"
            >
              <span class="landing-hero__app-icon landing-hero__app-icon--cc">CC</span>
              <span>CC Switch</span>
            </a>
            <div class="landing-hero__app-card landing-hero__app-card--more">
              <span class="landing-hero__app-icon landing-hero__app-icon--more">···</span>
              <span>{{ t('landing.moreApps') }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 右栏：终端 API 演示 -->
      <div class="landing-hero__right">
        <HeroTerminalDemo />
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use "@/styles/tokens" as *;

.landing-hero {
  position: relative;
  z-index: 10;
  padding: 96px $spacing-6 64px;
  overflow: hidden;

  @media (width >= 768px) {
    padding: 128px $spacing-6 96px;
  }

  @media (width >= 1024px) {
    padding: 144px $spacing-8 112px;
  }
}

.landing-hero__bg-gradient {
  position: absolute;
  inset: 0;
  z-index: -10;
  pointer-events: none;
  background:
    radial-gradient(ellipse 60% 50% at 20% 20%, color-mix(in srgb, var(--ys-color-primary) 80%, transparent) 0%, transparent 70%),
    radial-gradient(ellipse 50% 40% at 80% 15%, color-mix(in srgb, var(--ys-color-primary) 60%, transparent) 0%, transparent 70%),
    radial-gradient(ellipse 40% 35% at 40% 80%, color-mix(in srgb, var(--ys-color-secondary) 40%, transparent) 0%, transparent 70%);
  opacity: 0.25;

  :global(html.dark) & {
    opacity: 0.12;
  }
}

.landing-hero__bg-grid {
  position: absolute;
  inset: 0;
  z-index: -10;
  pointer-events: none;
  background-image:
    linear-gradient(to right, var(--el-border-color) 1px, transparent 1px),
    linear-gradient(to bottom, var(--el-border-color) 1px, transparent 1px);
  background-size: 4rem 4rem;
  opacity: 0.08;
  mask-image: radial-gradient(ellipse 60% 50% at 50% 30%, black 20%, transparent 100%);
}

.landing-hero__grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-12;
  align-items: start;
  max-width: 1152px;
  margin: 0 auto;

  @media (width >= 1024px) {
    grid-template-columns: repeat(12, 1fr);
    gap: $spacing-8;
  }
}

.landing-hero__left {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  text-align: left;

  @media (width >= 1024px) {
    grid-column: span 6;
  }
}

.landing-hero__badge {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 6px $spacing-3;
  margin-bottom: $spacing-5;
  font-size: 11px;
  font-weight: 500;
  color: var(--ys-color-primary);
  background: color-mix(in srgb, var(--ys-color-primary) 5%, transparent);
  border: 1px solid color-mix(in srgb, var(--ys-color-primary) 20%, transparent);
  border-radius: 9999px;

  :global(html.dark) & {
    color: var(--ys-color-primary);
    background: color-mix(in srgb, var(--ys-color-primary) 8%, transparent);
    border-color: color-mix(in srgb, var(--ys-color-primary) 25%, transparent);
  }
}

.landing-hero__badge-dot {
  position: relative;
  display: flex;
  width: 6px;
  height: 6px;

  &::before {
    position: absolute;
    inset: 0;
    content: '';
    background: var(--ys-color-primary);
    border-radius: 50%;
    opacity: 0.75;
    animation: hero-ping 1.5s cubic-bezier(0, 0, 0.2, 1) infinite;
  }

  &::after {
    position: relative;
    display: inline-flex;
    width: 6px;
    height: 6px;
    content: '';
    background: var(--ys-color-primary);
    border-radius: 50%;
  }
}

@keyframes hero-ping {
  0% {
    opacity: 0.75;
    transform: scale(1);
  }

  75%, 100% {
    opacity: 0;
    transform: scale(2);
  }
}

.landing-hero__title {
  font-size: clamp(2.25rem, 4.5vw, 3.25rem);
  font-weight: 700;
  line-height: 1.15;
  color: var(--el-text-color-primary);
  letter-spacing: -0.025em;
}

.landing-hero__title-gradient {
  color: transparent;
  background: linear-gradient(to right, var(--ys-color-primary), var(--ys-color-secondary));
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.landing-hero__desc {
  max-width: 576px;
  margin-top: $spacing-5;
  font-size: $font-size-lg;
  line-height: 1.625;
  color: var(--el-text-color-secondary);

  @media (width >= 768px) {
    font-size: 15px;
  }
}

.landing-hero__cta {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-3;
  align-items: center;
  margin-top: $spacing-8;
}

.landing-hero__btn {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  height: 44px;
  padding: 0 $spacing-5;
  font-size: $font-size-base;
  font-weight: 500;
  text-decoration: none;
  cursor: pointer;
  border: 1px solid transparent;
  border-radius: var(--ys-radius-full);
  transition: all 0.2s;

  &--primary {
    color: var(--el-color-white);
    background: var(--ys-gradient-brand);
    border-color: transparent;

    &:hover {
      opacity: 0.9;
    }

    i {
      transition: transform 0.2s;
    }

    &:hover i {
      transform: translateX(2px);
    }
  }

  &--outline {
    color: var(--el-text-color-primary);
    background: transparent;
    border-color: var(--el-border-color);

    &:hover {
      background: var(--el-fill-color-light);
      border-color: var(--el-border-color-light);
    }
  }
}

.landing-hero__apps {
  width: 100%;
  max-width: 576px;
  margin-top: 40px;
}

.landing-hero__apps-header {
  display: flex;
  flex-direction: column;
  gap: $spacing-1;
  margin-bottom: $spacing-4;
}

.landing-hero__apps-label {
  font-size: 10px;
  font-weight: 700;
  color: var(--el-text-color-placeholder);
  text-transform: uppercase;
  letter-spacing: 0.15em;
}

.landing-hero__apps-desc {
  font-size: $font-size-xs;
  line-height: 1.5;
  color: var(--el-text-color-disabled);
}

.landing-hero__apps-list {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-3;
  align-items: center;
}

.landing-hero__app-card {
  display: flex;
  gap: $spacing-3;
  align-items: center;
  padding: 10px $spacing-5;
  font-size: $font-size-base;
  font-weight: 500;
  color: var(--el-text-color-regular);
  text-decoration: none;
  cursor: pointer;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 9999px;
  transition: all 0.3s;

  &:hover {
    color: var(--el-text-color-primary);
    background: var(--el-fill-color);
    border-color: var(--el-border-color);
    transform: scale(1.02);
  }

  &--more {
    color: var(--el-text-color-disabled);
    cursor: default;
  }
}

.landing-hero__app-icon {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  font-size: 10px;
  font-weight: 700;
  border-radius: var(--ys-radius-base);

  &--cherry {
    color: var(--ys-color-danger);
    background: color-mix(in srgb, var(--ys-color-danger) 10%, transparent);
  }

  &--cc {
    color: var(--ys-color-primary);
    background: color-mix(in srgb, var(--ys-color-primary) 10%, transparent);
  }

  &--more {
    font-size: $font-size-lg;
    color: var(--el-text-color-disabled);
    letter-spacing: 0.1em;
    background: var(--el-fill-color);
  }
}

.landing-hero__right {
  display: flex;
  justify-content: center;
  width: 100%;

  @media (width >= 1024px) {
    grid-column: span 6;
  }
}
</style>
