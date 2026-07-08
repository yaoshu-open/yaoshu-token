<script setup lang="ts">
/**
 * LoaderShimmer — 流式加载占位动画（shimmer 效果）。
 * 用于 AI 回复等待时的骨架屏占位。
 */
withDefaults(defineProps<{
  /** 行数 */
  lines?: number
}>(), {
  lines: 3
})
</script>

<template>
  <div class="loader-shimmer">
    <div
      v-for="i in lines"
      :key="i"
      class="loader-shimmer__line"
      :style="{ width: i === lines ? '60%' : '100%' }"
    />
  </div>
</template>

<style scoped lang="scss">
.loader-shimmer {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-2);
  padding: var(--ys-spacing-2) 0;

  &__line {
    height: 14px;
    background: linear-gradient(
      90deg,
      var(--el-fill-color-light) 25%,
      var(--el-fill-color) 50%,
      var(--el-fill-color-light) 75%
    );
    background-size: 200% 100%;
    border-radius: var(--ys-radius-sm);
    animation: loader-shimmer 1.5s ease-in-out infinite;
  }
}

@keyframes loader-shimmer {
  0% {
    background-position: 200% 0;
  }

  100% {
    background-position: -200% 0;
  }
}
</style>
