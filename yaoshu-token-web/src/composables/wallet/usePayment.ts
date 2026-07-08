/**
 * 普通支付 + Stripe 支付 composable。
 * 处理支付方式：普通（alipay/wxpay 等）+ stripe。
 * Waffo/WaffoPancake/Creem 由各自独立 composable 处理。
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  calculateAmount,
  calculateStripeAmount,
  requestPayment,
  requestStripePayment,
} from '@/api/wallet'

/** 安全 HTTP 跳转校验：仅允许 http/https */
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

export function usePayment() {
  const { t } = useI18n()
  const amount = ref('0')
  const calculating = ref(false)
  const processing = ref(false)

  /** 根据支付方式计算金额 */
  async function calculatePaymentAmount(
    topupAmount: number,
    paymentType: string
  ): Promise<void> {
    calculating.value = true
    try {
      const req = { amount: topupAmount }
      if (paymentType === 'stripe') {
        amount.value = await calculateStripeAmount(req)
      } else {
        amount.value = await calculateAmount(req)
      }
    } catch {
      amount.value = '0'
    } finally {
      calculating.value = false
    }
  }

  /** 发起支付 */
  async function processPayment(
    topupAmount: number,
    paymentType: string
  ): Promise<boolean> {
    processing.value = true
    try {
      const req = { amount: topupAmount, paymentMethod: paymentType }
      if (paymentType === 'stripe') {
        const res = await requestStripePayment(req)
        const payLink = res.data?.payLink
        if (payLink && isSafeHttpCheckoutUrl(payLink)) {
          window.open(payLink, '_blank')
          return true
        }
        ElMessage.error(t('wallet.payment.payLinkError'))
        return false
      } else {
        const res = await requestPayment(req)
        // 普通支付：表单提交
        const url = res.url
        const params = res.data
        if (url && params && typeof params === 'object') {
          const form = document.createElement('form')
          form.action = url
          form.method = 'POST'
          const isSafari =
            navigator.userAgent.indexOf('Safari') > -1 &&
            navigator.userAgent.indexOf('Chrome') < 1
          if (!isSafari) form.target = '_blank'
          for (const [key, value] of Object.entries(params)) {
            const input = document.createElement('input')
            input.type = 'hidden'
            input.name = key
            input.value = String(value)
            form.appendChild(input)
          }
          document.body.appendChild(form)
          form.submit()
          document.body.removeChild(form)
          return true
        }
        ElMessage.error(t('wallet.payment.failed'))
        return false
      }
    } catch {
      ElMessage.error(t('wallet.payment.failed'))
      return false
    } finally {
      processing.value = false
    }
  }

  return {
    amount,
    calculating,
    processing,
    calculatePaymentAmount,
    processPayment,
  }
}
