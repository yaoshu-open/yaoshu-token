/**
 * Waffo 支付 composable。
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { requestWaffoPayment } from '@/api/wallet'

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

export function useWaffoPayment() {
  const { t } = useI18n()
  const processing = ref(false)

  async function processWaffoPayment(
    amount: number,
    payMethodIndex?: number
  ): Promise<boolean> {
    processing.value = true
    try {
      const res = await requestWaffoPayment({ amount, payMethodIndex })
      const data = res.data
      const paymentUrl =
        typeof data === 'object' && data !== null ? data.paymentUrl : undefined
      if (paymentUrl && isSafeHttpCheckoutUrl(paymentUrl)) {
        window.open(paymentUrl, '_blank')
        return true
      }
      ElMessage.error(t('wallet.payment.payLinkError'))
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
    processWaffoPayment,
  }
}
