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

export function loadConfig(): Partial<PlaygroundConfig> {
  return safeParse<Partial<PlaygroundConfig>>(
    localStorage.getItem(STORAGE_KEYS.CONFIG),
    {}
  )
}

export function saveConfig(config: Partial<PlaygroundConfig>): void {
  try {
    localStorage.setItem(STORAGE_KEYS.CONFIG, JSON.stringify(config))
  } catch (err) {
    console.error('[playground] saveConfig failed:', err)
  }
}

export function loadParameterEnabled(): Partial<ParameterEnabled> {
  return safeParse<Partial<ParameterEnabled>>(
    localStorage.getItem(STORAGE_KEYS.PARAMETER_ENABLED),
    {}
  )
}

export function saveParameterEnabled(
  parameterEnabled: Partial<ParameterEnabled>
): void {
  try {
    localStorage.setItem(
      STORAGE_KEYS.PARAMETER_ENABLED,
      JSON.stringify(parameterEnabled)
    )
  } catch (err) {
    console.error('[playground] saveParameterEnabled failed:', err)
  }
}

export function loadMessages(): Message[] | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEYS.MESSAGES)
    if (!raw) return null
    const parsed = safeParse<unknown>(raw, null)
    if (!Array.isArray(parsed)) return null
    const sanitized = sanitizeMessagesOnLoad(parsed as Message[])
    if (sanitized !== parsed) saveMessages(sanitized)
    return sanitized
  } catch (err) {
    console.error('[playground] loadMessages failed:', err)
    return null
  }
}

export function saveMessages(messages: Message[]): void {
  try {
    localStorage.setItem(STORAGE_KEYS.MESSAGES, JSON.stringify(messages))
  } catch (err) {
    console.error('[playground] saveMessages failed:', err)
  }
}

export function clearPlaygroundData(): void {
  try {
    localStorage.removeItem(STORAGE_KEYS.CONFIG)
    localStorage.removeItem(STORAGE_KEYS.PARAMETER_ENABLED)
    localStorage.removeItem(STORAGE_KEYS.MESSAGES)
  } catch (err) {
    console.error('[playground] clearPlaygroundData failed:', err)
  }
}
