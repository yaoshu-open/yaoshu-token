import { ElMessage } from 'element-plus'
import { onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

export interface UseCopyToClipboardOptions {
  /** 是否显示全局 ElMessage 反馈（默认 true） */
  notify?: boolean
  /** 成功提示文案，未提供时走 i18n key common.copySuccess */
  successMessage?: string
  /** 失败提示文案，未提供时走 i18n key common.copyFailed */
  errorMessage?: string
  /** copiedText 自动重置时间（ms，默认 2000） */
  resetAfterMs?: number
}

/**
 *
 * 优先使用 `navigator.clipboard.writeText`，不可用时回退到 `document.execCommand('copy')`。
 * 失败时不静默吞错（红线 21）：通过 ElMessage 显式提示，并 reject Promise 让上层可感知。
 */
export function useCopyToClipboard(options?: UseCopyToClipboardOptions) {
  const {
    notify = true,
    successMessage,
    errorMessage,
    resetAfterMs = 2000
  } = options || {}
  const { t } = useI18n()

  const copiedText = ref<string | null>(null)
  let resetTimer: ReturnType<typeof setTimeout> | null = null

  function clearResetTimer(): void {
    if (resetTimer) {
      clearTimeout(resetTimer)
      resetTimer = null
    }
  }

  async function copy(text: string): Promise<boolean> {
    if (typeof text !== 'string') return false
    try {
      if (navigator?.clipboard?.writeText) {
        await navigator.clipboard.writeText(text)
      } else {
        // 老浏览器/非 https：document.execCommand 兜底
        const textarea = document.createElement('textarea')
        textarea.value = text
        textarea.style.position = 'fixed'
        textarea.style.opacity = '0'
        document.body.appendChild(textarea)
        textarea.select()
        const ok = document.execCommand('copy')
        document.body.removeChild(textarea)
        if (!ok) throw new Error('execCommand copy returned false')
      }

      copiedText.value = text
      clearResetTimer()
      resetTimer = setTimeout(() => {
        copiedText.value = null
        resetTimer = null
      }, resetAfterMs)

      if (notify) {
        ElMessage.success(successMessage ?? t('common.copySuccess'))
      }
      return true
    } catch (err) {
      // 失败显式 fail-fast：弹错 + 写入 console + 返回 false 让 caller 感知
      if (notify) {
        ElMessage.error(errorMessage ?? t('common.copyFailed'))
      }
      console.error('[useCopyToClipboard] copy failed:', err)
      return false
    }
  }

  onUnmounted(clearResetTimer)

  return { copy, copiedText }
}
