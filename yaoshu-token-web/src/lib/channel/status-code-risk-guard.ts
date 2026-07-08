/**
 * 状态码映射风险守卫。
 *
 * 检测 status_code_mapping 中的高危重定向（如把不可重定向的 504/524 映射到其他码），
 * 在表单提交时作为风险确认守卫使用。
 */

/** 不可重定向的源状态码（网关超时类，重定向会掩盖真实故障） */
const NON_REDIRECTABLE_STATUS_CODES = new Set([504, 524])

function parseStatusCodeKey(rawKey: string): number | null {
  const normalized = rawKey.trim()
  if (!/^[1-5]\d{2}$/.test(normalized)) return null
  return Number.parseInt(normalized, 10)
}

function parseStatusCodeMappingTarget(rawValue: unknown): number | null {
  if (typeof rawValue === 'number' && Number.isInteger(rawValue)) {
    return rawValue >= 100 && rawValue <= 599 ? rawValue : null
  }
  if (typeof rawValue === 'string') {
    const normalized = rawValue.trim()
    if (!/^[1-5]\d{2}$/.test(normalized)) return null
    const code = Number.parseInt(normalized, 10)
    return code >= 100 && code <= 599 ? code : null
  }
  return null
}

/** 收集 status_code_mapping 中的格式无效项（键或值不是合法状态码） */
export function collectInvalidStatusCodeEntries(
  statusCodeMappingStr: string
): string[] {
  if (!statusCodeMappingStr?.trim()) return []

  let parsed: Record<string, unknown>
  try {
    parsed = JSON.parse(statusCodeMappingStr)
  } catch {
    return []
  }

  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return []

  const invalid: string[] = []
  for (const [rawKey, rawValue] of Object.entries(parsed)) {
    const fromCode = parseStatusCodeKey(rawKey)
    const toCode = parseStatusCodeMappingTarget(rawValue)
    if (fromCode === null || toCode === null) {
      invalid.push(`${rawKey} → ${rawValue}`)
    }
  }
  return invalid
}

/** 收集 status_code_mapping 中的高危重定向项（不可重定向码被映射到其他码） */
export function collectDisallowedStatusCodeRedirects(
  statusCodeMappingStr: string
): string[] {
  if (!statusCodeMappingStr?.trim()) return []

  let parsed: Record<string, unknown>
  try {
    parsed = JSON.parse(statusCodeMappingStr)
  } catch {
    return []
  }

  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return []

  const riskyMappings: string[] = []
  for (const [rawFrom, rawTo] of Object.entries(parsed)) {
    const fromCode = parseStatusCodeKey(rawFrom)
    const toCode = parseStatusCodeMappingTarget(rawTo)
    if (fromCode === null || toCode === null) continue
    if (!NON_REDIRECTABLE_STATUS_CODES.has(fromCode)) continue
    if (fromCode === toCode) continue
    riskyMappings.push(`${fromCode} -> ${toCode}`)
  }

  return [...new Set(riskyMappings)].sort()
}

/** 对比初始值，返回新增的高危重定向项（仅拦截本次编辑新增的风险项） */
export function collectNewDisallowedStatusCodeRedirects(
  originalStr: string,
  currentStr: string
): string[] {
  const currentRisky = collectDisallowedStatusCodeRedirects(currentStr)
  if (currentRisky.length === 0) return []

  const originalRiskySet = new Set(
    collectDisallowedStatusCodeRedirects(originalStr)
  )
  return currentRisky.filter((mapping) => !originalRiskySet.has(mapping))
}
