/**
 * Ollama 模型管理纯工具函数 + 类型。
 */
import type { Channel } from '@/api/channel/types'

export type PullProgress = {
  status?: string
  completed?: number
  total?: number
  [k: string]: unknown
}

export type OllamaModel = {
  id: string
  owned_by?: string
  size?: number
  digest?: string
  modified_at?: string
  details?: unknown
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function getString(value: unknown): string | undefined {
  return typeof value === 'string' ? value : undefined
}

function getNumber(value: unknown): number | undefined {
  return typeof value === 'number' ? value : undefined
}

function parseMaybeJSON(value: unknown): unknown {
  if (!value) return null
  if (typeof value === 'object') return value
  if (typeof value === 'string') {
    try {
      return JSON.parse(value)
    } catch {
      return null
    }
  }
  return null
}

/** 从 channel.base_url / other_info 解析 Ollama API 地址 */
export function resolveOllamaBaseUrl(channel: Channel | null): string {
  if (!channel) return ''

  const direct =
    typeof channel.baseUrl === 'string' ? channel.baseUrl.trim() : ''
  if (direct) return direct
  const alt = getString(
    (channel as unknown as Record<string, unknown>).ollama_base_url
  )?.trim()
  if (alt) return alt

  const parsed = parseMaybeJSON(channel.otherInfo)
  if (isRecord(parsed)) {
    const baseUrl = getString(parsed.base_url)?.trim()
    if (baseUrl) return baseUrl
    const publicUrl = getString(parsed.public_url)?.trim()
    if (publicUrl) return publicUrl
    const apiUrl = getString(parsed.api_url)?.trim()
    if (apiUrl) return apiUrl
  }

  return ''
}

/** 归一化后端返回的模型列表（兼容字符串/对象多种格式） */
export function normalizeOllamaModels(items: unknown): OllamaModel[] {
  if (!Array.isArray(items)) return []

  return items
    .map((item): OllamaModel | null => {
      if (!item) return null

      if (typeof item === 'string') {
        return { id: item, owned_by: 'ollama' }
      }

      if (isRecord(item)) {
        const candidateId =
          getString(item.id) ||
          getString(item.ID) ||
          getString(item.name) ||
          getString(item.model) ||
          getString(item.Model)
        if (!candidateId) return null

        const metadata = item.metadata ?? item.Metadata
        const normalized: OllamaModel = {
          id: candidateId,
          owned_by:
            getString(item.owned_by) || getString(item.ownedBy) || 'ollama'
        }

        const itemSize = getNumber(item.size)
        if (typeof itemSize === 'number' && !normalized.size) {
          normalized.size = itemSize
        }
        if (isRecord(metadata)) {
          const metaSize = getNumber(metadata.size)
          if (typeof metaSize === 'number' && !normalized.size) {
            normalized.size = metaSize
          }
          const metaDigest = getString(metadata.digest)
          if (!normalized.digest && metaDigest) {
            normalized.digest = metaDigest
          }
          const metaModifiedAt = getString(metadata.modified_at)
          if (!normalized.modified_at && metaModifiedAt) {
            normalized.modified_at = metaModifiedAt
          }
          if (metadata.details && !normalized.details) {
            normalized.details = metadata.details
          }
        }
        return normalized
      }

      return null
    })
    .filter((m): m is OllamaModel => m !== null)
}

/** 字节数格式化（B/KB/MB/GB） */
export function formatBytes(bytes?: number): string {
  if (typeof bytes !== 'number' || Number.isNaN(bytes)) return '-'
  if (bytes < 1024) return `${bytes} B`
  const kb = bytes / 1024
  if (kb < 1024) return `${kb.toFixed(1)} KB`
  const mb = kb / 1024
  if (mb < 1024) return `${mb.toFixed(1)} MB`
  const gb = mb / 1024
  return `${gb.toFixed(2)} GB`
}
