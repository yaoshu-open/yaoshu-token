<script setup lang="ts">
import { computed } from 'vue'

/**
 * 周期环比增长百分比渲染。
 * 正值绿色 ↑，负值红色 ↓，零值灰色 0%。
 */
interface Props {
  value: number
}
const props = defineProps<Props>()

const isUp = computed(() => Number.isFinite(props.value) && props.value > 0)
const isZero = computed(
  () => !Number.isFinite(props.value) || props.value === 0
)
const display = computed(() => {
  if (isZero.value) return '0%'
  const abs = Math.abs(props.value)
  return `${isUp.value ? '↑' : '↓'}${abs.toFixed(abs >= 100 ? 0 : 1)}%`
})
</script>

<template>
  <span
    class="growth-text"
    :class="{
      'growth-text--up': isUp,
      'growth-text--down': !isUp && !isZero
    }"
  >{{ display }}</span>
</template>

<style scoped lang="scss">
.growth-text {
  font-family: var(--uno-font-mono, ui-monospace, monospace);
  font-variant-numeric: tabular-nums;

  &--up {
    color: #059669; // emerald-600
  }

  &--down {
    color: #e11d48; // rose-600
  }

  :global(.dark) &--up {
    color: #34d399; // emerald-400
  }

  :global(.dark) &--down {
    color: #fb7185; // rose-400
  }
}
</style>
