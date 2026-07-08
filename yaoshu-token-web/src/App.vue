<script setup lang="ts">
import { onMounted, onUnmounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'
import CommandMenu from '@/components/command-menu/CommandMenu.vue'
import { setupCommandMenuShortcut, teardownCommandMenuShortcut } from '@/composables/useCommandMenu'

const { locale } = useI18n()

// Element Plus 组件内置文案跟随 vue-i18n locale 切换
const elLocale = computed(() => {
  if (locale.value === 'zh-CN') return zhCn
  return en
})

// 根组件：全局浮层挂载点（CommandMenu）+ Cmd/K 全局快捷键注册
onMounted(() => {
  setupCommandMenuShortcut()
})

onUnmounted(() => {
  teardownCommandMenuShortcut()
})
</script>

<template>
  <ElConfigProvider :locale="elLocale">
    <RouterView />
    <CommandMenu />
  </ElConfigProvider>
</template>
