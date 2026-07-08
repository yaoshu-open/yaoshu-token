import { onUnmounted, ref } from 'vue'

export interface UseCountdownOptions {
  initialSeconds?: number
  autoStart?: boolean
}

/**
 * 倒计时：
 * - secondsLeft 当前剩余秒数
 * - isActive 是否运行中
 * - start(seconds?) 启动；不传则用 initialSeconds
 * - stop() 停止并保留当前剩余值
 * - reset() 停止并恢复到 initialSeconds
 */
export function useCountdown(options: UseCountdownOptions = {}) {
  const { initialSeconds = 30, autoStart = false } = options

  const secondsLeft = ref<number>(initialSeconds)
  const isActive = ref<boolean>(false)
  let timer: ReturnType<typeof setInterval> | null = null

  function clearTimer(): void {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  function stop(): void {
    clearTimer()
    isActive.value = false
  }

  function start(seconds?: number): void {
    const total = seconds ?? initialSeconds
    secondsLeft.value = total
    isActive.value = true
    clearTimer()
    timer = setInterval(() => {
      if (secondsLeft.value <= 1) {
        clearTimer()
        isActive.value = false
        secondsLeft.value = initialSeconds
        return
      }
      secondsLeft.value -= 1
    }, 1000)
  }

  function reset(): void {
    stop()
    secondsLeft.value = initialSeconds
  }

  if (autoStart) start()

  onUnmounted(clearTimer)

  return { secondsLeft, isActive, start, stop, reset }
}
