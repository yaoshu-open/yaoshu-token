// 适配：@/lib/currency → @/utils/currency
// 金额体系统一：定价场景用 formatBillingCurrencyFromUSD（强制货币符号，TOKENS 模式回落 USD/CNY）
import { formatBillingCurrencyFromUSD } from '@/utils/currency'
import { QUOTA_TYPE_VALUES, TOKEN_UNIT_DIVISORS } from '@/api/pricing/constants'
import type { PricingModel, TokenUnit, PriceType } from '@/api/pricing/types'

/** 去除格式化价格字符串的尾部零 */
export function stripTrailingZeros(formatted: string): string {
  const match = formatted.match(/^([^\d-]*)([-\d,]+\.?\d*)(k?)$/)
  if (!match) return formatted
  const [, symbol, number, suffix] = match
  const cleanNumber = number.replace(/,/g, '')
  const parsed = parseFloat(cleanNumber)
  if (isNaN(parsed)) return formatted
  let result = parsed.toString()
  if (result.includes('e')) {
    result = parsed.toFixed(20).replace(/\.?0+$/, '')
  }
  return `${symbol}${result}${suffix}`
}

/** 从启用分组中找最小分组比率 */
function getMinGroupRatio(
  enableGroups: string[],
  groupRatio: Record<string, number>
): number {
  if (enableGroups.length === 0) return 1
  let minRatio = Number.POSITIVE_INFINITY
  for (const group of enableGroups) {
    const ratio = groupRatio[group]
    if (ratio !== undefined && ratio < minRatio) {
      minRatio = ratio
    }
  }
  return minRatio === Number.POSITIVE_INFINITY ? 1 : minRatio
}

function hasRatio(value: number | null | undefined): boolean {
  return value !== undefined && value !== null && Number.isFinite(Number(value))
}

/** 计算 token 价格（USD） */
function calculateTokenPrice(
  model: PricingModel,
  type: PriceType,
  ratio: number
): number {
  const base = model.modelRatio * 2 * ratio
  switch (type) {
    case 'input':
      return base
    case 'output':
      return base * model.completionRatio
    case 'cache':
      return hasRatio(model.cacheRatio) ? base * Number(model.cacheRatio) : NaN
    case 'create_cache':
      return hasRatio(model.createCacheRatio)
        ? base * Number(model.createCacheRatio)
        : NaN
    case 'image':
      return hasRatio(model.imageRatio) ? base * Number(model.imageRatio) : NaN
    case 'audio_input':
      return hasRatio(model.audioRatio) ? base * Number(model.audioRatio) : NaN
    case 'audio_output':
      return hasRatio(model.audioRatio) && hasRatio(model.audioCompletionRatio)
        ? base * Number(model.audioRatio) * Number(model.audioCompletionRatio)
        : NaN
  }
}

/** 应用充值费率 */
function applyRechargeRate(
  price: number,
  showWithRecharge: boolean,
  priceRate: number,
  usdExchangeRate: number
): number {
  if (!showWithRecharge) return price
  return (price * priceRate) / usdExchangeRate
}

/** 格式化 token 计费价格 */
export function formatPrice(
  model: PricingModel,
  type: PriceType,
  tokenUnit: TokenUnit,
  showWithRecharge = false,
  priceRate = 1,
  usdExchangeRate = 1
): string {
  if (model.quotaType === QUOTA_TYPE_VALUES.REQUEST) return '-'

  const enableGroups = Array.isArray(model.enableGroup) ? model.enableGroup : []
  const groupRatio = model.groupRatio || {}
  const minRatio = getMinGroupRatio(enableGroups, groupRatio)

  let priceInUSD = calculateTokenPrice(model, type, minRatio)
  priceInUSD = applyRechargeRate(priceInUSD, showWithRecharge, priceRate, usdExchangeRate)

  const price = priceInUSD / TOKEN_UNIT_DIVISORS[tokenUnit]
  return formatBillingCurrencyFromUSD(price, {
    digitsLarge: 4,
    digitsSmall: 6,
    abbreviate: false
  })
}

/** 格式化指定分组的 token 价格 */
export function formatGroupPrice(
  model: PricingModel,
  group: string,
  type: PriceType,
  tokenUnit: TokenUnit,
  showWithRecharge = false,
  priceRate = 1,
  usdExchangeRate = 1,
  groupRatio: Record<string, number>
): string {
  if (model.quotaType === QUOTA_TYPE_VALUES.REQUEST) return '-'

  const ratio = groupRatio[group] || 1
  let priceInUSD = calculateTokenPrice(model, type, ratio)
  priceInUSD = applyRechargeRate(priceInUSD, showWithRecharge, priceRate, usdExchangeRate)

  const price = priceInUSD / TOKEN_UNIT_DIVISORS[tokenUnit]
  return formatBillingCurrencyFromUSD(price, {
    digitsLarge: 4,
    digitsSmall: 6,
    abbreviate: false
  })
}

/** 格式化按次计费的固定价格（指定分组） */
export function formatFixedPrice(
  model: PricingModel,
  group: string,
  showWithRecharge = false,
  priceRate = 1,
  usdExchangeRate = 1,
  groupRatio: Record<string, number>
): string {
  if (model.quotaType !== QUOTA_TYPE_VALUES.REQUEST) return '-'

  const ratio = groupRatio[group] || 1
  let priceInUSD = (model.modelPrice || 0) * ratio
  priceInUSD = applyRechargeRate(priceInUSD, showWithRecharge, priceRate, usdExchangeRate)

  return formatBillingCurrencyFromUSD(priceInUSD, {
    digitsLarge: 4,
    digitsSmall: 4,
    abbreviate: false
  })
}

/** 格式化按次计费的固定价格（最小价格） */
export function formatRequestPrice(
  model: PricingModel,
  showWithRecharge = false,
  priceRate = 1,
  usdExchangeRate = 1
): string {
  if (model.quotaType !== QUOTA_TYPE_VALUES.REQUEST) return '-'

  const enableGroups = Array.isArray(model.enableGroup) ? model.enableGroup : []
  const groupRatio = model.groupRatio || {}
  const minRatio = getMinGroupRatio(enableGroups, groupRatio)

  let priceInUSD = (model.modelPrice || 0) * minRatio
  priceInUSD = applyRechargeRate(priceInUSD, showWithRecharge, priceRate, usdExchangeRate)

  return stripTrailingZeros(formatBillingCurrencyFromUSD(priceInUSD, {
    digitsLarge: 4,
    digitsSmall: 4,
    abbreviate: false
  }))
}
