// HeaderNavModules 解析器：/api/status 返回的 header_nav_modules 字段
// 后端契约：字符串化 JSON，键为 home/console/pricing/rankings/docs/about，值为 boolean 或 { enabled, requireAuth }

export interface NavModuleObjectConfig {
  enabled: boolean
  requireAuth?: boolean
}

export type NavModuleConfig = boolean | NavModuleObjectConfig

export interface HeaderNavModules {
  home?: NavModuleConfig
  console?: NavModuleConfig
  pricing?: NavModuleConfig
  rankings?: NavModuleConfig
  docs?: NavModuleConfig
  about?: NavModuleConfig
  [key: string]: NavModuleConfig | undefined
}

// 安全解析后端 status，未配置或解析失败时返回 null（消费方按默认显示规则处理）
export function parseHeaderNavModulesFromStatus(
  status: Record<string, unknown> | null | undefined
): HeaderNavModules | null {
  if (!status) return null

  const raw =
    (status.headerNavModules as unknown) ??
    (status.data as Record<string, unknown> | undefined)?.headerNavModules

  if (!raw) return null

  // 已是对象：直接返回
  if (typeof raw === 'object' && raw !== null) {
    return raw as HeaderNavModules
  }

  // 字符串化 JSON：解析后返回，解析失败时返回 null（不静默吞错）
  if (typeof raw === 'string') {
    try {
      const parsed = JSON.parse(raw) as HeaderNavModules
      if (parsed && typeof parsed === 'object') return parsed
      return null
    } catch {
      return null
    }
  }

  return null
}
