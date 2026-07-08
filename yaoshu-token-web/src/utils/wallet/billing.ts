import dayjs from 'dayjs'
import type { TopupStatus } from '@/api/wallet/types'

/** 账单状态徽标配置 */
export interface BillingStatusConfig {
  variant: 'success' | 'warning' | 'danger' | 'info'
  label: string
  type: 'success' | 'warning' | 'danger' | 'info'
}

/** 状态徽标映射 */
export const BILLING_STATUS_CONFIG: Record<TopupStatus, BillingStatusConfig> = {
  success: { variant: 'success', label: 'Success', type: 'success' },
  pending: { variant: 'warning', label: 'Pending', type: 'warning' },
  expired: { variant: 'danger', label: 'Expired', type: 'danger' },
}

/** 获取状态徽标配置 */
export function getBillingStatusConfig(status: TopupStatus): BillingStatusConfig {
  return BILLING_STATUS_CONFIG[status] || BILLING_STATUS_CONFIG.pending
}

/** 支付方式显示名映射 */
export const PAYMENT_METHOD_NAMES: Record<string, string> = {
  stripe: 'Stripe',
  alipay: 'Alipay',
  wxpay: 'WeChat Pay',
  waffo: 'Waffo',
  waffo_pancake: 'Waffo Pancake',
  creem: 'Creem',
}

/** 获取支付方式显示名 */
export function getPaymentMethodName(
  method: string,
  t?: (key: string) => string
): string {
  const name = PAYMENT_METHOD_NAMES[method] || method
  return t ? t(name) : name
}

/** 时间戳格式化（秒级 → 本地日期字符串） */
export function formatBillingTimestamp(timestamp: number): string {
  if (!timestamp) return '-'
  // 后端返回秒级时间戳，dayjs 需要毫秒
  return dayjs(timestamp * 1000).format('YYYY-MM-DD HH:mm:ss')
}
