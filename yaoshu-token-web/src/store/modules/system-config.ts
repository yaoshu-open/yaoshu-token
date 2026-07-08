import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getStatus } from '@/api/system'
import { simplifyConfig } from '@/plugins/spi/registry'
import type { SystemStatus, SystemStatusData } from '@/api/system/types'
import defaultLogoUrl from '@/assets/logo/logo.png'

// 币种显示类型：与后端契约 quota_display_type 字段对齐
export type CurrencyDisplayType = 'TOKENS' | 'USD' | 'CNY' | 'CUSTOM'

export interface CurrencyConfig {
  displayInCurrency: boolean
  quotaDisplayType: CurrencyDisplayType
  /** USD 基准：1 USD = quotaPerUnit quota（后端 CommonConstants 定义，套餐恒定 USD 定价） */
  quotaPerUnit: number
  /** USD→CNY 展示汇率（仅展示层用，不参与计费） */
  usdExchangeRate: number
  customCurrencySymbol: string
  customCurrencyExchangeRate: number
}

// 默认值与 default `stores/system-config-store.ts` DEFAULT_CURRENCY_CONFIG 1:1
export const DEFAULT_CURRENCY_CONFIG: CurrencyConfig = {
  displayInCurrency: false,
  quotaDisplayType: 'TOKENS',
  quotaPerUnit: 500000, // 1 USD = 500000 quota（$0.002/1K tokens → 500*1000）
  usdExchangeRate: 7,   // 仅展示层 USD→CNY 换算，不参与计费
  customCurrencySymbol: '¥',
  customCurrencyExchangeRate: 7
}

// 系统名/Logo 默认值（M0 阶段品牌定值，M1 接入后端覆盖）
export const DEFAULT_SYSTEM_NAME = 'Yaoshu Token'
// 默认 Logo 用 Vite import 走构建期打包，避免 /logo.png 这类运行时静态资源未部署导致 404
export const DEFAULT_LOGO: string = defaultLogoUrl

export interface SystemConfig {
  systemName: string
  logo: string
  footerHtml?: string
  demoSiteEnabled?: boolean
  displayTokenStatEnabled?: boolean
  currency: CurrencyConfig
  // 完整 status 原始数据，供下游派生字段（passkey_login/announcements/header_nav_modules 等）
  rawStatus: SystemStatus | null
}

function toNumber(value: unknown, fallback: number): number {
  if (typeof value === 'number' && !Number.isNaN(value)) return value
  if (typeof value === 'string') {
    const parsed = Number(value)
    if (!Number.isNaN(parsed)) return parsed
  }
  return fallback
}
function mapStatusDataToConfig(
  data: SystemStatusData | undefined
): Partial<SystemConfig> {
  if (!data) return {}

  const quotaDisplayType =
    (data.quotaDisplayType as CurrencyDisplayType | undefined) ??
    DEFAULT_CURRENCY_CONFIG.quotaDisplayType

  const currency: CurrencyConfig = {
    displayInCurrency:
      data.displayInCurrency ?? DEFAULT_CURRENCY_CONFIG.displayInCurrency,
    quotaDisplayType,
    quotaPerUnit: toNumber(
      data.quotaPerUnit,
      DEFAULT_CURRENCY_CONFIG.quotaPerUnit
    ),
    usdExchangeRate: toNumber(
      data.usdExchangeRate,
      DEFAULT_CURRENCY_CONFIG.usdExchangeRate
    ),
    customCurrencySymbol:
      (typeof data.customCurrencySymbol === 'string' &&
        data.customCurrencySymbol.trim()) ||
      DEFAULT_CURRENCY_CONFIG.customCurrencySymbol,
    customCurrencyExchangeRate: toNumber(
      data.customCurrencyExchangeRate,
      DEFAULT_CURRENCY_CONFIG.customCurrencyExchangeRate
    )
  }

  return {
    systemName: data.systemName || DEFAULT_SYSTEM_NAME,
    logo: data.logo || DEFAULT_LOGO,
    footerHtml: data.footerHtml,
    demoSiteEnabled: data.demoSiteEnabled,
    displayTokenStatEnabled: data.displayTokenStatEnabled,
    currency
  }
}

const STORAGE_KEY = 'yaoshu_system_status'
function readPersistedStatus(): SystemStatus | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    return JSON.parse(raw) as SystemStatus
  } catch {
    return null
  }
}

function writePersistedStatus(status: SystemStatus | null): void {
  try {
    if (status === null) {
      localStorage.removeItem(STORAGE_KEY)
    } else {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(status))
    }
  } catch {
    /* 配额满/隐私模式：忽略持久化失败，下次拉取时重建 */
  }
}

export const useSystemConfigStore = defineStore('system-config', () => {
  // 同步初始化：从 localStorage 读取 → 避免 SystemBrand/Footer 等组件首屏空白
  const initial = readPersistedStatus()

  // initial 经拦截器解包后为扁平 SystemStatusData，直接读取字段（无 data 嵌套层）
  const systemName = ref<string>(
    initial?.systemName || DEFAULT_SYSTEM_NAME
  )
  const logo = ref<string>(initial?.logo || DEFAULT_LOGO)
  const footerHtml = ref<string | undefined>(initial?.footerHtml)
  const demoSiteEnabled = ref<boolean | undefined>(
    initial?.demoSiteEnabled
  )
  const displayTokenStatEnabled = ref<boolean | undefined>(
    initial?.displayTokenStatEnabled
  )
  const currency = ref<CurrencyConfig>({ ...DEFAULT_CURRENCY_CONFIG })
  const rawStatus = ref<SystemStatus | null>(initial)
  const loading = ref<boolean>(false)
  const lastError = ref<Error | null>(null)

  // 用初始持久化值同步派生 currency
  if (initial) {
    const mapped = mapStatusDataToConfig(initial)
    if (mapped.currency) currency.value = mapped.currency
  }
  // 品牌字段（systemName/logo/footerHtml）经 SPI simplifyConfig 处理：
  // 商业版可注册固定品牌信息覆盖后端测试数据，开源版无注册则透传原值
  function setConfig(partial: Partial<SystemConfig>): void {
    const brandOverride = simplifyConfig('brand-info', {
      systemName: partial.systemName,
      logo: partial.logo,
      footerHtml: partial.footerHtml,
    })
    if (brandOverride.systemName !== undefined) systemName.value = brandOverride.systemName
    if (brandOverride.logo !== undefined) logo.value = brandOverride.logo
    if (brandOverride.footerHtml !== undefined) footerHtml.value = brandOverride.footerHtml
    if (partial.demoSiteEnabled !== undefined)
      demoSiteEnabled.value = partial.demoSiteEnabled
    if (partial.displayTokenStatEnabled !== undefined)
      displayTokenStatEnabled.value = partial.displayTokenStatEnabled
    if (partial.currency) currency.value = { ...partial.currency }
    if (partial.rawStatus !== undefined) rawStatus.value = partial.rawStatus
  }

  // 从后端拉取 /api/status 并落库 + 持久化（被 useStatus composable 调度）
  async function fetch(): Promise<SystemStatus | null> {
    loading.value = true
    lastError.value = null
    try {
      const status = await getStatus()
      setConfig({
        ...mapStatusDataToConfig(status ?? undefined),
        rawStatus: status
      })
      writePersistedStatus(status)
      return status
    } catch (err) {
      lastError.value = err instanceof Error ? err : new Error(String(err))
      // 不静默吞错：拦截器已弹 ElMessage，此处仅记录最后一次错误供调用方查询
      throw lastError.value
    } finally {
      loading.value = false
    }
  }

  // 主题/币种相关派生 getter
  const isCurrencyDisplay = computed(() => currency.value.displayInCurrency)

  return {
    // state
    systemName,
    logo,
    footerHtml,
    demoSiteEnabled,
    displayTokenStatEnabled,
    currency,
    rawStatus,
    loading,
    lastError,
    // getter
    isCurrencyDisplay,
    // action
    setConfig,
    fetch
  }
})
