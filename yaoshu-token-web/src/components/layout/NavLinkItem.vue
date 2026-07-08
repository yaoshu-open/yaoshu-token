<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { TopNavLink } from './types'

interface Props {
  link: TopNavLink
}

const props = defineProps<Props>()
const route = useRoute()
const router = useRouter()

// 当前页激活判断：外链不适用；activeUrls 显式匹配优先；首页仅精确匹配，其余前缀匹配子路由
const isActive = computed(() => {
  if (props.link.external) return false
  if (typeof props.link.isActive === 'boolean') return props.link.isActive
  const href = props.link.href
  const path = route.path
  // activeUrls 显式匹配（如控制台子页面不共享 href 前缀）
  if (props.link.activeUrls?.some(url => path === url || path.startsWith(url + '/'))) {
    return true
  }
  if (href === '/') return path === '/'
  return path === href || path.startsWith(href + '/')
})

const linkClass = computed(() => [
  'nav-link-item',
  { 'nav-link-item--disabled': props.link.disabled },
  { 'nav-link-item--active': isActive.value }
])

const emit = defineEmits<{
  (e: 'auth-required', link: TopNavLink): void
}>()

function handleClick() {
  if (props.link.disabled) return

  if (props.link.external) {
    window.open(props.link.href, '_blank', 'noopener, noreferrer')
    return
  }

  // 未登录且链接需要登录：emit 事件由父组件处理（弹出登录提示）
  if (props.link.requiresAuth) {
    emit('auth-required', props.link)
    return
  }

  router.push(props.link.href)
}
</script>

<template>
  <!-- 外链：渲染 <a> 以便 target=_blank + rel=noopener -->
  <a
    v-if="link.external"
    :href="link.href"
    target="_blank"
    rel="noopener noreferrer"
    :class="linkClass"
    :aria-disabled="link.disabled"
    @click.prevent="handleClick"
  >{{ link.title }}</a>

  <!-- 内部链接：走 vue-router（默认插槽允许消费方追加图标） -->
  <a
    v-else
    :href="link.href"
    :class="linkClass"
    :aria-disabled="link.disabled"
    @click.prevent="handleClick"
  >
    <slot>{{ link.title }}</slot>
  </a>
</template>

<style scoped lang="scss">
.nav-link-item {
  color: var(--el-text-color-secondary);
  text-decoration: none;
  transition: color 0.2s, background-color 0.2s;

  &:hover {
    color: var(--el-text-color-primary);
  }

  &--active {
    color: var(--el-color-primary);
    font-weight: 600;
  }

  &.nav-link-item--active {
    background: var(--el-color-primary-light-9);
    border-radius: var(--ys-radius-full);
  }

  &.nav-link-item--active:hover {
    color: var(--el-color-primary);
    background: var(--el-color-primary-light-8);
  }

  &--disabled {
    pointer-events: none;
    opacity: 0.5;
  }
}
</style>
