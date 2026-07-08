<script setup lang="ts">
/**
 * 新年烟花彩蛋按钮（T-LT-02）。
 *
 * 农历新年期间（1月20日-2月20日）显示，点击触发烟花动画
 */
import { computed } from 'vue'
import confetti from 'canvas-confetti'

const isNewYear = computed((): boolean => {
  const now = new Date()
  const month = now.getMonth() + 1
  const day = now.getDate()
  return (month === 1 && day >= 20) || month === 2 && day <= 20
})

function handleFireworks(): void {
  const duration = 3000
  const end = Date.now() + duration
  const colors = ['#bb0000', '#ffffff', '#gold', '#ffc700', '#ff6b6b']

  function frame(): void {
    confetti({
      particleCount: 4,
      angle: 60,
      spread: 55,
      origin: { x: 0 },
      colors
    })
    confetti({
      particleCount: 4,
      angle: 120,
      spread: 55,
      origin: { x: 1 },
      colors
    })
    if (Date.now() < end) {
      requestAnimationFrame(frame)
    }
  }

  confetti({
    particleCount: 80,
    spread: 70,
    origin: { y: 0.6 },
    colors
  })
  frame()
}
</script>

<template>
  <button
    v-if="isNewYear"
    type="button"
    class="new-year-btn"
    aria-label="Happy New Year"
    @click="handleFireworks"
  >
    <span class="new-year-btn__emoji">🎉</span>
  </button>
</template>

<style scoped>
.new-year-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--el-border-radius-base);
  transition: background-color 0.2s, transform 0.2s;
}

.new-year-btn:hover {
  background: var(--el-fill-color-light);
  transform: scale(1.15);
}

.new-year-btn__emoji {
  font-size: 18px;
  line-height: 1;
}
</style>
