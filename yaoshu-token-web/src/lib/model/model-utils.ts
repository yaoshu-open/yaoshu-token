/**
 * 模型工具函数（纯函数，无响应式）。
 */

import { NAME_RULE_CONFIG, QUOTA_TYPE_CONFIG } from '@/api/model/constants'
import type { Model, NameRule } from '@/api/model/types'

/** 格式化时间戳为标准日期字符串 */
export function formatTimestamp(timestamp: number): string {
  if (!timestamp || timestamp === 0) return '-'
  const date = new Date(timestamp * 1000)
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const h = String(date.getHours()).padStart(2, '0')
  const min = String(date.getMinutes()).padStart(2, '0')
  const s = String(date.getSeconds()).padStart(2, '0')
  return `${y}-${m}-${d} ${h}:${min}:${s}`
}

/** 格式化相对时间 */
export function formatRelativeTime(timestamp: number): string {
  if (!timestamp || timestamp === 0) return 'Never'
  const now = Date.now()
  const time = timestamp * 1000
  const diff = now - time
  const seconds = Math.floor(diff / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)
  if (days > 0) return `${days} day${days > 1 ? 's' : ''} ago`
  if (hours > 0) return `${hours} hour${hours > 1 ? 's' : ''} ago`
  if (minutes > 0) return `${minutes} minute${minutes > 1 ? 's' : ''} ago`
  return `${seconds} second${seconds !== 1 ? 's' : ''} ago`
}

/** 解析标签字符串为数组 */
export function parseModelTags(tags: string | undefined): string[] {
  if (!tags) return []
  return tags.split(',').map((tag) => tag.trim()).filter(Boolean)
}

/** 格式化标签数组为字符串 */
export function formatTagsString(tags: string[]): string {
  return tags.join(',')
}

/** 解析端点 JSON 字符串 */
export function parseEndpoints(endpoints: string | undefined): Record<string, unknown> | unknown[] | null {
  if (!endpoints || endpoints.trim() === '') return null
  try {
    return JSON.parse(endpoints)
  } catch {
    return null
  }
}

/** 格式化端点为显示数组 */
export function formatEndpointsDisplay(endpoints: string | undefined): string[] {
  const parsed = parseEndpoints(endpoints)
  if (!parsed) return []
  if (typeof parsed === 'object' && !Array.isArray(parsed)) {
    return Object.keys(parsed)
  }
  if (Array.isArray(parsed)) {
    return parsed.map(String)
  }
  return []
}

/** 获取名称规则配置 */
export function getNameRuleConfigByRule(rule: NameRule) {
  return NAME_RULE_CONFIG[rule] || NAME_RULE_CONFIG[0]
}

/** 格式化配额类型数组 */
export function formatQuotaTypes(quotaTypes: number[] | undefined): string {
  if (!quotaTypes || quotaTypes.length === 0) return '-'
  return quotaTypes.map((qt) => QUOTA_TYPE_CONFIG[qt]?.label || String(qt)).join(', ')
}

/** 校验模型名称 */
export function validateModelName(name: string): boolean {
  return name.trim().length > 0
}

/** 校验端点 JSON */
export function validateEndpointsJSON(endpoints: string): boolean {
  if (!endpoints || endpoints.trim() === '') return true
  try {
    JSON.parse(endpoints)
    return true
  } catch {
    return false
  }
}

/** 模型是否启用 */
export function isModelEnabled(model: Model): boolean {
  return model.status === 1
}

/** 模型是否同步官方 */
export function isModelSyncOfficial(model: Model): boolean {
  return model.syncOfficial === 1
}

/** 将表单数据转为 API 提交格式 */
export function modelFormToApi(formData: {
  id?: number
  modelName: string
  description: string
  icon: string
  tags: string[]
  vendorId?: number
  endpoints: string
  nameRule: number
  status: boolean
  syncOfficial: boolean
}): Record<string, unknown> {
  return {
    ...(formData.id != null ? { id: formData.id } : {}),
    modelName: formData.modelName,
    description: formData.description,
    icon: formData.icon,
    tags: formatTagsString(formData.tags),
    vendorId: formData.vendorId ?? 0,
    endpoints: formData.endpoints,
    nameRule: formData.nameRule,
    status: formData.status ? 1 : 0,
    syncOfficial: formData.syncOfficial ? 1 : 0,
  }
}

/** 将 API 模型实体转为表单数据 */
export function modelToForm(model: Model): {
  id: number
  modelName: string
  description: string
  icon: string
  tags: string[]
  vendorId?: number
  endpoints: string
  nameRule: number
  status: boolean
  syncOfficial: boolean
} {
  return {
    id: model.id,
    modelName: model.modelName,
    description: model.description || '',
    icon: model.icon || '',
    tags: parseModelTags(model.tags),
    vendorId: model.vendorId,
    endpoints: model.endpoints || '',
    nameRule: model.nameRule,
    status: model.status === 1,
    syncOfficial: model.syncOfficial === 1,
  }
}
