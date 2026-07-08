import { onUnmounted, ref, watch, type Ref } from 'vue'

/**
 * 最小加载时间防闪烁。
 *
 * 行为：
 * - source.value 由 false → true：立即把内部 flag 置 true，记录起始时间
 * - source.value 由 true → false：经过的时间 ≥ minimumTime → 立即关闭；否则延迟剩余时间后关闭
 */
export function useMinimumLoadingTime(
  source: Ref<boolean>,
  minimumTime = 1000
): Ref<boolean> {
  const showSkeleton = ref<boolean>(source.value)
  let loadingStart = Date.now()
  let timer: ReturnType<typeof setTimeout> | null = null

  function clearTimer(): void {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
  }

  watch(
    source,
    (loading) => {
      clearTimer()
      if (loading) {
        loadingStart = Date.now()
        showSkeleton.value = true
        return
      }
      const elapsed = Date.now() - loadingStart
      const remaining = Math.max(0, minimumTime - elapsed)
      if (remaining === 0) {
        showSkeleton.value = false
      } else {
        timer = setTimeout(() => {
          showSkeleton.value = false
          timer = null
        }, remaining)
      }
    },
    { immediate: true }
  )

  onUnmounted(clearTimer)

  return showSkeleton
}
