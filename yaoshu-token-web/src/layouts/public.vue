<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
// 公开页布局：无侧边栏，用于 rankings / home / pricing / about 等公开页面。
// 无论登录态，公开页保持沉浸式全宽体验（PublicHeader 自身处理登录态：
// 已登录显示 ProfileDropdown，未登录显示登录按钮）。侧边栏是控制台工作台专属。
import PublicHeader from '@/components/layout/PublicHeader.vue'
import PageFooter from '@/components/layout/PageFooter.vue'

const route = useRoute()
// 内容区是否包裹 max-width 容器：rankings 用 false（全宽图表），home/pricing 用 true（默认）
const showContainer = computed(() => route.meta.container !== false)
</script>

<template>
  <div class="public-layout">
    <PublicHeader />
    <main
      class="public-layout__main"
      :class="{ 'public-layout__main--container': showContainer }"
    >
      <slot>
        <RouterView />
      </slot>
    </main>
    <PageFooter />
  </div>
</template>

<style scoped lang="scss">
.public-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;

  &__main {
    flex: 1;
    width: 100%;

    // 包裹 max-width 容器（内容居中）
    &--container {
      max-width: 1280px;
      padding: 1.5rem 1rem;
      margin: 0 auto;

      @media (width >= 640px) {
        padding: 2rem 1.5rem;
      }

      @media (width >= 1280px) {
        padding: 2rem;
      }
    }
  }
}
</style>
