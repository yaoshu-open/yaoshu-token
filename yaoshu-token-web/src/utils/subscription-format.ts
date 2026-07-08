import dayjs from 'dayjs'
import type { SubscriptionPlan } from '@/api/subscription/types'

/** 格式化套餐有效期 */
export function formatDuration(plan: Partial<SubscriptionPlan>): string {
  const unit = plan.durationUnit || 'month'
  const value = plan.durationValue || 1
  const unitLabels: Record<string, string> = {
    year: '年',
    month: '月',
    day: '天',
    hour: '小时',
    custom: '自定义'
  }
  if (unit === 'custom') {
    const seconds = plan.customSeconds || 0
    if (seconds >= 86400) return `${Math.floor(seconds / 86400)} 天`
    if (seconds >= 3600) return `${Math.floor(seconds / 3600)} 小时`
    return `${seconds} 秒`
  }
  return `${value} ${unitLabels[unit] || unit}`
}

/** 格式化配额重置周期 */
export function formatResetPeriod(plan: Partial<SubscriptionPlan>): string {
  const period = plan.quotaResetPeriod || 'never'
  if (period === 'daily') return '每天'
  if (period === 'weekly') return '每周'
  if (period === 'monthly') return '每月'
  if (period === 'custom') {
    const seconds = Number(plan.quotaResetCustomSeconds || 0)
    if (seconds >= 86400) return `${Math.floor(seconds / 86400)} 天`
    if (seconds >= 3600) return `${Math.floor(seconds / 3600)} 小时`
    if (seconds >= 60) return `${Math.floor(seconds / 60)} 分钟`
    return `${seconds} 秒`
  }
  return '不重置'
}

/** 格式化 Unix 时间戳 */
export function formatTimestamp(ts: number): string {
  if (!ts) return '-'
  return dayjs(ts * 1000).format('YYYY-MM-DD HH:mm:ss')
}

/** 计算订阅剩余天数 */
export function getRemainingDays(endTime: number): number {
  if (!endTime) return 0
  const now = Math.floor(Date.now() / 1000)
  return Math.max(0, Math.ceil((endTime - now) / 86400))
}

/** 格式化配额用量 */
export function formatQuota(amount: number): string {
  if (amount >= 100000000) return `${(amount / 100000000).toFixed(2)} 亿`
  if (amount >= 10000) return `${(amount / 10000).toFixed(2)} 万`
  return amount.toLocaleString()
}
