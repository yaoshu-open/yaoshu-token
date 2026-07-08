/**
 * 渠道工具函数（纯函数，无响应式）。
 */

import {
  BALANCE_THRESHOLDS,
  CHANNEL_STATUS,
  CHANNEL_STATUS_CONFIG,
  CHANNEL_TYPES,
  MODEL_FETCHABLE_TYPES,
  RESPONSE_TIME_CONFIG,
  RESPONSE_TIME_THRESHOLDS
} from '@/api/channel/constants'
import type { Channel } from '@/api/channel/types'

/** 获取渠道状态配置 */
export function getStatusConfig(status: number) {
  return (
    CHANNEL_STATUS_CONFIG[status as keyof typeof CHANNEL_STATUS_CONFIG] ??
    CHANNEL_STATUS_CONFIG[CHANNEL_STATUS.UNKNOWN]
  )
}

/** 获取渠道类型名称 */
export function getChannelTypeName(type: number): string {
  return CHANNEL_TYPES[type as keyof typeof CHANNEL_TYPES] ?? 'Unknown'
}

/** 格式化余额显示（防御后端返回 string/null，统一转 Number） */
export function formatBalance(balance: number): string {
  const n = Number(balance)
  if (!n) return '$0.00'
  if (n < BALANCE_THRESHOLDS.LOW) return `$${n.toFixed(4)}`
  return `$${n.toFixed(2)}`
}

/** 获取余额颜色变体（防御后端返回 string/null，统一转 Number） */
export function getBalanceVariant(balance: number): string {
  const n = Number(balance)
  if (!n) return 'neutral'
  if (n < BALANCE_THRESHOLDS.LOW) return 'danger'
  if (n < BALANCE_THRESHOLDS.MEDIUM) return 'warning'
  return 'success'
}

/** 格式化响应时间显示（防御后端返回 string/null，统一转 Number） */
export function formatResponseTime(ms: number): string {
  const n = Number(ms)
  if (!n) return '-'
  if (n < 1000) return `${n}ms`
  return `${(n / 1000).toFixed(2)}s`
}

/** 获取响应时间配置 */
export function getResponseTimeConfig(ms: number) {
  if (ms === 0) return RESPONSE_TIME_CONFIG.UNKNOWN
  if (ms <= RESPONSE_TIME_THRESHOLDS.EXCELLENT) return RESPONSE_TIME_CONFIG.EXCELLENT
  if (ms <= RESPONSE_TIME_THRESHOLDS.GOOD) return RESPONSE_TIME_CONFIG.GOOD
  if (ms <= RESPONSE_TIME_THRESHOLDS.FAIR) return RESPONSE_TIME_CONFIG.FAIR
  return RESPONSE_TIME_CONFIG.POOR
}

/** 格式化时间戳为可读字符串 */
export function formatTimestamp(ts: number): string {
  if (!ts) return '-'
  return new Date(ts * 1000).toLocaleString()
}

/** 截断模型列表显示（紧凑模式用） */
export function truncateModels(models: string, maxCount = 3): string {
  if (!models) return '-'
  const list = models.split(',').filter(Boolean)
  if (list.length <= maxCount) return list.join(', ')
  return `${list.slice(0, maxCount).join(', ')} +${list.length - maxCount}`
}

/** 是否为多密钥渠道 */
export function isMultiKeyChannel(channel: Channel): boolean {
  return channel.channelInfo?.isMultiKey ?? false
}

/** 获取渠道密钥预览（脱敏） */
export function getKeyPreview(key: string): string {
  if (!key) return '-'
  if (key.length <= 12) return '****'
  return `${key.slice(0, 6)}****${key.slice(-4)}`
}

/** 获取渠道分组列表 */
export function getGroupList(channel: Channel): string[] {
  if (!channel.group) return []
  return channel.group.split(',').filter(Boolean)
}

/** 判断渠道是否启用 */
export function isChannelEnabled(channel: Channel): boolean {
  return channel.status === CHANNEL_STATUS.ENABLED
}

/** 判断渠道是否可获取上游模型 */
export function isModelFetchable(type: number): boolean {
  return MODEL_FETCHABLE_TYPES.has(type)
}

/**
 * 将业务 variant 映射到 Element Plus el-tag 的 type。
 * 'neutral' -> 'info'（el-tag 无 neutral type）
 */
export function variantToTagType(variant: string): 'success' | 'warning' | 'danger' | 'info' {
  switch (variant) {
    case 'success':
      return 'success'
    case 'warning':
      return 'warning'
    case 'danger':
      return 'danger'
    default:
      return 'info'
  }
}
