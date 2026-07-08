<script setup lang="ts">
/**
 * 按标签批量编辑对话框。
 * 对同一标签下所有渠道执行覆盖式编辑：标签名/模型/模型映射/分组。
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElFormItem,
  ElInput,
  ElLoading,
  ElMessage,
  ElOption,
  ElSelect
} from 'element-plus'
import {
  editTagChannels,
  getAllModels,
  getGroups,
  getTagModels
} from '@/api/channel'

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

// ============================================================================
// 表单状态
// ============================================================================

const isLoading = ref(false)
const isSaving = ref(false)
const newTag = ref('')
const modelsText = ref('')
const modelMapping = ref('')
const groupsArr = ref<string[]>([])
const groupOptions = ref<string[]>([])
const modelOptions = ref<string[]>([])

// ============================================================================
// 数据加载
// ============================================================================

async function loadData(): Promise<void> {
  if (!props.tag) return

  const loading = ElLoading.service({
    lock: true,
    text: t('channel.dialog.tagBatch.loading')
  })

  try {
    const [tagModelsRes, allModelsRes, groupsRes] = await Promise.all([
      getTagModels(props.tag),
      getAllModels(),
      getGroups()
    ])

    if (typeof tagModelsRes === 'string') {
      modelsText.value = tagModelsRes
    }

    if (Array.isArray(allModelsRes)) {
      modelOptions.value = allModelsRes.map((m) => m.id || String(m))
    }

    if (Array.isArray(groupsRes)) {
      groupOptions.value = groupsRes
    }

    newTag.value = props.tag
  } catch (e) {
    ElMessage.error((e as Error)?.message || t('channel.dialog.tagBatch.loadFailed'))
  } finally {
    loading.close()
    isLoading.value = false
  }
}

watch(visible, (v) => {
  if (v && props.tag) {
    isLoading.value = true
    newTag.value = ''
    modelsText.value = ''
    modelMapping.value = ''
    groupsArr.value = []
    loadData()
  }
})

// ============================================================================
// 保存
// ============================================================================

const hasChanges = computed(() => {
  return (
    newTag.value !== props.tag ||
    modelsText.value.trim() !== '' ||
    modelMapping.value.trim() !== '' ||
    groupsArr.value.length > 0
  )
})

async function handleSave(): Promise<void> {
  if (!props.tag) return

  // 校验模型映射 JSON
  if (modelMapping.value.trim()) {
    try {
      JSON.parse(modelMapping.value)
    } catch {
      ElMessage.error(t('channel.dialog.tagBatch.invalidMapping'))
      return
    }
  }

  if (!hasChanges.value) {
    ElMessage.warning(t('channel.dialog.tagBatch.noChanges'))
    return
  }

  isSaving.value = true
  try {
    const params: Record<string, string | undefined> = { tag: props.tag }

    if (newTag.value !== props.tag) {
      params.newTag = newTag.value || undefined
    }
    if (modelsText.value.trim()) {
      params.models = modelsText.value
    }
    if (modelMapping.value.trim()) {
      params.modelMapping = modelMapping.value
    }
    if (groupsArr.value.length > 0) {
      params.groups = groupsArr.value.join(',')
    }

    await editTagChannels(params as unknown as Parameters<typeof editTagChannels>[0])
    ElMessage.success(t('channel.dialog.tagBatch.saveSuccess'))
    emit('success')
    visible.value = false
  } catch (e) {
    ElMessage.error((e as Error)?.message || t('channel.dialog.tagBatch.saveFailed'))
  } finally {
    isSaving.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="t('channel.dialog.tagBatch.title')"
    width="600px"
    append-to-body
  >
    <p class="tag-batch__desc">
      {{ t('channel.dialog.tagBatch.description') }}
      <strong>{{ tag }}</strong>
    </p>

    <el-alert
      :title="t('channel.dialog.tagBatch.overwriteWarning')"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />

    <div class="tag-batch__form">
      <el-form-item :label="t('channel.dialog.tagBatch.tagName')">
        <el-input
          v-model="newTag"
          :placeholder="t('channel.dialog.tagBatch.tagNamePlaceholder')"
          :disabled="isSaving"
        />
        <p class="tag-batch__hint">
          {{ t('channel.dialog.tagBatch.tagNameHint') }}
        </p>
      </el-form-item>

      <el-form-item :label="t('channel.dialog.tagBatch.models')">
        <el-input
          v-model="modelsText"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 5 }"
          :placeholder="t('channel.dialog.tagBatch.modelsPlaceholder')"
          :disabled="isSaving"
        />
        <p class="tag-batch__hint">
          {{ t('channel.dialog.tagBatch.modelsHint') }}
        </p>
      </el-form-item>

      <el-form-item :label="t('channel.dialog.tagBatch.modelMapping')">
        <el-input
          v-model="modelMapping"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 6 }"
          :placeholder="t('channel.dialog.tagBatch.modelMappingPlaceholder')"
          :disabled="isSaving"
        />
        <p class="tag-batch__hint">
          {{ t('channel.dialog.tagBatch.modelMappingHint') }}
        </p>
      </el-form-item>

      <el-form-item :label="t('channel.dialog.tagBatch.groups')">
        <el-select
          v-model="groupsArr"
          multiple
          filterable
          allow-create
          default-first-option
          :placeholder="t('channel.dialog.tagBatch.groupsPlaceholder')"
          :disabled="isSaving"
          style="width: 100%"
        >
          <el-option
            v-for="g in groupOptions"
            :key="g"
            :label="g"
            :value="g"
          />
        </el-select>
        <p class="tag-batch__hint">
          {{ t('channel.dialog.tagBatch.groupsHint') }}
        </p>
      </el-form-item>
    </div>

    <template #footer>
      <el-button
        :disabled="isSaving"
        @click="visible = false"
      >
        {{ t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        :loading="isSaving"
        @click="handleSave"
      >
        {{ isSaving
          ? t('channel.dialog.tagBatch.saving')
          : t('channel.dialog.tagBatch.save')
        }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.tag-batch__desc {
  margin: 0 0 var(--ys-spacing-4);
  font-size: var(--ys-font-size-sm);
  color: var(--el-text-color-secondary);
}

.tag-batch__desc strong {
  margin-left: 4px;
  color: var(--el-text-color-primary);
}

.tag-batch__form {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-2);
}

.tag-batch__hint {
  margin: var(--ys-spacing-1) 0 0;
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
}
</style>
