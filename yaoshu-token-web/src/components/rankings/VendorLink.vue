<script setup lang="ts">
/**
 * 供应商名链接，导航到 pricing 模型广场并按供应商过滤。
 */
defineProps<{
  vendor: string
}>()
</script>

<template>
  <RouterLink
    :to="{ path: '/pricing', query: { vendor } }"
    class="entity-link entity-link--vendor"
  >
    <slot>{{ vendor }}</slot>
  </RouterLink>
</template>

<style scoped lang="scss">
.entity-link {
  text-decoration: underline;
  text-decoration-thickness: 1px;
  text-decoration-color: color-mix(in srgb, currentcolor 40%, transparent);
  text-underline-offset: 2px;
  cursor: pointer;
  transition: text-decoration-color 0.15s;

  &:hover {
    text-decoration-color: currentcolor;
  }
}
</style>
