import { onUnmounted, ref, watch, type Ref } from 'vue'

/**
 * 防抖 ref：源 ref 变化后延迟 delay ms 同步到目标 ref。
 */
export function useDebounce<T>(value: Ref<T>, delay = 500): Ref<T> {
  const debounced = ref(value.value) as Ref<T>
  let timer: ReturnType<typeof setTimeout> | null = null

  const stopWatch = watch(value, (next) => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      debounced.value = next
      timer = null
    }, delay)
  })

  onUnmounted(() => {
    if (timer) clearTimeout(timer)
    stopWatch()
  })

  return debounced
}
