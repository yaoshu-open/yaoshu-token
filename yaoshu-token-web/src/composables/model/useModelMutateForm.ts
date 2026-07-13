/**
 * 模型编辑表单 composable。
 *
 * 职责：创建/编辑表单状态 + 校验 + 提交。
 * 不负责：列表数据加载（useModelsData）/ 操作确认（useModelActions）。
 */
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { createModel, getModel, getVendors, updateModel } from '@/api/model'
import { NAME_RULE_OPTIONS } from '@/api/model/constants'
import type { ModelFormData, Vendor } from '@/api/model/types'
import { modelFormToApi, modelToForm } from '@/lib/model/model-utils'

const DEFAULT_FORM: ModelFormData = {
  modelName: '',
  description: '',
  icon: '',
  tags: [],
  vendorId: undefined,
  endpoints: '',
  nameRule: 0,
  status: true,
  syncOfficial: true,
}

export function useModelMutateForm() {
  const { t } = useI18n()
  const visible = ref(false)
  const loading = ref(false)
  const submitting = ref(false)
  const mode = ref<'create' | 'update'>('create')
  const editingId = ref<number | null>(null)
  const formData = reactive<ModelFormData>({ ...DEFAULT_FORM })
  const errors = reactive<Record<string, string>>({})
  const vendors = ref<Vendor[]>([])

  // ============================================================================
  // 校验
  // ============================================================================

  function validate(): boolean {
    Object.keys(errors).forEach((k) => delete errors[k])

    if (!formData.modelName.trim()) {
      errors.modelName = t('model.form.modelNameRequired')
    }
    if (formData.endpoints && !isValidJSON(formData.endpoints)) {
      errors.endpoints = t('model.form.endpointsInvalid')
    }

    return Object.keys(errors).length === 0
  }

  function isValidJSON(str: string): boolean {
    try {
      JSON.parse(str)
      return true
    } catch {
      return false
    }
  }

  // ============================================================================
  // 加载供应商
  // ============================================================================

  async function fetchVendors(): Promise<void> {
    try {
      const data = await getVendors({ pageSize: 1000 })
      vendors.value = data.list
    } catch {
      // 非阻塞
    }
  }

  // ============================================================================
  // 初始化表单
  // ============================================================================

  function initCreate(): void {
    mode.value = 'create'
    editingId.value = null
    Object.assign(formData, DEFAULT_FORM)
    formData.tags = []
    Object.keys(errors).forEach((k) => delete errors[k])
    visible.value = true
    fetchVendors()
  }

  async function initUpdate(id: number): Promise<void> {
    mode.value = 'update'
    editingId.value = id
    loading.value = true
    visible.value = true
    Object.keys(errors).forEach((k) => delete errors[k])
    try {
      await fetchVendors()
      const model = await getModel(id)
      const formValues = modelToForm(model)
      Object.assign(formData, formValues)
    } catch {
      ElMessage.error(t('model.form.loadFailed'))
      visible.value = false
    } finally {
      loading.value = false
    }
  }

  // ============================================================================
  // 提交
  // ============================================================================

  async function submit(): Promise<boolean> {
    if (!validate()) return false

    submitting.value = true
    try {
      const apiPayload = modelFormToApi(formData) as unknown as Omit<ModelFormData, 'status'> & { status: number }
      if (mode.value === 'create') {
        await createModel(apiPayload)
      } else if (editingId.value != null) {
        await updateModel({ ...apiPayload, id: editingId.value })
      }
      ElMessage.success(mode.value === 'create' ? t('model.form.createSuccess') : t('model.form.updateSuccess'))
      visible.value = false
      return true
    } catch {
      ElMessage.error(t('common.operationFailed'))
      return false
    } finally {
      submitting.value = false
    }
  }

  function reset(): void {
    Object.assign(formData, DEFAULT_FORM)
    formData.tags = []
    Object.keys(errors).forEach((k) => delete errors[k])
  }

  return {
    visible,
    loading,
    submitting,
    mode,
    editingId,
    formData,
    errors,
    vendors,
    NAME_RULE_OPTIONS,
    initCreate,
    initUpdate,
    submit,
    reset,
  }
}
