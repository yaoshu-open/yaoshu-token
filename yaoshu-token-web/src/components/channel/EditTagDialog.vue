<script setup lang="ts">
/**
 * 渠道标签编辑 Dialog (T-CH-03)。
 * 补齐：default `features/channels/components/dialogs/edit-tag-dialog.tsx` 缺失的 param_override/header_override。
 *
 * 后端契约：ai-docs/后端设计/API_Contract/契约_渠道管理.md §三 标签管理。
 */
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect
} from 'element-plus'
import { editTagChannels, getAllModels, getGroups, getTagModels } from '@/api/channel'
import type { ChannelModel, TagOperationParams } from '@/api/channel/types'
import LoadingState from '@/components/LoadingState.vue'

const props = defineProps<{
  modelValue: boolean
  tag: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'success'): void
}>()

const { t } = useI18n()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const loading = ref(false)
const initLoading = ref(false)
const modelOptions = ref<{ label: string; value: string }[]>([])
const groupOptions = ref<{ label: string; value: string }[]>([])
const customModel = ref('')

interface EditTagForm {
  new_tag: string
  models: string[]
  model_mapping: string | null
  param_override: string | null
  header_override: string | null
  groups: string[]
}

const form = reactive<EditTagForm>({
  new_tag: '',
  models: [],
  model_mapping: null,
  param_override: null,
  header_override: null,
  groups: []
})

const MODEL_MAPPING_TEMPLATE = JSON.stringify({ 'gpt-3.5-turbo': 'gpt-3.5-turbo-0125' }, null, 2)
const EMPTY_JSON = JSON.stringify({}, null, 2)
const PARAM_OVERRIDE_OLD_TEMPLATE = JSON.stringify({ temperature: 0 }, null, 2)
const PARAM_OVERRIDE_NEW_TEMPLATE = JSON.stringify(
  {
    operations: [
      {
        path: 'temperature',
        mode: 'set',
        value: 0.7,
        conditions: [{ path: 'model', mode: 'prefix', value: 'gpt' }],
        logic: 'AND'
      }
    ]
  },
  null,
  2
)
const HEADER_OVERRIDE_TEMPLATE = JSON.stringify(
  {
    'User-Agent':
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36 Edg/139.0.0.0',
    Authorization: 'Bearer {api_key}'
  },
  null,
  2
)

const paramOverridePlaceholder = computed(() =>
  t('channel.tagEdit.advanced.paramOverridePlaceholder')
)

const headerOverridePlaceholder = computed(() =>
  t('channel.tagEdit.advanced.headerOverridePlaceholder')
)

const modelMappingPlaceholder = computed(() =>
  t('channel.tagEdit.modelConfig.modelMappingPlaceholder')
)

watch(
  visible,
  async (v) => {
    if (v && props.tag) {
      form.new_tag = props.tag
      form.models = []
      form.model_mapping = null
      form.param_override = null
      form.header_override = null
      form.groups = []
      customModel.value = ''
      await initFormData()
    }
  },
  { immediate: true }
)

async function initFormData(): Promise<void> {
  initLoading.value = true
  try {
    const [modelsRes, groupsRes, tagModelsRes] = await Promise.all([
      getAllModels(),
      getGroups(),
      getTagModels(props.tag)
    ])

    // 拦截器已解包：modelsRes 直接是 ChannelModel[]
    if (modelsRes && modelsRes.length > 0) {
      modelOptions.value = modelsRes.map((m: ChannelModel) => ({
        label: m.id,
        value: m.id
      }))
    }

    // 拦截器已解包：groupsRes 直接是 string[]
    if (groupsRes && groupsRes.length > 0) {
      groupOptions.value = groupsRes.map((g: string) => ({ label: g, value: g }))
    }

    // 拦截器已解包：tagModelsRes 直接是逗号分隔 string
    if (tagModelsRes) {
      form.models = tagModelsRes.split(',').filter(Boolean)
      form.models.forEach((m) => {
        if (!modelOptions.value.find((opt) => opt.value === m)) {
          modelOptions.value.push({ label: m, value: m })
        }
      })
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('common.error.title'))
  } finally {
    initLoading.value = false
  }
}

function addCustomModels(): void {
  if (customModel.value.trim() === '') return
  const modelArray = customModel.value
    .split(',')
    .map((m) => m.trim())
    .filter(Boolean)
  const addedModels: string[] = []

  modelArray.forEach((m) => {
    if (!form.models.includes(m)) {
      form.models.push(m)
      if (!modelOptions.value.find((opt) => opt.value === m)) {
        modelOptions.value.push({ label: m, value: m })
      }
      addedModels.push(m)
    }
  })

  customModel.value = ''

  if (addedModels.length > 0) {
    ElMessage.success(
      t('channel.tagEdit.actions.addedModels', {
        count: addedModels.length,
        list: addedModels.join(', ')
      })
    )
  } else {
    ElMessage.info(t('channel.tagEdit.actions.noNewModels'))
  }
}

function verifyJSON(str: string): boolean {
  try {
    JSON.parse(str.trim())
    return true
  } catch {
    return false
  }
}

async function handleSubmit(): Promise<void> {
  if (form.new_tag === '' && props.tag !== '') {
    try {
      await ElMessageBox.confirm(
        t('channel.tagEdit.confirm.dissolveDesc'),
        t('channel.tagEdit.confirm.dissolveTitle'),
        { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') }
      )
    } catch {
      return
    }
  }

  if (form.model_mapping && !verifyJSON(form.model_mapping)) {
    ElMessage.info(t('channel.tagEdit.validate.modelMappingInvalid'))
    return
  }
  if (form.param_override && !verifyJSON(form.param_override)) {
    ElMessage.info(t('channel.tagEdit.validate.paramOverrideInvalid'))
    return
  }
  if (form.header_override && !verifyJSON(form.header_override)) {
    ElMessage.info(t('channel.tagEdit.validate.headerOverrideInvalid'))
    return
  }

  const data: TagOperationParams = { tag: props.tag }

  if (form.model_mapping !== null && form.model_mapping !== '') {
    data.modelMapping = form.model_mapping
  }
  if (form.param_override !== null && form.param_override !== '') {
    data.paramOverride = form.param_override
  }
  if (form.header_override !== null && form.header_override !== '') {
    data.headerOverride = form.header_override
  }
  if (form.models.length > 0) {
    data.models = form.models.join(',')
  }
  if (form.groups.length > 0) {
    data.groups = form.groups.join(',')
  }
  data.newTag = form.new_tag

  const hasChanges =
    form.new_tag !== props.tag ||
    data.modelMapping !== undefined ||
    data.paramOverride !== undefined ||
    data.headerOverride !== undefined ||
    data.models !== undefined ||
    data.groups !== undefined

  if (!hasChanges) {
    ElMessage.warning(t('channel.tagEdit.validate.noChanges'))
    return
  }

  loading.value = true
  try {
    await editTagChannels(data)
    ElMessage.success(t('channel.tagEdit.actions.saveSuccess'))
    emit('success')
    visible.value = false
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('common.error.title'))
  } finally {
    loading.value = false
  }
}

function handleCancel(): void {
  visible.value = false
}
</script>

<template>
  <ElDialog
    v-model="visible"
    :title="t('channel.tagEdit.title')"
    width="640"
    :close-on-click-modal="false"
    align-center
    destroy-on-close
  >
    <template #header>
      <div class="edit-tag-dialog__header">
        <i class="i-ep-edit" />
        <span class="edit-tag-dialog__title">{{ t('channel.tagEdit.title') }}</span>
      </div>
    </template>

    <div
      v-if="initLoading"
      class="edit-tag-dialog__loading"
    >
      <LoadingState :message="t('channel.tagEdit.loading')" />
    </div>

    <ElForm
      v-else
      label-position="top"
      class="edit-tag-dialog__form"
    >
      <ElAlert
        :title="t('channel.tagEdit.description')"
        type="warning"
        :closable="false"
        show-icon
        class="edit-tag-dialog__alert"
      />

      <div class="edit-tag-dialog__section">
        <div class="edit-tag-dialog__section-header">
          <i class="i-ep-collection-tag" />
          <div class="edit-tag-dialog__section-title">
            <span class="edit-tag-dialog__section-name">{{ t('channel.tagEdit.tagInfo.title') }}</span>
            <span class="edit-tag-dialog__section-subtitle">{{ t('channel.tagEdit.tagInfo.subtitle') }}</span>
          </div>
        </div>
        <ElFormItem :label="t('channel.tagEdit.tagInfo.tagName')">
          <ElInput
            v-model="form.new_tag"
            :placeholder="t('channel.tagEdit.tagInfo.tagNamePlaceholder')"
            clearable
          />
        </ElFormItem>
      </div>

      <div class="edit-tag-dialog__section">
        <div class="edit-tag-dialog__section-header">
          <i class="i-ep-cpu" />
          <div class="edit-tag-dialog__section-title">
            <span class="edit-tag-dialog__section-name">{{ t('channel.tagEdit.modelConfig.title') }}</span>
            <span class="edit-tag-dialog__section-subtitle">{{ t('channel.tagEdit.modelConfig.subtitle') }}</span>
          </div>
        </div>
        <ElAlert
          :title="t('channel.tagEdit.modelConfig.modelsHint')"
          type="info"
          :closable="false"
          show-icon
          class="edit-tag-dialog__alert"
        />
        <ElFormItem :label="t('channel.tagEdit.modelConfig.models')">
          <ElSelect
            v-model="form.models"
            multiple
            filterable
            allow-create
            :placeholder="t('channel.tagEdit.modelConfig.modelsPlaceholder')"
            style="width: 100%"
          >
            <ElOption
              v-for="opt in modelOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem :label="t('channel.tagEdit.modelConfig.customModel')">
          <div class="edit-tag-dialog__custom-model">
            <ElInput
              v-model="customModel"
              :placeholder="t('channel.tagEdit.modelConfig.customModelPlaceholder')"
              clearable
            />
            <ElButton
              type="primary"
              size="small"
              @click="addCustomModels"
            >
              {{ t('channel.tagEdit.modelConfig.fillIn') }}
            </ElButton>
          </div>
        </ElFormItem>
        <ElFormItem :label="t('channel.tagEdit.modelConfig.modelMapping')">
          <ElInput
            v-model="form.model_mapping"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 10 }"
            :placeholder="modelMappingPlaceholder"
          />
          <div class="edit-tag-dialog__quick-actions">
            <ElButton
              text
              size="small"
              type="primary"
              @click="form.model_mapping = MODEL_MAPPING_TEMPLATE"
            >
              {{ t('channel.tagEdit.modelConfig.fillTemplate') }}
            </ElButton>
            <ElButton
              text
              size="small"
              type="primary"
              @click="form.model_mapping = EMPTY_JSON"
            >
              {{ t('channel.tagEdit.modelConfig.clearRedirect') }}
            </ElButton>
            <ElButton
              text
              size="small"
              type="primary"
              @click="form.model_mapping = null"
            >
              {{ t('channel.tagEdit.modelConfig.noChange') }}
            </ElButton>
          </div>
        </ElFormItem>
      </div>

      <div class="edit-tag-dialog__section">
        <div class="edit-tag-dialog__section-header">
          <i class="i-ep-setting" />
          <div class="edit-tag-dialog__section-title">
            <span class="edit-tag-dialog__section-name">{{ t('channel.tagEdit.advanced.title') }}</span>
            <span class="edit-tag-dialog__section-subtitle">{{ t('channel.tagEdit.advanced.subtitle') }}</span>
          </div>
        </div>
        <ElFormItem :label="t('channel.tagEdit.advanced.paramOverride')">
          <ElInput
            v-model="form.param_override"
            type="textarea"
            :autosize="{ minRows: 4, maxRows: 15 }"
            :placeholder="paramOverridePlaceholder"
          />
          <div class="edit-tag-dialog__quick-actions">
            <ElButton
              text
              size="small"
              type="primary"
              @click="form.param_override = PARAM_OVERRIDE_OLD_TEMPLATE"
            >
              {{ t('channel.tagEdit.advanced.oldFormatTemplate') }}
            </ElButton>
            <ElButton
              text
              size="small"
              type="primary"
              @click="form.param_override = PARAM_OVERRIDE_NEW_TEMPLATE"
            >
              {{ t('channel.tagEdit.advanced.newFormatTemplate') }}
            </ElButton>
            <ElButton
              text
              size="small"
              type="primary"
              @click="form.param_override = null"
            >
              {{ t('channel.tagEdit.modelConfig.noChange') }}
            </ElButton>
          </div>
        </ElFormItem>
        <ElFormItem :label="t('channel.tagEdit.advanced.headerOverride')">
          <ElInput
            v-model="form.header_override"
            type="textarea"
            :autosize="{ minRows: 4, maxRows: 12 }"
            :placeholder="headerOverridePlaceholder"
          />
          <div class="edit-tag-dialog__quick-actions">
            <ElButton
              text
              size="small"
              type="primary"
              @click="form.header_override = HEADER_OVERRIDE_TEMPLATE"
            >
              {{ t('channel.tagEdit.advanced.fillTemplate') }}
            </ElButton>
            <ElButton
              text
              size="small"
              type="primary"
              @click="form.header_override = null"
            >
              {{ t('channel.tagEdit.modelConfig.noChange') }}
            </ElButton>
          </div>
          <div class="edit-tag-dialog__vars-hint">
            <span class="edit-tag-dialog__vars-label">{{ t('channel.tagEdit.advanced.supportedVars') }}</span>
            <span class="edit-tag-dialog__vars-item">{{ t('channel.tagEdit.advanced.apiKeyVar') }}</span>
          </div>
        </ElFormItem>
      </div>

      <div class="edit-tag-dialog__section">
        <div class="edit-tag-dialog__section-header">
          <i class="i-ep-user" />
          <div class="edit-tag-dialog__section-title">
            <span class="edit-tag-dialog__section-name">{{ t('channel.tagEdit.groups.title') }}</span>
            <span class="edit-tag-dialog__section-subtitle">{{ t('channel.tagEdit.groups.subtitle') }}</span>
          </div>
        </div>
        <ElFormItem :label="t('channel.tagEdit.groups.groups')">
          <ElSelect
            v-model="form.groups"
            multiple
            filterable
            allow-create
            :placeholder="t('channel.tagEdit.groups.groupsPlaceholder')"
            style="width: 100%"
          >
            <ElOption
              v-for="opt in groupOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
        </ElFormItem>
      </div>
    </ElForm>

    <template #footer>
      <div class="edit-tag-dialog__footer">
        <ElButton @click="handleCancel">
          {{ t('channel.tagEdit.actions.cancel') }}
        </ElButton>
        <ElButton
          type="primary"
          :loading="loading"
          :disabled="initLoading"
          @click="handleSubmit"
        >
          {{ t('channel.tagEdit.actions.save') }}
        </ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
.edit-tag-dialog__header {
  display: flex;
  gap: var(--ys-spacing-2);
  align-items: center;
}

.edit-tag-dialog__title {
  font-size: var(--ys-font-size-lg);
  font-weight: 600;
}

.edit-tag-dialog__loading {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 300px;
}

.edit-tag-dialog__form {
  max-height: 60vh;
  padding-right: 4px;
  overflow-y: auto;
}

.edit-tag-dialog__alert {
  margin-bottom: 16px;
}

.edit-tag-dialog__section {
  padding: var(--ys-spacing-4);
  margin-bottom: 16px;
  background-color: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--ys-radius-lg);
}

.edit-tag-dialog__section-header {
  display: flex;
  gap: var(--ys-spacing-2);
  align-items: center;
  margin-bottom: 16px;
}

.edit-tag-dialog__section-header i {
  font-size: 18px;
  color: var(--el-color-primary);
}

.edit-tag-dialog__section-title {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.edit-tag-dialog__section-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.edit-tag-dialog__section-subtitle {
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
}

.edit-tag-dialog__custom-model {
  display: flex;
  gap: var(--ys-spacing-2);
  width: 100%;
}

.edit-tag-dialog__custom-model .el-input {
  flex: 1;
}

.edit-tag-dialog__quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ys-spacing-1);
  margin-top: 4px;
}

.edit-tag-dialog__vars-hint {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ys-spacing-2);
  margin-top: 6px;
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
}

.edit-tag-dialog__vars-label {
  font-weight: 500;
}

.edit-tag-dialog__footer {
  display: flex;
  gap: var(--ys-spacing-2);
  justify-content: flex-end;
}
</style>
