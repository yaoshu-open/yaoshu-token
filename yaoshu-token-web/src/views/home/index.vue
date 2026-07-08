<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/store/modules/auth'

const { t } = useI18n()
const authStore = useAuthStore()

// dev-only 调试输出：仅 dev 构建展示，prod 构建被 Vite import.meta.env.DEV 静态消除
const isDev = import.meta.env.DEV
</script>

<template>
  <div class="home">
    <h1>{{ t('home.title') }}</h1>
    <p>{{ t('home.welcome') }}</p>
    <p
      v-if="isDev && authStore.isLoggedIn"
      class="home__debug"
    >
      {{ t('home.debug.tokenLabel', { token: authStore.token }) }}
    </p>
  </div>
</template>

<style scoped>
.home {
  padding: var(--ys-spacing-6);
}

.home__debug {
  font-family: var(--el-font-family-monospace, monospace);
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
}
</style>
