/**
 * Codex 用量解析纯工具函数 + 类型。
 *
 * 后端 /codex/usage 返回 data: Record<string, unknown>，结构随上游可能变化，
 * 全部用 unknown + 类型守卫防御性解析，缺字段返回 '-'。
 */
import dayjs from 'dayjs'
import type { CodexUsageResponse } from '@/api/channel'

export type CodexRateLimitWindow = {
  used_percent?: number
  reset_at?: number
  reset_after_seconds?: number
  limit_window_seconds?: number
}

export type CodexRateLimit = {
  plan_type?: string
  allowed?: boolean
  limit_reached?: boolean
  primary_window?: CodexRateLimitWindow
  secondary_window?: CodexRateLimitWindow
}

export type CodexAdditionalRateLimit = {
  limit_name?: string
  metered_feature?: string
  rate_limit?: CodexRateLimit
  primary_window?: CodexRateLimitWindow
  secondary_window?: CodexRateLimitWindow
  plan_type?: string
}

export type CodexUsagePayload = {
  plan_type?: string
  user_id?: string
  email?: string
  account_id?: string
  rate_limit?: CodexRateLimit
  additional_rate_limits?: CodexAdditionalRateLimit[]
}

type StatusVariant = 'success' | 'neutral' | 'danger' | 'warning' | 'info' | 'primary'

/** 数值钳制到 0-100 */
export function clampPercent(value: unknown): number {
  const v = Number(value)
  return Number.isFinite(v) ? Math.max(0, Math.min(100, v)) : 0
}

/** Unix 秒 → 格式化字符串 */
export function formatUnixSeconds(unixSeconds: unknown): string {
  const v = Number(unixSeconds)
  if (!Number.isFinite(v) || v <= 0) return '-'
  try {
    return dayjs(v * 1000).format('YYYY-MM-DD HH:mm:ss')
  } catch {
    return String(unixSeconds)
  }
}

/** 秒数 → 时长格式化（xh xm / xm xs / xs） */
export function formatDurationSeconds(seconds: unknown): string {
  const s = Number(seconds)
  if (!Number.isFinite(s) || s <= 0) return '-'

  const total = Math.floor(s)
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const secs = total % 60

  if (hours > 0) return `${hours}h ${minutes}m`
  if (minutes > 0) return `${minutes}m ${secs}s`
  return `${secs}s`
}

function normalizePlanType(value: unknown): string {
  if (value == null) return ''
  return String(value).trim().toLowerCase()
}

/** 按 limit_window_seconds 分类窗口（≥86400s=weekly, <86400s=fiveHour） */
function classifyWindowByDuration(
  windowData?: CodexRateLimitWindow | null
): 'weekly' | 'fiveHour' | null {
  const seconds = Number(windowData?.limit_window_seconds)
  if (!Number.isFinite(seconds) || seconds <= 0) return null
  return seconds >= 24 * 60 * 60 ? 'weekly' : 'fiveHour'
}

type RateLimitSource = {
  plan_type?: string
  rate_limit?: CodexRateLimit
}

/** 从 rate_limit 中解析 5 小时窗口和周窗口 */
export function resolveRateLimitWindows(data: RateLimitSource | null): {
  fiveHourWindow: CodexRateLimitWindow | null
  weeklyWindow: CodexRateLimitWindow | null
} {
  const rateLimit = data?.rate_limit ?? {}
  const primary = rateLimit?.primary_window ?? null
  const secondary = rateLimit?.secondary_window ?? null
  const windows = [primary, secondary].filter(Boolean) as CodexRateLimitWindow[]
  const planType = normalizePlanType(data?.plan_type ?? rateLimit?.plan_type)

  let fiveHourWindow: CodexRateLimitWindow | null = null
  let weeklyWindow: CodexRateLimitWindow | null = null

  for (const w of windows) {
    const bucket = classifyWindowByDuration(w)
    if (bucket === 'fiveHour' && !fiveHourWindow) {
      fiveHourWindow = w
      continue
    }
    if (bucket === 'weekly' && !weeklyWindow) {
      weeklyWindow = w
    }
  }

  // free 计划只有周窗口
  if (planType === 'free') {
    if (!weeklyWindow) weeklyWindow = primary ?? secondary ?? null
    return { fiveHourWindow: null, weeklyWindow }
  }

  // 无法分类时回退到 primary/secondary 顺序
  if (!fiveHourWindow && !weeklyWindow) {
    return { fiveHourWindow: primary, weeklyWindow: secondary }
  }

  if (!fiveHourWindow) {
    fiveHourWindow = windows.find((w) => w !== weeklyWindow) ?? null
  }
  if (!weeklyWindow) {
    weeklyWindow = windows.find((w) => w !== fiveHourWindow) ?? null
  }

  return { fiveHourWindow, weeklyWindow }
}

const PLAN_TYPE_BADGE: Record<string, { label: string; variant: StatusVariant }> = {
  enterprise: { label: 'Enterprise', variant: 'success' },
  team: { label: 'Team', variant: 'info' },
  pro: { label: 'Pro', variant: 'primary' },
  plus: { label: 'Plus', variant: 'primary' },
  free: { label: 'Free', variant: 'warning' }
}

/** 套餐类型 → 徽章配置 */
export function getAccountTypeBadge(value: unknown): {
  label: string
  variant: StatusVariant
} {
  const normalized = normalizePlanType(value)
  return (
    PLAN_TYPE_BADGE[normalized] ?? {
      label: String(value || '') || 'Unknown',
      variant: 'neutral' as const
    }
  )
}

/** 用量百分比 → 徽章配置 */
export function getWindowBadge(windowData?: CodexRateLimitWindow | null): {
  percent: number
  variant: StatusVariant
} {
  const percent = clampPercent(windowData?.used_percent)
  const variant: StatusVariant =
    percent >= 95 ? 'danger' : percent >= 80 ? 'warning' : 'info'
  return { percent, variant }
}

/** 从 CodexUsageResponse 解析 payload */
export function parseCodexUsagePayload(
  response: CodexUsageResponse | null
): CodexUsagePayload | null {
  const raw = response?.data
  if (!raw || typeof raw !== 'object') return null
  return raw as CodexUsagePayload
}

/** 判断速率限制状态 */
export function getRateLimitStatus(rateLimit?: CodexRateLimit | null): {
  label: string
  variant: StatusVariant
} {
  if (!rateLimit || Object.keys(rateLimit).length === 0) {
    return { label: 'common.pending', variant: 'neutral' }
  }
  if (rateLimit.allowed && !rateLimit.limit_reached) {
    return { label: 'channel.codex.available', variant: 'success' }
  }
  return { label: 'channel.codex.limited', variant: 'danger' }
}
