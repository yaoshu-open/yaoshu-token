/**
 * 参数覆写配置常量与工具函数。
 *
 * 纯数据/纯逻辑文件，不含 Vue 响应式。
 */

// ============================================================================
// 类型
// ============================================================================

export interface ParamOverrideCondition {
  id: string
  path: string
  mode: string
  value_text: string
  invert: boolean
  pass_missing_key: boolean
}

export interface ParamOverrideOperation {
  id: string
  description: string
  path: string
  mode: string
  from: string
  to: string
  value_text: string
  keep_origin: boolean
  logic: string
  conditions: ParamOverrideCondition[]
}

// ============================================================================
// 常量
// ============================================================================

let localIdSeed = 0
function nextLocalId(): string {
  return `po_${Date.now()}_${localIdSeed++}`
}

export const OPERATION_MODE_OPTIONS: Array<{ label: string; value: string }> = [
  { label: 'Set Field', value: 'set' },
  { label: 'Delete Field', value: 'delete' },
  { label: 'Append to End', value: 'append' },
  { label: 'Prepend to Start', value: 'prepend' },
  { label: 'Copy Field', value: 'copy' },
  { label: 'Move Field', value: 'move' },
  { label: 'String Replace', value: 'replace' },
  { label: 'Regex Replace', value: 'regex_replace' },
  { label: 'Trim Prefix', value: 'trim_prefix' },
  { label: 'Trim Suffix', value: 'trim_suffix' },
  { label: 'Ensure Prefix', value: 'ensure_prefix' },
  { label: 'Ensure Suffix', value: 'ensure_suffix' },
  { label: 'Trim Space', value: 'trim_space' },
  { label: 'To Lowercase', value: 'to_lower' },
  { label: 'To Uppercase', value: 'to_upper' },
  { label: 'Return Custom Error', value: 'return_error' },
  { label: 'Prune Object Items', value: 'prune_objects' },
  { label: 'Pass Through Headers', value: 'pass_headers' },
  { label: 'Sync Fields', value: 'sync_fields' },
  { label: 'Set Request Header', value: 'set_header' },
  { label: 'Delete Request Header', value: 'delete_header' },
  { label: 'Copy Request Header', value: 'copy_header' },
  { label: 'Move Request Header', value: 'move_header' }
]

export const CONDITION_MODE_OPTIONS: Array<{ label: string; value: string }> = [
  { label: 'Exact Match', value: 'full' },
  { label: 'Prefix', value: 'prefix' },
  { label: 'Suffix', value: 'suffix' },
  { label: 'Contains', value: 'contains' },
  { label: 'Greater Than', value: 'gt' },
  { label: 'Greater Than or Equal', value: 'gte' },
  { label: 'Less Than', value: 'lt' },
  { label: 'Less Than or Equal', value: 'lte' }
]

const OPERATION_MODE_VALUES = new Set(OPERATION_MODE_OPTIONS.map((o) => o.value))
const CONDITION_MODE_VALUES = new Set(CONDITION_MODE_OPTIONS.map((o) => o.value))

export const OPERATION_MODE_LABEL_MAP = OPERATION_MODE_OPTIONS.reduce<Record<string, string>>(
  (acc, item) => {
    acc[item.value] = item.label
    return acc
  },
  {}
)

interface ModeMeta {
  path?: boolean
  pathOptional?: boolean
  value?: boolean
  from?: boolean
  to?: boolean
  keepOrigin?: boolean
  pathAlias?: boolean
}

export const MODE_META: Record<string, ModeMeta> = {
  delete: { path: true },
  set: { path: true, value: true, keepOrigin: true },
  append: { path: true, value: true, keepOrigin: true },
  prepend: { path: true, value: true, keepOrigin: true },
  copy: { from: true, to: true },
  move: { from: true, to: true },
  replace: { path: true, from: true },
  regex_replace: { path: true, from: true },
  trim_prefix: { path: true, value: true },
  trim_suffix: { path: true, value: true },
  ensure_prefix: { path: true, value: true },
  ensure_suffix: { path: true, value: true },
  trim_space: { path: true },
  to_lower: { path: true },
  to_upper: { path: true },
  return_error: { value: true },
  prune_objects: { pathOptional: true, value: true },
  pass_headers: { value: true, keepOrigin: true },
  sync_fields: { from: true, to: true },
  set_header: { path: true, value: true, keepOrigin: true },
  delete_header: { path: true },
  copy_header: { from: true, to: true, keepOrigin: true, pathAlias: true },
  move_header: { from: true, to: true, keepOrigin: true, pathAlias: true }
}

export const VALUE_REQUIRED_MODES = new Set([
  'trim_prefix', 'trim_suffix', 'ensure_prefix', 'ensure_suffix',
  'set_header', 'return_error', 'prune_objects', 'pass_headers'
])

export const FROM_REQUIRED_MODES = new Set([
  'copy', 'move', 'replace', 'regex_replace',
  'copy_header', 'move_header', 'sync_fields'
])

export const TO_REQUIRED_MODES = new Set([
  'copy', 'move', 'copy_header', 'move_header', 'sync_fields'
])

export const MODE_DESCRIPTIONS: Record<string, string> = {
  set: 'Write value to the target field',
  delete: 'Remove the target field',
  append: 'Append value to array / string / object end',
  prepend: 'Prepend value to array / string / object start',
  copy: 'Copy source field to target field',
  move: 'Move source field to target field',
  replace: 'Do string replacement in the target field',
  regex_replace: 'Do regex replacement in the target field',
  trim_prefix: 'Remove string prefix',
  trim_suffix: 'Remove string suffix',
  ensure_prefix: 'Ensure the string has a specified prefix',
  ensure_suffix: 'Ensure the string has a specified suffix',
  trim_space: 'Trim leading/trailing whitespace',
  to_lower: 'Convert string to lowercase',
  to_upper: 'Convert string to uppercase',
  return_error: 'Return a custom error immediately',
  prune_objects: 'Prune object items by conditions',
  pass_headers: 'Pass specified request headers to upstream',
  sync_fields: 'Auto-fill when one field exists and another is missing',
  set_header: 'Set runtime request header',
  delete_header: 'Delete a runtime request header',
  copy_header: 'Copy a request header',
  move_header: 'Move a request header'
}

// ============================================================================
// 模板预设
// ============================================================================

export interface TemplatePreset {
  label: string
  payload: Record<string, unknown>
}

export const TEMPLATE_PRESETS: Record<string, TemplatePreset> = {
  operations_default: {
    label: 'New Format Template',
    payload: {
      operations: [
        {
          description: 'Set default temperature for openai/* models.',
          path: 'temperature',
          mode: 'set',
          value: 0.7,
          conditions: [{ path: 'model', mode: 'prefix', value: 'openai/' }],
          logic: 'AND'
        }
      ]
    }
  },
  pass_headers: {
    label: 'Header Passthrough (X-Request-Id)',
    payload: {
      operations: [
        {
          description: 'Pass through X-Request-Id header to upstream.',
          mode: 'pass_headers',
          value: ['X-Request-Id'],
          keep_origin: true
        }
      ]
    }
  },
  gemini_image_4k: {
    label: 'Gemini Image 4K',
    payload: {
      operations: [
        {
          description: 'Set imageSize to 4K for gemini image models ending with 4k.',
          mode: 'set',
          path: 'generationConfig.imageConfig.imageSize',
          value: '4K',
          conditions: [
            { path: 'original_model', mode: 'contains', value: 'gemini' },
            { path: 'original_model', mode: 'contains', value: 'image' },
            { path: 'original_model', mode: 'suffix', value: '4k' }
          ],
          logic: 'AND'
        }
      ]
    }
  },
  codex_cli_headers: {
    label: 'Codex CLI Header Passthrough',
    payload: {
      operations: [
        {
          mode: 'pass_headers',
          value: [
            'Originator', 'Session_id', 'User-Agent',
            'X-Codex-Beta-Features', 'X-Codex-Turn-Metadata'
          ],
          keep_origin: true
        }
      ]
    }
  },
  claude_cli_headers: {
    label: 'Claude CLI Header Passthrough',
    payload: {
      operations: [
        {
          mode: 'pass_headers',
          value: [
            'X-Stainless-Arch', 'X-Stainless-Lang', 'X-Stainless-Os',
            'X-Stainless-Package-Version', 'X-Stainless-Retry-Count',
            'X-Stainless-Runtime', 'X-Stainless-Runtime-Version',
            'X-Stainless-Timeout', 'User-Agent', 'X-App',
            'Anthropic-Beta', 'Anthropic-Dangerous-Direct-Browser-Access',
            'Anthropic-Version'
          ],
          keep_origin: true
        }
      ]
    }
  }
}

// ============================================================================
// 工具函数
// ============================================================================

function toValueText(value: unknown): string {
  if (value === undefined) return ''
  if (typeof value === 'string') return value
  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}

function parseLooseValue(valueText: string): unknown {
  const raw = String(valueText ?? '').trim()
  if (raw === '') return ''
  try {
    return JSON.parse(raw)
  } catch {
    return raw
  }
}

export function normalizeCondition(condition: Record<string, unknown> = {}): ParamOverrideCondition {
  return {
    id: nextLocalId(),
    path: typeof condition.path === 'string' ? condition.path : '',
    mode: CONDITION_MODE_VALUES.has(condition.mode as string) ? (condition.mode as string) : 'full',
    value_text: toValueText(condition.value),
    invert: condition.invert === true,
    pass_missing_key: condition.pass_missing_key === true
  }
}

export function createDefaultCondition(): ParamOverrideCondition {
  return normalizeCondition({})
}

export function normalizeOperation(operation: Record<string, unknown> = {}): ParamOverrideOperation {
  return {
    id: nextLocalId(),
    description: typeof operation.description === 'string' ? operation.description : '',
    path: typeof operation.path === 'string' ? operation.path : '',
    mode: OPERATION_MODE_VALUES.has(operation.mode as string) ? (operation.mode as string) : 'set',
    value_text: toValueText(operation.value),
    keep_origin: operation.keep_origin === true,
    from: typeof operation.from === 'string' ? operation.from : '',
    to: typeof operation.to === 'string' ? operation.to : '',
    logic: String(operation.logic || 'OR').toUpperCase() === 'AND' ? 'AND' : 'OR',
    conditions: Array.isArray(operation.conditions)
      ? (operation.conditions as Record<string, unknown>[]).map(normalizeCondition)
      : []
  }
}

export function createDefaultOperation(): ParamOverrideOperation {
  return normalizeOperation({ mode: 'set' })
}

export function isOperationBlank(operation: ParamOverrideOperation): boolean {
  const hasCondition = operation.conditions.some(
    (c) => c.path.trim() || c.value_text.trim() || c.mode !== 'full' || c.invert || c.pass_missing_key
  )
  return (
    operation.mode === 'set' &&
    !operation.path.trim() &&
    !operation.from.trim() &&
    !operation.to.trim() &&
    operation.value_text.trim() === '' &&
    !operation.keep_origin &&
    !hasCondition
  )
}

function buildConditionPayload(condition: ParamOverrideCondition): Record<string, unknown> | null {
  if (!condition.path.trim() && !condition.value_text.trim()) return null
  const payload: Record<string, unknown> = {
    path: condition.path.trim(),
    mode: condition.mode,
    value: parseLooseValue(condition.value_text)
  }
  if (condition.invert) payload.invert = true
  if (condition.pass_missing_key) payload.pass_missing_key = true
  return payload
}

/** 校验操作列表，返回错误消息（null=通过） */
export function validateOperations(ops: ParamOverrideOperation[]): string | null {
  for (const op of ops) {
    const mode = op.mode || 'set'
    const meta = MODE_META[mode] || MODE_META.set

    if (meta.path && !op.path.trim()) return `Mode "${mode}": Target field path is required`
    if (meta.pathOptional && op.path.trim() === '' && mode === 'prune_objects') {
      // prune_objects 允许 path 为空
    }
    if (VALUE_REQUIRED_MODES.has(mode) && op.value_text.trim() === '') {
      return `Mode "${mode}": Value is required`
    }
    if (FROM_REQUIRED_MODES.has(mode) && !op.from.trim()) {
      return `Mode "${mode}": Source field is required`
    }
    if (TO_REQUIRED_MODES.has(mode) && !op.to.trim()) {
      return `Mode "${mode}": Target field is required`
    }
  }
  return null
}

/** 将操作列表序列化为 JSON 字符串 */
export function buildOperationsJson(operations: ParamOverrideOperation[]): string {
  const filteredOps = operations.filter((o) => !isOperationBlank(o))
  if (filteredOps.length === 0) return ''

  const payloadOps = filteredOps.map((operation) => {
    const mode = operation.mode || 'set'
    const meta = MODE_META[mode] || MODE_META.set
    const descriptionValue = operation.description.trim()
    const pathValue = operation.path.trim()
    const fromValue = operation.from.trim()
    const toValue = operation.to.trim()
    const payload: Record<string, unknown> = { mode }

    if (descriptionValue) payload.description = descriptionValue
    if (meta.path) payload.path = pathValue
    if (meta.pathOptional && pathValue) payload.path = pathValue
    if (meta.value) payload.value = parseLooseValue(operation.value_text)
    if (meta.keepOrigin && operation.keep_origin) payload.keep_origin = true
    if (meta.from) payload.from = fromValue
    if (!meta.to && operation.to.trim()) payload.to = toValue
    if (meta.to) payload.to = toValue
    if (meta.pathAlias) {
      if (!payload.from && pathValue) payload.from = pathValue
      if (!payload.to && pathValue) payload.to = pathValue
    }

    const conditions = operation.conditions.map(buildConditionPayload).filter(Boolean)
    if (conditions.length > 0) {
      payload.conditions = conditions
      payload.logic = operation.logic === 'AND' ? 'AND' : 'OR'
    }

    return payload
  })

  return JSON.stringify({ operations: payloadOps }, null, 2)
}

/** 解析 JSON 字符串为操作列表 */
export function parseParamOverride(value: string): {
  operations: ParamOverrideOperation[]
  isLegacy: boolean
  legacyValue: string
} {
  const trimmed = (value || '').trim()
  if (!trimmed) {
    return { operations: [createDefaultOperation()], isLegacy: false, legacyValue: '' }
  }

  try {
    const parsed = JSON.parse(trimmed) as unknown
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      const obj = parsed as Record<string, unknown>
      if (Array.isArray(obj.operations)) {
        const operations = (obj.operations as Record<string, unknown>[]).map(normalizeOperation)
        return {
          operations: operations.length > 0 ? operations : [createDefaultOperation()],
          isLegacy: false,
          legacyValue: ''
        }
      }
    }
    // 非 operations 格式 → 视为 legacy
    return {
      operations: [createDefaultOperation()],
      isLegacy: true,
      legacyValue: trimmed
    }
  } catch {
    return { operations: [createDefaultOperation()], isLegacy: true, legacyValue: trimmed }
  }
}

/** 获取操作摘要 */
export function getOperationSummary(operation: ParamOverrideOperation, index: number): string {
  const mode = operation.mode || 'set'
  const modeLabel = OPERATION_MODE_LABEL_MAP[mode] || mode
  if (mode === 'sync_fields') {
    const from = operation.from.trim()
    const to = operation.to.trim()
    return `${index + 1}. ${modeLabel} · ${from || to || '-'}`
  }
  const path = operation.path.trim()
  const from = operation.from.trim()
  const to = operation.to.trim()
  return `${index + 1}. ${modeLabel} · ${path || from || to || '-'}`
}
