/**
 * 适配 Pinia。
 *
 * 核心概念：
 * - 系统 USD：内部货币单位
 * - 显示货币：管理员配置的展示货币（USD/CNY/TOKENS/CUSTOM）
 * - formatCurrencyFromUSD：USD → 显示货币（余额/配额）
 * - formatBillingCurrencyFromUSD：USD → 计费货币（永不显示 tokens，用于定价）
 */
import {
  useSystemConfigStore,
  DEFAULT_CURRENCY_CONFIG,
  type CurrencyConfig
} from '@/store/modules/system-config'

export interface CurrencyFormatOptions {
  digitsLarge?: number
  digitsSmall?: number
  abbreviate?: boolean
  minimumNonZero?: number
}

type DisplayMeta =
  | {
      kind: 'currency'
      symbol: string
      currencyCode: string
      exchangeRate: number
    }
  | {
      kind: 'custom'
      symbol: string
      exchangeRate: number
    }
  | {
      kind: 'tokens'
      quotaPerUnit: number
    }

const DEFAULT_FORMAT_OPTIONS: Required<CurrencyFormatOptions> = {
  digitsLarge: 2,
  digitsSmall: 4,
  abbreviate: true,
  minimumNonZero: 0
}

function getConfig(): CurrencyConfig {
  const store = useSystemConfigStore()
  return {
    ...DEFAULT_CURRENCY_CONFIG,
    ...store.currency
  }
}

function getDisplayMeta(config: CurrencyConfig): DisplayMeta {
  const displayType = config.quotaDisplayType as string
  switch (displayType) {
    case 'CNY':
      return {
        kind: 'currency',
        symbol: '¥',
        currencyCode: 'CNY',
        exchangeRate: config.usdExchangeRate
      }
    case 'CUSTOM':
      return {
        kind: 'custom',
        symbol: config.customCurrencySymbol,
        exchangeRate: config.customCurrencyExchangeRate
      }
    case 'TOKENS':
      return {
        kind: 'tokens',
        quotaPerUnit: config.quotaPerUnit
      }
    case 'USD':
    default:
      return {
        kind: 'currency',
        symbol: '$',
        currencyCode: 'USD',
        exchangeRate: 1
      }
  }
}

function getBillingDisplayMeta(config: CurrencyConfig): DisplayMeta {
  const meta = getDisplayMeta(config)
  // 仅 tokens 模式在定价场景回落到 USD（无货币含义），其他货币（USD/CNY/CUSTOM）按实际配置
  if (meta.kind === 'tokens') {
    return { kind: 'currency', symbol: '$', currencyCode: 'USD', exchangeRate: 1 }
  }
  return meta
}

function mergeOptions(
  options?: CurrencyFormatOptions
): Required<CurrencyFormatOptions> {
  if (!options) return DEFAULT_FORMAT_OPTIONS
  return {
    digitsLarge: options.digitsLarge ?? DEFAULT_FORMAT_OPTIONS.digitsLarge,
    digitsSmall: options.digitsSmall ?? DEFAULT_FORMAT_OPTIONS.digitsSmall,
    abbreviate: options.abbreviate ?? DEFAULT_FORMAT_OPTIONS.abbreviate,
    minimumNonZero:
      options.minimumNonZero ?? DEFAULT_FORMAT_OPTIONS.minimumNonZero
  }
}

function removeTrailingZeros(str: string): string {
  if (!str.includes('.')) return str
  return str.replace(/(\.[0-9]*?)0+$/, '$1').replace(/\.$/, '')
}

function formatNumberWithSuffix(
  value: number,
  digitsLarge: number,
  digitsSmall: number,
  abbreviate: boolean
): string {
  const abs = Math.abs(value)
  if (abbreviate && abs >= 1000) {
    const result = value / 1000
    return removeTrailingZeros(result.toFixed(1)) + 'k'
  }
  const digits = abs >= 1 ? digitsLarge : digitsSmall
  return removeTrailingZeros(value.toFixed(digits))
}

function adjustForMinimum(
  value: number,
  digits: number,
  minimumNonZero: number
): number {
  if (value === 0) return value
  const threshold = minimumNonZero > 0 ? minimumNonZero : Math.pow(10, -digits)
  const abs = Math.abs(value)
  if (abs > 0 && abs < threshold) {
    return value > 0 ? threshold : -threshold
  }
  return value
}

function formatCurrencyValue(
  value: number,
  options: Required<CurrencyFormatOptions>,
  meta: DisplayMeta
): string {
  if (meta.kind === 'tokens') {
    return formatNumberWithSuffix(
      value,
      options.digitsLarge,
      options.digitsSmall,
      options.abbreviate
    )
  }

  const digits = Math.abs(value) >= 1 ? options.digitsLarge : options.digitsSmall
  const adjustedValue = adjustForMinimum(value, digits, options.minimumNonZero)

  if (meta.kind === 'currency') {
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency: meta.currencyCode,
      currencyDisplay: 'narrowSymbol',
      minimumFractionDigits: 0,
      maximumFractionDigits: digits
    }).format(adjustedValue)
  }

  const decimal = new Intl.NumberFormat(undefined, {
    minimumFractionDigits: 0,
    maximumFractionDigits: digits
  }).format(adjustedValue)

  return `${meta.symbol} ${decimal}`
}

/** 获取当前货币配置与显示元信息 */
export function getCurrencyDisplay() {
  const config = getConfig()
  const meta = getDisplayMeta(config)
  return { config, meta }
}

/** USD 金额 → 显示货币（余额/配额场景） */
export function formatCurrencyFromUSD(
  amountUSD: number | null | undefined,
  options?: CurrencyFormatOptions
): string {
  if (amountUSD == null || Number.isNaN(amountUSD)) return '-'

  const { config, meta } = getCurrencyDisplay()
  const merged = mergeOptions(options)

  if (meta.kind === 'tokens') {
    const tokens = amountUSD * config.quotaPerUnit
    return formatNumberWithSuffix(tokens, 0, merged.digitsSmall, merged.abbreviate)
  }

  const value = amountUSD * meta.exchangeRate
  return formatCurrencyValue(value, merged, meta)
}

/** USD 金额 → 计费货币（定价场景，永不显示 tokens） */
export function formatBillingCurrencyFromUSD(
  amountUSD: number | null | undefined,
  options?: CurrencyFormatOptions
): string {
  if (amountUSD == null || Number.isNaN(amountUSD)) return '-'

  const { config } = getCurrencyDisplay()
  const meta = getBillingDisplayMeta(config)
  const merged = mergeOptions(options)
  const value =
    meta.kind === 'currency' || meta.kind === 'custom'
      ? amountUSD * meta.exchangeRate
      : amountUSD

  return formatCurrencyValue(value, merged, meta)
}

/** raw quota（token 单位）→ 显示货币 */
export function formatQuotaWithCurrency(
  quota: number | null | undefined,
  options?: CurrencyFormatOptions
): string {
  if (quota == null || Number.isNaN(quota)) return '-'
  const { config } = getCurrencyDisplay()
  const amountUSD = quota / config.quotaPerUnit
  return formatCurrencyFromUSD(amountUSD, options)
}

/**
 * raw quota → 定价/消费货币（强制显示货币符号）。
 * 用于 dashboard 消费统计、调用日志金额等「必须显示货币」的场景：
 * TOKENS 模式不回落为裸 token 数，而是按 getBillingDisplayMeta 强制 USD（或 CNY/CUSTOM 实际配置）。
 */
export function formatQuotaBilling(
  quota: number | null | undefined,
  options?: CurrencyFormatOptions
): string {
  if (quota == null || Number.isNaN(quota)) return '-'
  const { config } = getCurrencyDisplay()
  const meta = getBillingDisplayMeta(config)
  const amountUSD = quota / config.quotaPerUnit
  const merged = mergeOptions(options)
  const value =
    meta.kind === 'currency' || meta.kind === 'custom'
      ? amountUSD * meta.exchangeRate
      : amountUSD
  return formatCurrencyValue(value, merged, meta)
}

/** 获取当前货币标签 */
export function getCurrencyLabel(): string {
  const { config, meta } = getCurrencyDisplay()
  if (meta.kind === 'tokens') return 'Tokens'
  switch (config.quotaDisplayType as string) {
    case 'CNY':
      return 'CNY'
    case 'CUSTOM':
      return meta.kind === 'custom' ? meta.symbol : 'Custom'
    case 'USD':
    default:
      return 'USD'
  }
}

/** 是否启用货币显示（非 tokens 模式） */
export function isCurrencyDisplayEnabled(): boolean {
  const { meta } = getCurrencyDisplay()
  return meta.kind !== 'tokens'
}
