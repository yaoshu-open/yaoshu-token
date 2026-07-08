<script setup lang="ts">
// 后续模块可复用，放入 components/common/。
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  group: string
  size?: 'sm' | 'md'
}>(), {
  size: 'md'
})

// 基于分组名哈希生成色相，确保同一分组每次渲染颜色一致
const hue = computed(() => {
  let hash = 0
  for (let i = 0; i < props.group.length; i++) {
    hash = (hash * 31 + props.group.charCodeAt(i)) | 0
  }
  return Math.abs(hash) % 360
})

const style = computed(() => ({
  backgroundColor: `hsl(${hue.value}, 65%, 92%)`,
  color: `hsl(${hue.value}, 45%, 35%)`,
  borderColor: `hsl(${hue.value}, 40%, 80%)`
}))
</script>

<template>
  <span
    class="group-badge"
    :class="{ 'group-badge--sm': size === 'sm' }"
    :style="style"
  >
    {{ group }}
  </span>
</template>

<style scoped lang="scss">
.group-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px var(--ys-spacing-2);
  font-size: var(--ys-font-size-xs);
  font-weight: 500;
  line-height: 1.4;
  white-space: nowrap;
  border: 1px solid;
  border-radius: var(--ys-radius-sm);

  &--sm {
    padding: 1px 6px;
    font-size: 11px;
  }
}
</style>
