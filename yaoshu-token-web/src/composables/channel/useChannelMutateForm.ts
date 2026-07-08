/**
 * 渠道表单状态管理（创建/编辑）。
 *
 * 职责：表单数据持有 / 初始化 / 校验 / 提交（create|update）。
 * 不含：多密钥批量（第四批）/ Codex（第四批）。
 */
import { reactive, ref, computed, shallowRef, inject, type InjectionKey } from 'vue'
import { createChannel, updateChannel, getChannel } from '@/api/channel'
import type { Channel } from '@/api/channel/types'
import {
  CHANNEL_FORM_DEFAULT_VALUES,
  transformChannelToFormDefaults,
  transformFormDataToCreatePayload,
  transformFormDataToUpdatePayload,
  isOptionalJsonObject,
  isOptionalModelMapping,
  isOptionalStatusCodeMapping,
  normalizeBaseUrl,
  findMissingModelsInMapping
} from '@/lib/channel/channel-form'
import type { ChannelFormValues } from '@/lib/channel/channel-form'
import { collectNewDisallowedStatusCodeRedirects } from '@/lib/channel/status-code-risk-guard'

/** 需要 base_url 的渠道类型（Azure/Custom/SunoAPI/VolcEngine） */
const REQUIRES_BASE_URL_TYPES = new Set([3, 8, 36, 45])

/** 表单校验错误映射（字段名 → 错误消息 i18n key） */
export type FormErrors = Partial<Record<keyof ChannelFormValues | 'models_mapping', string>>

export interface UseChannelMutateFormOptions {
  /** 提交成功回调 */
  onSuccess?: (mode: 'create' | 'update', channel: Channel | null) => void
}

/** composable 返回类型 */
export type ChannelMutateFormContext = ReturnType<typeof useChannelMutateForm>

/** provide/inject 注入键 */
export const CHANNEL_MUTATE_FORM_KEY: InjectionKey<ChannelMutateFormContext> = Symbol('channelMutateForm')

/** 在子组件中注入表单上下文 */
export function useChannelMutateFormContext(): ChannelMutateFormContext {
  const ctx = inject(CHANNEL_MUTATE_FORM_KEY)
  if (!ctx) {
    throw new Error('useChannelMutateFormContext 必须在 ChannelMutateDrawer 内部使用')
  }
  return ctx
}

export function useChannelMutateForm(options: UseChannelMutateFormOptions = {}) {
  const form = reactive<ChannelFormValues>({ ...CHANNEL_FORM_DEFAULT_VALUES })
  const errors = reactive<FormErrors>({})
  const isEditing = ref(false)
  const editingChannelId = ref<number | null>(null)
  const submitting = ref(false)
  const initLoading = ref(false)
  /** 编辑时缓存的原始渠道数据（用于缺失模型检测等） */
  const originalChannel = shallowRef<Channel | null>(null)
  /** 编辑模式初始 status_code_mapping 快照（用于风险守卫对比新增高危项） */
  const initialStatusCodeMapping = ref('')

  const isCreateMode = computed(() => !isEditing.value)

  // ==========================================================================
  // 初始化
  // ==========================================================================

  /** 重置表单为默认值 */
  function resetForm(): void {
    Object.assign(form, CHANNEL_FORM_DEFAULT_VALUES)
    Object.keys(errors).forEach((k) => delete errors[k as keyof FormErrors])
    isEditing.value = false
    editingChannelId.value = null
    originalChannel.value = null
    initialStatusCodeMapping.value = ''
  }

  /** 初始化为创建模式 */
  function initCreate(): void {
    resetForm()
  }

  /** 初始化为编辑模式（拉取渠道详情） */
  async function initEdit(channelId: number): Promise<void> {
    resetForm()
    isEditing.value = true
    editingChannelId.value = channelId
    initLoading.value = true
    try {
      const res = await getChannel(channelId)
      if (res) {
        originalChannel.value = res
        Object.assign(form, transformChannelToFormDefaults(res))
        initialStatusCodeMapping.value = form.status_code_mapping
      }
    } finally {
      initLoading.value = false
    }
  }

  /** 直接用渠道对象初始化（编辑模式，跳过 API 拉取） */
  function initEditWithChannel(channel: Channel): void {
    resetForm()
    isEditing.value = true
    editingChannelId.value = channel.id
    originalChannel.value = channel
    Object.assign(form, transformChannelToFormDefaults(channel))
    initialStatusCodeMapping.value = form.status_code_mapping
  }

  // ==========================================================================
  // 校验
  // ==========================================================================

  /** 校验表单，返回是否通过 */
  function validate(): boolean {
    Object.keys(errors).forEach((k) => delete errors[k as keyof FormErrors])

    // 名称必填
    if (!form.name.trim()) {
      errors.name = 'channel.edit.validate.nameRequired'
    }

    // 类型必填（>=0）
    if (form.type == null || form.type < 0) {
      errors.type = 'channel.edit.validate.typeRequired'
    }

    // base_url 校验（特定类型必填）
    if (REQUIRES_BASE_URL_TYPES.has(form.type) && !normalizeBaseUrl(form.base_url)) {
      errors.base_url = 'channel.edit.validate.baseUrlRequired'
    }

    // 创建模式密钥必填
    if (!isEditing.value && !form.key.trim()) {
      errors.key = 'channel.edit.validate.keyRequired'
    }

    // 模型必填
    if (!form.models.trim()) {
      errors.models = 'channel.edit.validate.modelsRequired'
    }

    // 分组必填
    if (!form.group || form.group.length === 0) {
      errors.group = 'channel.edit.validate.groupRequired'
    }

    // model_mapping 格式校验
    if (!isOptionalModelMapping(form.model_mapping)) {
      errors.model_mapping = 'channel.edit.validate.modelMappingInvalid'
    }

    // status_code_mapping 格式校验
    if (!isOptionalStatusCodeMapping(form.status_code_mapping)) {
      errors.status_code_mapping = 'channel.edit.validate.statusCodeMappingInvalid'
    }

    // param_override 格式校验
    if (!isOptionalJsonObject(form.param_override)) {
      errors.param_override = 'channel.edit.validate.paramOverrideInvalid'
    }

    // header_override 格式校验
    if (!isOptionalJsonObject(form.header_override)) {
      errors.header_override = 'channel.edit.validate.headerOverrideInvalid'
    }

    return Object.keys(errors).length === 0
  }

  /** 检测 model_mapping 中引用了但不在 models 列表中的源模型 */
  function getMissingMappingModels(): string[] {
    return findMissingModelsInMapping(form.models, form.model_mapping)
  }

  // ==========================================================================
  // 提交
  // ==========================================================================

  /** 提交表单（创建或更新） */
  async function submit(
    opts: { skipRiskGuard?: boolean } = {}
  ): Promise<{ success: boolean; missingModels?: string[]; statusCodeRisk?: string[] }> {
    if (!validate()) {
      return { success: false }
    }

    // 风险守卫：检测新增的高危状态码重定向（如 504/524 被映射到其他码）
    if (!opts.skipRiskGuard && form.status_code_mapping?.trim()) {
      const riskyRedirects = collectNewDisallowedStatusCodeRedirects(
        initialStatusCodeMapping.value,
        form.status_code_mapping
      )
      if (riskyRedirects.length > 0) {
        return { success: false, statusCodeRisk: riskyRedirects }
      }
    }

    // 编辑模式：检测缺失模型，交由调用方决定是否继续
    if (isEditing.value) {
      const missing = getMissingMappingModels()
      if (missing.length > 0) {
        return { success: false, missingModels: missing }
      }
    }

    submitting.value = true
    try {
      if (isEditing.value && editingChannelId.value != null) {
        const payload = transformFormDataToUpdatePayload(form, editingChannelId.value)
        await updateChannel(editingChannelId.value, payload)
        options.onSuccess?.('update', null)
        return { success: true }
      } else {
        const payload = transformFormDataToCreatePayload(form)
        await createChannel(payload)
        options.onSuccess?.('create', null)
        return { success: true }
      }
    } finally {
      submitting.value = false
    }
  }

  /** 强制提交（跳过缺失模型检测，用于用户确认后） */
  async function forceSubmit(): Promise<{ success: boolean }> {
    if (!validate()) {
      return { success: false }
    }
    submitting.value = true
    try {
      if (isEditing.value && editingChannelId.value != null) {
        const payload = transformFormDataToUpdatePayload(form, editingChannelId.value)
        await updateChannel(editingChannelId.value, payload)
        options.onSuccess?.('update', null)
      } else {
        const payload = transformFormDataToCreatePayload(form)
        await createChannel(payload)
        options.onSuccess?.('create', null)
      }
      return { success: true }
    } finally {
      submitting.value = false
    }
  }

  return {
    form,
    errors,
    isEditing,
    isCreateMode,
    editingChannelId,
    submitting,
    initLoading,
    originalChannel,
    initCreate,
    initEdit,
    initEditWithChannel,
    resetForm,
    validate,
    getMissingMappingModels,
    submit,
    forceSubmit
  }
}
