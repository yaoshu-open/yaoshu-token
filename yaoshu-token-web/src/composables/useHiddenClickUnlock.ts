import { ref } from 'vue'

export interface HiddenClickUnlockOptions {
  requiredClicks?: number
  disabled?: boolean
  onUnlock?: () => void
}

/**
 * N 次连续点击解锁隐藏功能（如 Doubao 渠道隐藏配置）。
 */
export function useHiddenClickUnlock(options: HiddenClickUnlockOptions = {}) {
  const requiredClicks = options.requiredClicks ?? 3
  const disabled = options.disabled ?? false
  const onUnlock = options.onUnlock

  const unlocked = ref<boolean>(false)
  let clickCount = 0

  function reset(): void {
    clickCount = 0
    unlocked.value = false
  }

  function handleClick(): void {
    if (disabled || unlocked.value) return
    clickCount += 1
    if (clickCount >= requiredClicks) {
      clickCount = 0
      unlocked.value = true
      onUnlock?.()
    }
  }

  return { unlocked, handleClick, reset }
}
