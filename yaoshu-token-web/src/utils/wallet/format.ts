import { DEFAULT_DISCOUNT_RATE } from '@/api/wallet/constants'

/** Creem 价格格式化（带货币符号） */
export function formatCreemPrice(price: number, currency: 'USD' | 'EUR'): string {
  const symbol = currency === 'EUR' ? '€' : '$'
  return `${symbol}${Number(price).toFixed(2)}`
}

/** 大额 quota 缩写（K/M 后缀） */
export function formatQuotaShort(quota: number): string {
  if (quota >= 1000000) return `${(quota / 1000000).toFixed(1)}M`
  if (quota >= 1000) return `${(quota / 1000).toFixed(1)}K`
  return quota.toString()
}

/** 通用数字格式化（用于金额、quota 等） */
export function formatNumber(value: number | string): string {
  const numeric = typeof value === 'number' ? value : Number.parseFloat(String(value))
  if (!Number.isFinite(numeric)) return '-'
  return new Intl.NumberFormat(undefined, {
    minimumFractionDigits: 0,
    maximumFractionDigits: Math.abs(numeric) >= 1 ? 2 : 4,
  }).format(numeric)
}

/** 本地货币格式化（已计算后的金额） */
export function formatCurrency(amount: number | string): string {
  return formatNumber(amount)
}

/** 折扣标签（如 "20% OFF"），无折扣返回空字符串 */
export function getDiscountLabel(discount: number): string {
  if (discount >= DEFAULT_DISCOUNT_RATE) return ''
  const off = Math.round((1 - discount) * 100)
  return `${off}% OFF`
}

/** 预设额度定价计算 */
export function calculatePresetPricing(
  presetValue: number,
  priceRatio: number,
  discount: number,
  usdExchangeRate: number = 1
) {
  const originalPrice = presetValue * priceRatio
  const actualPrice = originalPrice * discount
  const savedAmount = originalPrice - actualPrice
  const hasDiscount = discount < 1.0
  const displayValue = presetValue * usdExchangeRate
  return { displayValue, originalPrice, actualPrice, savedAmount, hasDiscount }
}
