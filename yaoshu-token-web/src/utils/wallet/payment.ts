/**
 * camelCase 约束：所有 TopupInfo 字段使用 camelCase。
 */
import type { PresetAmount, TopupInfo } from '@/api/wallet/types'

/** 支付方式类型 */
export const PAYMENT_TYPES = {
  ALIPAY: 'alipay',
  WECHAT: 'wxpay',
  STRIPE: 'stripe',
  CREEM: 'creem',
  WAFFO: 'waffo',
  WAFFO_PANCAKE: 'waffo_pancake',
} as const

/** 默认支付方式 */
export const DEFAULT_PAYMENT_TYPE = PAYMENT_TYPES.ALIPAY

/** 默认最小充值额度 */
export const DEFAULT_MIN_TOPUP = 1

/** 默认预设额度倍数 */
export const DEFAULT_PRESET_MULTIPLIERS = [1, 5, 10, 30, 50, 100, 300, 500]

/** 判断是否 Stripe 支付 */
export function isStripePayment(paymentType: string): boolean {
  return paymentType === PAYMENT_TYPES.STRIPE
}

/** 判断是否 Waffo Pancake 支付（独立 checkoutUrl 流程） */
export function isWaffoPancakePayment(paymentType: string): boolean {
  return paymentType === PAYMENT_TYPES.WAFFO_PANCAKE
}

/** 获取默认支付方式 */
export function getDefaultPaymentType(topupInfo: TopupInfo | null): string {
  if (!topupInfo) return DEFAULT_PAYMENT_TYPE
  if (topupInfo.payMethods?.length > 0) return topupInfo.payMethods[0].type
  if (topupInfo.enableStripeTopup) return PAYMENT_TYPES.STRIPE
  if (topupInfo.enableWaffoTopup) return PAYMENT_TYPES.WAFFO
  if (topupInfo.enableWaffoPancakeTopup) return PAYMENT_TYPES.WAFFO_PANCAKE
  return DEFAULT_PAYMENT_TYPE
}

/** 获取最小充值额度 */
export function getMinTopupAmount(topupInfo: TopupInfo | null): number {
  if (!topupInfo) return DEFAULT_MIN_TOPUP
  if (topupInfo.enableOnlineTopup) return topupInfo.minTopup
  if (topupInfo.enableStripeTopup) return topupInfo.stripeMinTopup
  if (topupInfo.enableWaffoTopup) return topupInfo.waffoMinTopup || DEFAULT_MIN_TOPUP
  if (topupInfo.enableWaffoPancakeTopup) return topupInfo.waffoPancakeMinTopup || DEFAULT_MIN_TOPUP
  return DEFAULT_MIN_TOPUP
}

/** 根据最小充值额度生成预设额度 */
export function generatePresetAmounts(minAmount: number): PresetAmount[] {
  return DEFAULT_PRESET_MULTIPLIERS.map((multiplier) => ({
    value: minAmount * multiplier,
  }))
}

/** 合并预设额度与折扣 */
export function mergePresetAmounts(
  amountOptions: number[],
  discounts: Record<number, number>
): PresetAmount[] {
  if (!amountOptions || amountOptions.length === 0) return []
  return amountOptions.map((amount) => ({
    value: amount,
    discount: discounts[amount] || 1.0,
  }))
}
