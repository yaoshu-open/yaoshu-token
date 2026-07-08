/**
 * Creem 支付 composable。
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { requestCreemPayment } from '@/api/wallet'

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

export function useCreemPayment() {
  const { t } = useI18n()
  const processing = ref(false)

  async function processCreemPayment(productId: string): Promise<boolean> {
    if (!productId) {
      ElMessage.error(t('wallet.payment.productError'))
      return false
    }
    processing.value = true
    try {
      const res = await requestCreemPayment({
        productId,
        paymentMethod: 'creem',
      })
      const checkoutUrl = res.data?.checkoutUrl
      if (checkoutUrl && isSafeHttpCheckoutUrl(checkoutUrl)) {
        window.open(checkoutUrl, '_blank')
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
    processCreemPayment,
  }
}
