<script setup lang="ts">
// 公共认证布局：登录/注册/找回密码/OTP 共用，无侧边栏
// M1-A 实现：Logo + 站点名 + 居中内容容器
import { useSystemConfig } from '@/composables/useSystemConfig'
import { DEFAULT_SYSTEM_NAME, DEFAULT_LOGO } from '@/store/modules/system-config'
import { resolveComponent } from '@/plugins/spi/registry'

const { systemName, logo, loading } = useSystemConfig()

// SPI 扩展点：auth-shell 覆盖整个认证页外壳（商业版分屏沉浸布局）
// SPI 扩展点：brand-login 覆盖登录页品牌区（仅在 auth-shell 未覆盖时生效）
const authShellOverride = resolveComponent('auth-shell')
const brandOverride = resolveComponent('brand-login')
</script>

<template>
  <!-- SPI 扩展点：有外壳覆盖时，RouterView 作为默认 slot 交由定制实现接管 -->
  <component
    v-if="authShellOverride"
    :is="authShellOverride"
  >
    <RouterView />
  </component>
  <!-- 默认居中布局 -->
  <div
    v-else
    class="auth-layout"
  >
    <!-- SPI 扩展点：有覆盖时渲染定制品牌区 -->
    <component
      :is="brandOverride"
      v-if="brandOverride"
    />
    <!-- 默认：Logo + 站点名，点击回首页 -->
    <RouterLink
      v-else
      to="/"
      class="auth-layout__brand"
    >
      <div class="auth-layout__logo-wrap">
        <img
          v-if="!loading"
          :src="logo || DEFAULT_LOGO"
          :alt="$t('layout.auth.logoAlt')"
          class="auth-layout__logo"
        >
        <div
          v-else
          class="auth-layout__logo-placeholder"
        />
      </div>
      <h1 class="auth-layout__site-name">
        {{ loading ? '' : (systemName || DEFAULT_SYSTEM_NAME) }}
      </h1>
    </RouterLink>

    <!-- 居中内容容器：子路由 RouterView -->
    <main class="auth-layout__container">
      <div class="auth-layout__content">
        <RouterView />
      </div>
    </main>
  </div>
</template>

<style scoped lang="scss">
.auth-layout {
  position: relative;
  display: grid;
  max-width: none;
  height: 100vh;
  background: var(--el-bg-color);

  &__brand {
    position: absolute;
    top: 16px;
    left: 16px;
    z-index: 10;
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
    color: inherit;
    text-decoration: none;
    transition: opacity 0.2s;

    &:hover {
      opacity: 0.8;
    }

    @media (width >= 640px) {
      top: 32px;
      left: 32px;
    }
  }

  &__logo-wrap {
    position: relative;
    width: 32px;
    height: 32px;
  }

  &__logo {
    width: 100%;
    height: 100%;
    object-fit: cover;
    border-radius: 50%;
  }

  &__logo-placeholder {
    width: 100%;
    height: 100%;
    background: var(--el-fill-color);
    border-radius: 50%;
    animation: pulse 1.5s ease-in-out infinite;
  }

  &__site-name {
    margin: 0;
    font-size: var(--el-font-size-large);
    font-weight: 500;
    color: var(--el-text-color-primary);
  }

  &__container {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 64px var(--ys-spacing-4);

    @media (width >= 640px) {
      padding: 0;
    }
  }

  &__content {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-2);
    justify-content: center;
    width: 100%;
    max-width: 480px;
    padding: var(--ys-spacing-8) var(--ys-spacing-4);

    @media (width >= 640px) {
      padding: var(--ys-spacing-8);
    }
  }
}

@keyframes pulse {
  0%, 100% { opacity: 0.4; }
  50% { opacity: 0.8; }
}
</style>
