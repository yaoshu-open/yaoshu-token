<script setup lang="ts">
/**
 * 日志列表（行渲染 + isAtBottom 检测 + scroll-to-bottom FAB）。
 *
 * 关键改进（vs classic）：用户向上滚动查看历史时不强制滚回（isAtBottom 检测）。
 */
import { nextTick, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  lines: string[]
  isStreaming: boolean
  following: boolean
  isAtBottom: boolean
}>()

const emit = defineEmits<{
  (e: 'scrollState', isAtBottom: boolean): void
}>()

const { t } = useI18n()

const containerRef = ref<HTMLDivElement | null>(null)
const BOTTOM_THRESHOLD = 5

function isAtBottomNow(el: HTMLDivElement): boolean {
  return el.scrollTop + el.clientHeight >= el.scrollHeight - BOTTOM_THRESHOLD
}

function handleScroll(): void {
  const el = containerRef.value
  if (!el) return
  emit('scrollState', isAtBottomNow(el))
}

function scrollToBottom(): void {
  const el = containerRef.value
  if (!el) return
  void nextTick(() => {
    el.scrollTop = el.scrollHeight
    emit('scrollState', true)
  })
}

defineExpose({ scrollToBottom })

onMounted(() => {
  scrollToBottom()
})
</script>

<template>
  <div class="deployment-log-list-wrap">
    <div
      ref="containerRef"
      class="deployment-log-list"
      @scroll="handleScroll"
    >
      <div
        v-for="(line, idx) in lines"
        :key="`log-${idx}-${line.slice(0, 20)}`"
        class="deployment-log-list__line"
      >
        {{ line }}
      </div>
    </div>
    <el-button
      v-if="!isAtBottom && props.lines.length > 0"
      class="deployment-log-list__fab"
      type="primary"
      size="small"
      circle
      @click="scrollToBottom"
    >
      <i class="i-ep-bottom" />
    </el-button>
    <div
      v-if="following && isStreaming"
      class="deployment-log-list__streaming"
    >
      <i class="i-ep-loading" />
      {{ t('deployment.logs.streaming') }}
    </div>
  </div>
</template>

<style scoped lang="scss">
.deployment-log-list-wrap {
  position: relative;
  height: 100%;
}

.deployment-log-list {
  height: 100%;
  max-height: 50vh;
  padding: var(--ys-spacing-2) 0;
  overflow-y: auto;
  font-family: var(--el-font-family-monospace, monospace);
  font-size: var(--el-font-size-small);
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);

  &__line {
    padding: var(--ys-spacing-1) var(--ys-spacing-3);
    word-break: break-all;
    white-space: pre-wrap;
    border-bottom: 1px solid var(--el-fill-color-light);
  }
}

.deployment-log-list__fab {
  position: absolute;
  right: 16px;
  bottom: 16px;
  z-index: 2;
  box-shadow: var(--el-box-shadow-light);
}

.deployment-log-list__streaming {
  position: absolute;
  top: 8px;
  left: 8px;
  display: flex;
  gap: var(--ys-spacing-1);
  align-items: center;
  padding: 2px var(--ys-spacing-2);
  font-size: var(--el-font-size-extra-small);
  color: var(--el-color-primary);
  background: var(--el-bg-color);
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 9999px;
}
</style>
