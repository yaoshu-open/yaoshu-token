/**
 * WaffoPancake 支付 composable。
 * 安全铁律：checkout_url 必须通过 isSafeHttpCheckoutUrl 校验，仅 http/https。
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { requestWaffoPancakePayment } from '@/api/wallet'

function isSafeHttpCheckoutUrl(value: string): boolean {
  const trimmed = (value || '').trim()
  if (!trimmed) return false
  try {
    const u = new URL(trimmed)
    return u.protocol === 'http:' || u.protocol === 'https:'
  } catch {
    return false
  }
}

export function useWaffoPancakePayment() {
  const { t } = useI18n()
  const processing = ref(false)

  async function processWaffoPancakePayment(amount: number): Promise<boolean> {
    processing.value = true
    try {
      const res = await requestWaffoPancakePayment({ amount })
      const checkoutUrl = res.data?.checkoutUrl
      if (checkoutUrl && isSafeHttpCheckoutUrl(checkoutUrl)) {
        // In-tab redirect（非 window.open）—— await 后丢失用户手势上下文，popup blocker 会拦截
        window.location.href = checkoutUrl
        return true
      }
      if (checkoutUrl) {
        ElMessage.error(t('wallet.payment.unsafeUrl'))
      } else {
        ElMessage.error(t('wallet.payment.failed'))
      }
      return false
    } catch {
      ElMessage.error(t('wallet.payment.failed'))
      return false
    } finally {
      processing.value = false
    }
  }

  return {
    processing,
    processWaffoPancakePayment,
  }
}
