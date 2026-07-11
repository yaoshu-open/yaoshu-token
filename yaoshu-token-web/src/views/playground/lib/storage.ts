/**
 * Playground 持久化封装（localStorage）。
 */
import { STORAGE_KEYS } from '../constants'
import type {
  PlaygroundConfig,
  ParameterEnabled,
  Message
} from '@/api/playground/types'
import { sanitizeMessagesOnLoad } from './message-utils'

function safeParse<T>(raw: string | null, fallback: T): T {
  if (!raw) return fallback
  try {
    return JSON.parse(raw) as T
  } catch {
    return fallback
  }
}

// localStorage key 按用户隔离：`{baseKey}:{userId}`，防多账号切换串号
function userKey(base: string, userId: string): string {
  return `${base}:${userId}`
}

export function loadConfig(userId: string): Partial<PlaygroundConfig> {
  return safeParse<Partial<PlaygroundConfig>>(
    localStorage.getItem(userKey(STORAGE_KEYS.CONFIG, userId)),
    {}
  )
}

export function saveConfig(userId: string, config: Partial<PlaygroundConfig>): void {
  try {
    localStorage.setItem(userKey(STORAGE_KEYS.CONFIG, userId), JSON.stringify(config))
  } catch (err) {
    console.error('[playground] saveConfig failed:', err)
  }
}

export function loadParameterEnabled(userId: string): Partial<ParameterEnabled> {
  return safeParse<Partial<ParameterEnabled>>(
    localStorage.getItem(userKey(STORAGE_KEYS.PARAMETER_ENABLED, userId)),
    {}
  )
}

export function saveParameterEnabled(
  userId: string,
  parameterEnabled: Partial<ParameterEnabled>
): void {
  try {
    localStorage.setItem(
      userKey(STORAGE_KEYS.PARAMETER_ENABLED, userId),
      JSON.stringify(parameterEnabled)
    )
  } catch (err) {
    console.error('[playground] saveParameterEnabled failed:', err)
  }
}

export function loadMessages(userId: string): Message[] | null {
  try {
    const raw = localStorage.getItem(userKey(STORAGE_KEYS.MESSAGES, userId))
    if (!raw) return null
    const parsed = safeParse<unknown>(raw, null)
    if (!Array.isArray(parsed)) return null
    const sanitized = sanitizeMessagesOnLoad(parsed as Message[])
    if (sanitized !== parsed) saveMessages(userId, sanitized)
    return sanitized
  } catch (err) {
    console.error('[playground] loadMessages failed:', err)
    return null
  }
}

export function saveMessages(userId: string, messages: Message[]): void {
  try {
    localStorage.setItem(userKey(STORAGE_KEYS.MESSAGES, userId), JSON.stringify(messages))
  } catch (err) {
    console.error('[playground] saveMessages failed:', err)
  }
}

/** 清理指定用户的 Playground 持久化数据（登出时调用） */
export function clearPlaygroundData(userId: string): void {
  try {
    localStorage.removeItem(userKey(STORAGE_KEYS.CONFIG, userId))
    localStorage.removeItem(userKey(STORAGE_KEYS.PARAMETER_ENABLED, userId))
    localStorage.removeItem(userKey(STORAGE_KEYS.MESSAGES, userId))
  } catch (err) {
    console.error('[playground] clearPlaygroundData failed:', err)
  }
}
