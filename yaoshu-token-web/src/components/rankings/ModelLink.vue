<script setup lang="ts">
/**
 * 模型名链接，导航到 pricing 独立模型详情页。
 */
defineProps<{
  modelName: string
}>()
</script>

<template>
  <RouterLink
    :to="{ name: 'PricingModelDetails', params: { modelId: modelName } }"
    class="entity-link entity-link--model"
  >
    <slot>{{ modelName }}</slot>
  </RouterLink>
</template>

<style scoped lang="scss">
.entity-link {
  text-decoration: underline;
  text-decoration-thickness: 1px;
  text-decoration-color: color-mix(in srgb, currentcolor 30%, transparent);
  text-underline-offset: 4px;
  cursor: pointer;
  transition: text-decoration-color 0.15s;

  &:hover {
    text-decoration-color: currentcolor;
  }
}
</style>
