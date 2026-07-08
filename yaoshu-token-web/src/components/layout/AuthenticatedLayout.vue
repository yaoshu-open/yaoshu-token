<script setup lang="ts">
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useThemeStore } from '@/store/modules/theme'
import AppSidebar from './AppSidebar.vue'
import AppHeader from './AppHeader.vue'

const themeStore = useThemeStore()
const { sidebarVariant } = storeToRefs(themeStore)

// 根容器 class：variant 驱动布局
const layoutClass = computed(() => [
  'authenticated-layout',
  `authenticated-layout--${sidebarVariant.value}`
])
const mainId = 'main-content'
</script>

<template>
  <div :class="layoutClass">
    <!-- 无障碍：跳转到主内容 -->
    <a
      :href="`#${mainId}`"
      class="authenticated-layout__skip"
    >{{ $t('common.backHome') }}</a>

    <AppHeader />

    <div class="authenticated-layout__body">
      <AppSidebar />
      <main
        :id="mainId"
        class="authenticated-layout__main"
      >
        <div class="authenticated-layout__main-inner">
          <slot />
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped lang="scss">
.authenticated-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;

  &__skip {
    position: absolute;
    top: -100px;
    left: 0;
    z-index: -1;
    padding: var(--ys-spacing-2) var(--ys-spacing-4);
    color: var(--el-color-primary);
    background: var(--el-bg-color);
    border-radius: 0 0 var(--el-border-radius-base) 0;

    &:focus {
      top: 0;
      z-index: 100;
    }
  }

  &__body {
    display: flex;
    flex: 1;
    min-height: 0;
  }

  &__main {
    display: flex;
    flex: 1;
    flex-direction: column;
    min-width: 0;
    overflow: hidden;
  }

  &__main-inner {
    position: relative;
    display: flex;
    flex: 1;
    flex-direction: column;
    min-height: 0;
    padding: var(--ys-spacing-4);
    overflow-y: auto;
    background: var(--ys-bg-brand-soft);

    // variant=inset：内容区左侧 padding 与 sidebar 右边缘保持间距
    .authenticated-layout--inset & {
      padding: var(--ys-spacing-4) var(--ys-spacing-4) var(--ys-spacing-4) var(--ys-spacing-3);
    }

    // variant=floating：内容区紧贴 sidebar
    .authenticated-layout--floating & {
      padding: var(--ys-spacing-4);
    }
  }
}
</style>
