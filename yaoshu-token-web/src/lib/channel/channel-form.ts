/**
 * 渠道表单类型 / 默认值 / 转换 / 校验纯函数。
 *
 * 范围（T-CH-04 第二批）：单密钥创建/编辑全字段。
 * 不含：多密钥批量模式（第四批）/ Codex 凭证（第四批）。
 */
import { CHANNEL_STATUS, MODEL_FETCHABLE_TYPES } from '@/api/channel/constants'
import type { Channel, UaOverrideMode } from '@/api/channel/types'

// ============================================================================
// 表单数据类型
// ============================================================================

export interface ChannelFormValues {
  // 基础信息
  name: string
  type: number
  base_url: string
  key: string
  openai_organization: string
  status: number
  // 模型与分组
  models: string
  group: string[]
  model_mapping: string
  // API 访问
  priority: number
  weight: number
  test_model: string
  auto_ban: number
  other: string
  // 高级设置
  tag: string
  remark: string
  status_code_mapping: string
  param_override: string
  header_override: string
  setting: string
  settings: string
  // 渠道额外设置（存储在 setting JSON）
  force_format: boolean
  thinking_to_content: boolean
  proxy: string
  pass_through_body_enabled: boolean
  system_prompt: string
  system_prompt_override: boolean
  // 类型特定设置（存储在 settings JSON）
  is_enterprise_account: boolean
  vertex_key_type: 'json' | 'api_key'
  aws_key_type: 'ak_sk' | 'api_key'
  azure_responses_version: string
  // 字段透传控制（存储在 settings JSON）
  allow_service_tier: boolean
  disable_store: boolean
  allow_safety_identifier: boolean
  allow_include_obfuscation: boolean
  allow_inference_geo: boolean
  allow_speed: boolean
  claude_beta_query: boolean
  // 上游模型更新设置（存储在 settings JSON）
  upstream_model_update_check_enabled: boolean
  upstream_model_update_auto_sync_enabled: boolean
  upstream_model_update_ignored_models: string
  // UA 覆盖模式（渠道表直接字段）
  ua_override_mode: UaOverrideMode
}

// ============================================================================
// 默认值
// ============================================================================

export const CHANNEL_FORM_DEFAULT_VALUES: ChannelFormValues = {
  name: '',
  type: 1,
  base_url: '',
  key: '',
  openai_organization: '',
  status: CHANNEL_STATUS.ENABLED,
  models: '',
  group: ['default'],
  model_mapping: '',
  priority: 0,
  weight: 0,
  test_model: '',
  auto_ban: 1,
  other: '',
  tag: '',
  remark: '',
  status_code_mapping: '',
  param_override: '',
  header_override: '',
  setting: '',
  settings: '{}',
  force_format: false,
  thinking_to_content: false,
  proxy: '',
  pass_through_body_enabled: false,
  system_prompt: '',
  system_prompt_override: false,
  is_enterprise_account: false,
  vertex_key_type: 'json',
  aws_key_type: 'ak_sk',
  azure_responses_version: '',
  allow_service_tier: false,
  disable_store: false,
  allow_safety_identifier: false,
  allow_include_obfuscation: false,
  allow_inference_geo: false,
  allow_speed: false,
  claude_beta_query: false,
  upstream_model_update_check_enabled: false,
  upstream_model_update_auto_sync_enabled: false,
  upstream_model_update_ignored_models: '',
  ua_override_mode: 'AUTO'
}

// ============================================================================
// 解析 / 格式化辅助
// ============================================================================

/** 解析模型字符串为数组 */
export function parseModelsString(models: string): string[] {
  if (!models) return []
  return models
    .split(',')
    .map((m) => m.trim())
    .filter((m) => m.length > 0)
}

/** 格式化模型数组为字符串 */
export function formatModelsArray(models: string[]): string {
  return models.filter(Boolean).join(',')
}

/** 解析分组字符串为数组 */
export function parseGroupsString(groups: string): string[] {
  if (!groups) return []
  return groups
    .split(',')
    .map((g) => g.trim())
    .filter((g) => g.length > 0)
}

/** 格式化分组数组为字符串 */
export function formatGroupsArray(groups: string[]): string {
  return groups.filter(Boolean).join(',')
}

/** 规范化 base_url（去除尾部斜杠） */
export function normalizeBaseUrl(value: string | undefined): string {
  return String(value || '')
    .trim()
    .replace(/\/+$/, '')
}

// ============================================================================
// JSON 校验
// ============================================================================

/** 校验 JSON 字符串合法性（空串视为合法） */
export function validateJSON(value: string): boolean {
  if (!value || value.trim() === '') return true
  try {
    JSON.parse(value)
    return true
  } catch {
    return false
  }
}

/** 校验是否为合法的 JSON 对象（空串视为合法） */
export function isOptionalJsonObject(value: string | undefined): boolean {
  if (!value || value.trim() === '') return true
  try {
    const parsed = JSON.parse(value)
    return typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)
  } catch {
    return false
  }
}

/** 校验模型映射格式（JSON 对象，值为字符串） */
export function isOptionalModelMapping(value: string | undefined): boolean {
  if (!value || value.trim() === '') return true
  try {
    const parsed = JSON.parse(value)
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
      return false
    }
    return Object.values(parsed).every((item) => typeof item === 'string')
  } catch {
    return false
  }
}

/** 校验状态码映射格式（键值均为合法 HTTP 状态码） */
export function isOptionalStatusCodeMapping(value: string | undefined): boolean {
  if (!value || value.trim() === '') return true
  try {
    const parsed = JSON.parse(value)
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
      return false
    }
    return Object.entries(parsed).every(([from, to]) => {
      const fromCode = Number(from)
      const toCode = Number(to)
      return (
        Number.isInteger(fromCode) &&
        Number.isInteger(toCode) &&
        fromCode >= 100 &&
        fromCode <= 599 &&
        toCode >= 100 &&
        toCode <= 599
      )
    })
  } catch {
    return false
  }
}

// ============================================================================
// setting / settings JSON 构建
// ============================================================================

/** 从表单额外设置构建 setting JSON 字符串 */
function buildSettingJSON(formData: ChannelFormValues): string {
  const settingObj = {
    force_format: formData.force_format || false,
    thinking_to_content: formData.thinking_to_content || false,
    proxy: formData.proxy || '',
    pass_through_body_enabled: formData.pass_through_body_enabled || false,
    system_prompt: formData.system_prompt || '',
    system_prompt_override: formData.system_prompt_override || false
  }
  return JSON.stringify(settingObj)
}

/** 从表单类型特定设置构建 settings JSON 字符串 */
function buildSettingsJSON(formData: ChannelFormValues): string {
  let settingsObj: Record<string, unknown> = {}

  if (formData.settings && formData.settings !== '{}') {
    try {
      const parsed = JSON.parse(formData.settings)
      if (typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)) {
        settingsObj = parsed as Record<string, unknown>
      }
    } catch {
      // 保留空对象
    }
  }

  // Vertex AI (type 41)
  if (formData.type === 41) {
    settingsObj.vertex_key_type = formData.vertex_key_type || 'json'
  } else if ('vertex_key_type' in settingsObj) {
    delete settingsObj.vertex_key_type
  }

  // Azure (type 3)
  if (formData.type === 3 && formData.azure_responses_version) {
    settingsObj.azure_responses_version = formData.azure_responses_version
  } else if ('azure_responses_version' in settingsObj) {
    delete settingsObj.azure_responses_version
  }

  // OpenRouter (type 20)
  if (formData.type === 20) {
    settingsObj.openrouter_enterprise = formData.is_enterprise_account === true
  } else if ('openrouter_enterprise' in settingsObj) {
    delete settingsObj.openrouter_enterprise
  }

  // AWS (type 33)
  if (formData.type === 33) {
    settingsObj.aws_key_type = formData.aws_key_type || 'ak_sk'
  } else if ('aws_key_type' in settingsObj) {
    delete settingsObj.aws_key_type
  }

  // 字段透传：OpenAI(1) + Anthropic(14)
  if (formData.type === 1 || formData.type === 14) {
    settingsObj.allow_service_tier = formData.allow_service_tier === true
  } else if ('allow_service_tier' in settingsObj) {
    delete settingsObj.allow_service_tier
  }

  // OpenAI(1) 独有
  if (formData.type === 1) {
    settingsObj.disable_store = formData.disable_store === true
    settingsObj.allow_safety_identifier = formData.allow_safety_identifier === true
    settingsObj.allow_include_obfuscation = formData.allow_include_obfuscation === true
    settingsObj.allow_inference_geo = formData.allow_inference_geo === true
  } else {
    if ('disable_store' in settingsObj) delete settingsObj.disable_store
    if ('allow_safety_identifier' in settingsObj) delete settingsObj.allow_safety_identifier
    if ('allow_include_obfuscation' in settingsObj) delete settingsObj.allow_include_obfuscation
    if (formData.type !== 14 && 'allow_inference_geo' in settingsObj) {
      delete settingsObj.allow_inference_geo
    }
  }

  // Anthropic(14) 独有
  if (formData.type === 14) {
    settingsObj.allow_inference_geo = formData.allow_inference_geo === true
    settingsObj.allow_speed = formData.allow_speed === true
    settingsObj.claude_beta_query = formData.claude_beta_query === true
  } else {
    if ('allow_speed' in settingsObj) delete settingsObj.allow_speed
    if ('claude_beta_query' in settingsObj) delete settingsObj.claude_beta_query
  }

  // 上游模型更新设置（仅可获取上游模型的类型）
  if (MODEL_FETCHABLE_TYPES.has(formData.type)) {
    settingsObj.upstream_model_update_check_enabled =
      formData.upstream_model_update_check_enabled === true
    settingsObj.upstream_model_update_auto_sync_enabled =
      formData.upstream_model_update_check_enabled === true &&
      formData.upstream_model_update_auto_sync_enabled === true
    settingsObj.upstream_model_update_ignored_models = Array.from(
      new Set(
        String(formData.upstream_model_update_ignored_models || '')
          .split(',')
          .map((model) => model.trim())
          .filter(Boolean)
      )
    )
    if (!Array.isArray(settingsObj.upstream_model_update_last_detected_models)) {
      settingsObj.upstream_model_update_last_detected_models = []
    }
    if (typeof settingsObj.upstream_model_update_last_check_time !== 'number') {
      settingsObj.upstream_model_update_last_check_time = 0
    }
  }

  return JSON.stringify(settingsObj)
}

// ============================================================================
// 转换：Channel → 表单默认值
// ============================================================================

export function transformChannelToFormDefaults(channel: Channel): ChannelFormValues {
  // 解析 setting JSON（渠道额外设置）
  let extraSettings = {
    force_format: false,
    thinking_to_content: false,
    proxy: '',
    pass_through_body_enabled: false,
    system_prompt: '',
    system_prompt_override: false
  }

  if (channel.setting) {
    try {
      const parsed = JSON.parse(channel.setting)
      if (typeof parsed === 'object' && parsed !== null) {
        extraSettings = {
          force_format: parsed.force_format || false,
          thinking_to_content: parsed.thinking_to_content || false,
          proxy: parsed.proxy || '',
          pass_through_body_enabled: parsed.pass_through_body_enabled || false,
          system_prompt: parsed.system_prompt || '',
          system_prompt_override: parsed.system_prompt_override || false
        }
      }
    } catch {
      // 忽略解析错误，使用默认值
    }
  }

  // 解析 settings JSON（类型特定设置）
  let vertexKeyType: 'json' | 'api_key' = 'json'
  let azureResponsesVersion = ''
  let isEnterpriseAccount = false
  let awsKeyType: 'ak_sk' | 'api_key' = 'ak_sk'
  let allowServiceTier = false
  let disableStore = false
  let allowSafetyIdentifier = false
  let allowIncludeObfuscation = false
  let allowInferenceGeo = false
  let allowSpeed = false
  let claudeBetaQuery = false
  let upstreamCheckEnabled = false
  let upstreamAutoSyncEnabled = false
  let upstreamIgnoredModels = ''

  if (channel.settings) {
    try {
      const parsed = JSON.parse(channel.settings)
      if (typeof parsed === 'object' && parsed !== null) {
        vertexKeyType = parsed.vertex_key_type || 'json'
        azureResponsesVersion = parsed.azure_responses_version || ''
        isEnterpriseAccount = parsed.openrouter_enterprise === true
        awsKeyType = parsed.aws_key_type || 'ak_sk'
        allowServiceTier = parsed.allow_service_tier === true
        disableStore = parsed.disable_store === true
        allowSafetyIdentifier = parsed.allow_safety_identifier === true
        allowIncludeObfuscation = parsed.allow_include_obfuscation === true
        allowInferenceGeo = parsed.allow_inference_geo === true
        allowSpeed = parsed.allow_speed === true
        claudeBetaQuery = parsed.claude_beta_query === true
        upstreamCheckEnabled = parsed.upstream_model_update_check_enabled === true
        upstreamAutoSyncEnabled = parsed.upstream_model_update_auto_sync_enabled === true
        upstreamIgnoredModels = Array.isArray(parsed.upstream_model_update_ignored_models)
          ? parsed.upstream_model_update_ignored_models.join(',')
          : ''
      }
    } catch {
      // 忽略解析错误
    }
  }

  return {
    name: channel.name || '',
    type: channel.type,
    base_url: channel.baseUrl || '',
    key: '', // 出于安全考虑，不从后端回填密钥
    openai_organization: channel.openaiOrganization || '',
    status: channel.status,
    models: channel.models || '',
    group: parseGroupsString(channel.group || 'default'),
    model_mapping: channel.modelMapping || '',
    priority: channel.priority || 0,
    weight: channel.weight || 0,
    test_model: channel.testModel || '',
    auto_ban: channel.autoBan ?? 1,
    other: channel.other || '',
    tag: channel.tag || '',
    remark: channel.remark || '',
    status_code_mapping: channel.statusCodeMapping || '',
    param_override: channel.paramOverride || '',
    header_override: channel.headerOverride || '',
    setting: channel.setting || '',
    settings: channel.settings || '{}',
    ...extraSettings,
    is_enterprise_account: isEnterpriseAccount,
    vertex_key_type: vertexKeyType,
    aws_key_type: awsKeyType,
    azure_responses_version: azureResponsesVersion,
    allow_service_tier: allowServiceTier,
    disable_store: disableStore,
    allow_safety_identifier: allowSafetyIdentifier,
    allow_include_obfuscation: allowIncludeObfuscation,
    allow_inference_geo: allowInferenceGeo,
    allow_speed: allowSpeed,
    claude_beta_query: claudeBetaQuery,
    upstream_model_update_check_enabled: upstreamCheckEnabled,
    upstream_model_update_auto_sync_enabled: upstreamAutoSyncEnabled,
    upstream_model_update_ignored_models: upstreamIgnoredModels,
    ua_override_mode: channel.uaOverrideMode || 'AUTO'
  }
}

// ============================================================================
// 转换：表单 → API 载荷
// ============================================================================

/** 表单 → 创建渠道 API 载荷 */
export function transformFormDataToCreatePayload(formData: ChannelFormValues): {
  mode: 'single'
  channel: Partial<Channel>
} {
  const channel: Partial<Channel> = {
    name: formData.name,
    type: formData.type,
    baseUrl: normalizeBaseUrl(formData.base_url) || null,
    key: formData.key,
    openaiOrganization: formData.openai_organization || null,
    models: formData.models,
    group: formatGroupsArray(formData.group),
    modelMapping: formData.model_mapping || null,
    priority: formData.priority ?? 0,
    weight: formData.weight ?? null,
    testModel: formData.test_model || null,
    autoBan: formData.auto_ban ?? 1,
    status: formData.status,
    statusCodeMapping: formData.status_code_mapping || null,
    tag: formData.tag || null,
    remark: formData.remark || '',
    setting: buildSettingJSON(formData),
    paramOverride: formData.param_override || null,
    headerOverride: formData.header_override || null,
    settings: buildSettingsJSON(formData),
    other: formData.other || '',
    uaOverrideMode: formData.ua_override_mode
  }

  // 空字符串转 null
  Object.keys(channel).forEach((key) => {
    if (channel[key as keyof typeof channel] === '') {
      ;(channel as Record<string, unknown>)[key] = null
    }
  })

  return { mode: 'single', channel }
}

/** 检测 key 是否为后端掩码（如 sk-****XXXX），掩码 key 不应回传保存 */
function isMaskedKey(key: string): boolean {
  return key.includes('****')
}

/** 表单 → 更新渠道 API 载荷 */
export function transformFormDataToUpdatePayload(
  formData: ChannelFormValues,
  channelId: number
): Partial<Channel> {
  const payload: Partial<Channel> = {
    id: channelId,
    name: formData.name,
    type: formData.type,
    baseUrl: normalizeBaseUrl(formData.base_url) || '',
    openaiOrganization: formData.openai_organization || '',
    models: formData.models,
    group: formatGroupsArray(formData.group),
    modelMapping: formData.model_mapping || '',
    priority: formData.priority ?? 0,
    weight: formData.weight ?? 0,
    testModel: formData.test_model || '',
    autoBan: formData.auto_ban ?? 1,
    status: formData.status,
    statusCodeMapping: formData.status_code_mapping || '',
    tag: formData.tag || '',
    remark: formData.remark || '',
    setting: buildSettingJSON(formData),
    paramOverride: formData.param_override || '',
    headerOverride: formData.header_override || '',
    settings: buildSettingsJSON(formData),
    other: formData.other || '',
    uaOverrideMode: formData.ua_override_mode
  }

  // 仅当密钥非空且非掩码时包含（编辑时后端返回掩码key，回传会覆盖真实key）
  if (formData.key && formData.key.trim() && !isMaskedKey(formData.key)) {
    payload.key = formData.key
  }

  return payload
}

// ============================================================================
// 模型映射辅助
// ============================================================================

/** 从 model_mapping 提取目标模型（重定向后的模型） */
export function extractRedirectModels(modelMapping: string): string[] {
  if (!modelMapping?.trim()) return []
  try {
    const parsed = JSON.parse(modelMapping)
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
      return []
    }
    return Object.values(parsed)
      .map((v) => String(v || '').trim())
      .filter(Boolean)
  } catch {
    return []
  }
}

/** 从 model_mapping 提取源模型（被重定向的模型） */
export function extractMappingSourceModels(modelMapping: string): string[] {
  if (!modelMapping?.trim()) return []
  try {
    const parsed = JSON.parse(modelMapping)
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
      return []
    }
    return Object.keys(parsed)
      .map((k) => k.trim())
      .filter(Boolean)
  } catch {
    return []
  }
}

/** 查找 model_mapping 中引用了但不在 models 列表中的源模型 */
export function findMissingModelsInMapping(
  models: string,
  modelMapping: string
): string[] {
  const modelsArray = parseModelsString(models)
  const sourceModels = extractMappingSourceModels(modelMapping)
  return sourceModels.filter((source) => !modelsArray.includes(source))
}

/** 密钥去重（按换行/逗号分隔） */
export function deduplicateKeys(keyText: string): {
  deduplicatedText: string
  removedCount: number
  beforeCount: number
  afterCount: number
} {
  const keys = keyText
    .split(/[\n,]/)
    .map((k) => k.trim())
    .filter(Boolean)
  const beforeCount = keys.length
  const seen = new Set<string>()
  const unique: string[] = []
  for (const k of keys) {
    if (!seen.has(k)) {
      seen.add(k)
      unique.push(k)
    }
  }
  return {
    deduplicatedText: unique.join('\n'),
    removedCount: beforeCount - unique.length,
    beforeCount,
    afterCount: unique.length
  }
}
