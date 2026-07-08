// 纯函数，无状态，供组件直接调用

import type { UserProfile, UserSettings } from '@/api/profile/types'

/** 解析用户设置 JSON string 为 UserSettings 对象 */
export function parseUserSettings(settingsJson?: string): UserSettings {
  if (!settingsJson) return {}
  try {
    return JSON.parse(settingsJson) as UserSettings
  } catch {
    return {}
  }
}

/** 获取显示名称（displayName 优先，回退 username） */
export function getDisplayName(user?: UserProfile | null): string {
  if (!user) return ''
  return user.displayName || user.username
}

/** 获取用户头像 initials（取 displayName 前 2 字符大写） */
export function getUserInitials(user?: UserProfile | null): string {
  if (!user) return '?'
  const name = getDisplayName(user)
  if (!name) return '?'
  const parts = name.trim().split(/\s+/)
  if (parts.length >= 2) {
    return `${parts[0][0]}${parts[1][0]}`.toUpperCase()
  }
  return name.slice(0, 2).toUpperCase()
}
