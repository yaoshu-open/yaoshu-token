<script setup lang="ts">
import { computed } from 'vue'
import { useTopNavLinks } from '@/composables/useTopNavLinks'
import { defaultTopNavLinks } from './config/top-nav-config'
import NavLinkItem from './NavLinkItem.vue'
import type { TopNavLink } from './types'

interface Props {
  links?: TopNavLink[]
  className?: string
}

const props = withDefaults(defineProps<Props>(), {
  links: undefined,
  className: ''
})
const emit = defineEmits<{
  (e: 'auth-required', link: TopNavLink): void
}>()

const dynamicLinks = useTopNavLinks()
const resolvedLinks = computed<TopNavLink[]>(() => {
  if (dynamicLinks.value.length > 0) return dynamicLinks.value
  return props.links ?? defaultTopNavLinks
})
</script>

<template>
  <nav
    class="public-navigation"
    :class="className"
  >
    <NavLinkItem
      v-for="(link, idx) in resolvedLinks"
      :key="`${link.href}-${idx}`"
      :link="link"
      class="public-navigation__item"
      @auth-required="emit('auth-required', $event)"
    />
  </nav>
</template>

<style scoped lang="scss">
.public-navigation {
  display: none;
  gap: var(--ys-spacing-1);
  align-items: center;

  @media (width >= 768px) {
    display: flex;
  }

  &__item {
    display: inline-flex;
    align-items: center;
    height: 36px;
    padding: 0 var(--ys-spacing-4);
    font-size: var(--el-font-size-base);
    background: transparent;
    border-radius: var(--el-border-radius-base);
    transition: background-color 0.2s;

    &:hover {
      color: var(--el-text-color-primary);
      background: var(--el-fill-color-light);
    }
  }
}
</style>
