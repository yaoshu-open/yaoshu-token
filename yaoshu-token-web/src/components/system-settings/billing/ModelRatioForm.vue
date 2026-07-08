<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Search } from '@element-plus/icons-vue'
import { getEnabledModels } from '@/api/system-option'

type FilterMode = 'all' | 'unset' | 'conflict'

interface ModelRatioFormProps {
  modelRatio: string
  modelPrice: string
  completionRatio: string
  cacheRatio: string
  createCacheRatio?: string
  imageRatio?: string
  audioRatio?: string
  audioCompletionRatio?: string
}

const props = defineProps<ModelRatioFormProps>()
const emit = defineEmits<{
  (e: 'update', payload: {
    ModelRatio?: string
    ModelPrice?: string
    CompletionRatio?: string
    CacheRatio?: string
    CreateCacheRatio?: string
    ImageRatio?: string
    AudioRatio?: string
    AudioCompletionRatio?: string
  }): void
}>()

const { t } = useI18n()
const filterMode = ref<FilterMode>('all')
const enabledModels = ref<string[]>([])
const searchKeyword = ref('')
const editingModel = ref<string>('')
const editingRatio = ref<number>(0)
const editingPrice = ref<number>(0)
const editingCompletion = ref<number>(0)
const editingCache = ref<number>(0)
const editingCreateCache = ref<number>(0)
const editingImage = ref<number>(0)
const editingAudio = ref<number>(0)
const editingAudioCompletion = ref<number>(0)

interface ModelEntry {
  name: string
  ratio: number | null
  price: number | null
  completion: number | null
  cache: number | null
  isUnset: boolean
  isConflict: boolean
}

const parsedRatio = computed<Record<string, number>>(() => {
  try { return JSON.parse(props.modelRatio || '{}') } catch { return {} }
})
const parsedPrice = computed<Record<string, number>>(() => {
  try { return JSON.parse(props.modelPrice || '{}') } catch { return {} }
})
const parsedCompletion = computed<Record<string, number>>(() => {
  try { return JSON.parse(props.completionRatio || '{}') } catch { return {} }
})
const parsedCache = computed<Record<string, number>>(() => {
  try { return JSON.parse(props.cacheRatio || '{}') } catch { return {} }
})
const parsedCreateCache = computed<Record<string, number>>(() => {
  try { return JSON.parse(props.createCacheRatio || '{}') } catch { return {} }
})
const parsedImage = computed<Record<string, number>>(() => {
  try { return JSON.parse(props.imageRatio || '{}') } catch { return {} }
})
const parsedAudio = computed<Record<string, number>>(() => {
  try { return JSON.parse(props.audioRatio || '{}') } catch { return {} }
})
const parsedAudioCompletion = computed<Record<string, number>>(() => {
  try { return JSON.parse(props.audioCompletionRatio || '{}') } catch { return {} }
})

const allModels = computed<ModelEntry[]>(() => {
  const names = new Set<string>([
    ...Object.keys(parsedRatio.value),
    ...Object.keys(parsedPrice.value),
    ...enabledModels.value,
  ])
  return Array.from(names).map((name) => {
    const ratio = parsedRatio.value[name] ?? null
    const price = parsedPrice.value[name] ?? null
    const completion = parsedCompletion.value[name] ?? null
    const cache = parsedCache.value[name] ?? null
    return {
      name,
      ratio,
      price,
      completion,
      cache,
      isUnset: ratio == null && price == null,
      isConflict: ratio != null && price != null,
    }
  })
})

const filteredModels = computed<ModelEntry[]>(() => {
  let list = allModels.value
  if (filterMode.value === 'unset') {
    list = list.filter((m) => m.isUnset)
  } else if (filterMode.value === 'conflict') {
    list = list.filter((m) => m.isConflict)
  }
  if (searchKeyword.value.trim()) {
    const kw = searchKeyword.value.trim().toLowerCase()
    list = list.filter((m) => m.name.toLowerCase().includes(kw))
  }
  return list
})

function startEdit(entry: ModelEntry) {
  editingModel.value = entry.name
  editingRatio.value = entry.ratio ?? 0
  editingPrice.value = entry.price ?? 0
  editingCompletion.value = entry.completion ?? 1
  editingCache.value = entry.cache ?? 0
  editingCreateCache.value = parsedCreateCache.value[entry.name] ?? 0
  editingImage.value = parsedImage.value[entry.name] ?? 0
  editingAudio.value = parsedAudio.value[entry.name] ?? 0
  editingAudioCompletion.value = parsedAudioCompletion.value[entry.name] ?? 0
}

function saveEdit() {
  if (!editingModel.value) return
  const newRatio = { ...parsedRatio.value }
  const newPrice = { ...parsedPrice.value }
  const newCompletion = { ...parsedCompletion.value }
  const newCache = { ...parsedCache.value }
  const newCreateCache = { ...parsedCreateCache.value }
  const newImage = { ...parsedImage.value }
  const newAudio = { ...parsedAudio.value }
  const newAudioCompletion = { ...parsedAudioCompletion.value }
  if (editingRatio.value > 0) newRatio[editingModel.value] = editingRatio.value
  else delete newRatio[editingModel.value]
  if (editingPrice.value > 0) newPrice[editingModel.value] = editingPrice.value
  else delete newPrice[editingModel.value]
  if (editingCompletion.value !== 1) newCompletion[editingModel.value] = editingCompletion.value
  else delete newCompletion[editingModel.value]
  if (editingCache.value > 0) newCache[editingModel.value] = editingCache.value
  else delete newCache[editingModel.value]
  if (editingCreateCache.value > 0) newCreateCache[editingModel.value] = editingCreateCache.value
  else delete newCreateCache[editingModel.value]
  if (editingImage.value > 0) newImage[editingModel.value] = editingImage.value
  else delete newImage[editingModel.value]
  if (editingAudio.value > 0) newAudio[editingModel.value] = editingAudio.value
  else delete newAudio[editingModel.value]
  if (editingAudioCompletion.value > 0) newAudioCompletion[editingModel.value] = editingAudioCompletion.value
  else delete newAudioCompletion[editingModel.value]
  emit('update', {
    ModelRatio: JSON.stringify(newRatio),
    ModelPrice: JSON.stringify(newPrice),
    CompletionRatio: JSON.stringify(newCompletion),
    CacheRatio: JSON.stringify(newCache),
    CreateCacheRatio: JSON.stringify(newCreateCache),
    ImageRatio: JSON.stringify(newImage),
    AudioRatio: JSON.stringify(newAudio),
    AudioCompletionRatio: JSON.stringify(newAudioCompletion),
  })
  editingModel.value = ''
}

function deleteModel(name: string) {
  const newRatio = { ...parsedRatio.value }
  const newPrice = { ...parsedPrice.value }
  delete newRatio[name]
  delete newPrice[name]
  emit('update', {
    ModelRatio: JSON.stringify(newRatio),
    ModelPrice: JSON.stringify(newPrice),
  })
}

onMounted(async () => {
  try {
    const models = await getEnabledModels()
    enabledModels.value = models.map((m) => m.modelName)
  } catch {
    enabledModels.value = []
  }
})

watch(() => props.modelRatio, () => { /* reactivity trigger */ })
</script>

<template>
  <div class="model-ratio-form">
    <div class="model-ratio-form__header">
      <ElRadioGroup
        v-model="filterMode"
        size="small"
      >
        <ElRadioButton value="all">
          {{ t('systemSettings.billing.filterAll') }}
        </ElRadioButton>
        <ElRadioButton value="unset">
          {{ t('systemSettings.billing.filterUnset') }}
        </ElRadioButton>
        <ElRadioButton value="conflict">
          {{ t('systemSettings.billing.filterConflict') }}
        </ElRadioButton>
      </ElRadioGroup>
      <ElInput
        v-model="searchKeyword"
        :placeholder="t('systemSettings.billing.searchModel')"
        :prefix-icon="Search"
        clearable
        size="small"
        style="width: 220px"
      />
    </div>

    <ElEmpty
      v-if="filteredModels.length === 0"
      :description="filterMode === 'unset'
        ? t('systemSettings.billing.noUnsetModels')
        : t('systemSettings.billing.noModels')"
      :image-size="60"
    />

    <ElTable
      v-else
      :data="filteredModels"
      size="small"
      max-height="400"
      style="width: 100%"
    >
      <ElTableColumn
        :label="t('systemSettings.billing.modelName')"
        prop="name"
        min-width="180"
      />
      <ElTableColumn
        :label="t('systemSettings.billing.ratio')"
        width="100"
      >
        <template #default="{ row }">
          <span
            v-if="row.ratio != null"
            class="model-ratio-form__mono"
          >{{ row.ratio }}</span>
          <ElTag
            v-else
            type="warning"
            size="small"
          >
            {{ t('systemSettings.billing.notSet') }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn
        :label="t('systemSettings.billing.price')"
        width="100"
      >
        <template #default="{ row }">
          <span
            v-if="row.price != null"
            class="model-ratio-form__mono"
          >{{ row.price }}</span>
          <span
            v-else
            class="model-ratio-form__muted"
          >-</span>
        </template>
      </ElTableColumn>
      <ElTableColumn
        :label="t('systemSettings.billing.completion')"
        width="100"
      >
        <template #default="{ row }">
          <span
            v-if="row.completion != null"
            class="model-ratio-form__mono"
          >{{ row.completion }}</span>
          <span
            v-else
            class="model-ratio-form__muted"
          >-</span>
        </template>
      </ElTableColumn>
      <ElTableColumn
        :label="t('common.actions')"
        width="120"
        fixed="right"
      >
        <template #default="{ row }">
          <ElButton
            size="small"
            text
            type="primary"
            @click="startEdit(row as ModelEntry)"
          >
            {{ t('common.edit') }}
          </ElButton>
          <ElButton
            size="small"
            text
            type="danger"
            @click="deleteModel(row.name)"
          >
            {{ t('common.delete') }}
          </ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <ElDialog
      :model-value="editingModel !== ''"
      :title="t('systemSettings.billing.editModel')"
      width="420px"
      @update:model-value="val => { if (!val) editingModel = '' }"
      @close="editingModel = ''"
    >
      <ElForm
        v-if="editingModel"
        label-width="120px"
      >
        <ElFormItem :label="t('systemSettings.billing.modelName')">
          <span class="model-ratio-form__mono">{{ editingModel }}</span>
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.ratio')">
          <ElInputNumber
            v-model="editingRatio"
            :min="0"
            :precision="4"
            :step="0.1"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.price')">
          <ElInputNumber
            v-model="editingPrice"
            :min="0"
            :precision="4"
            :step="0.01"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.completion')">
          <ElInputNumber
            v-model="editingCompletion"
            :min="0"
            :precision="4"
            :step="0.1"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.cache')">
          <ElInputNumber
            v-model="editingCache"
            :min="0"
            :precision="4"
            :step="0.1"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.createCache')">
          <ElInputNumber
            v-model="editingCreateCache"
            :min="0"
            :precision="4"
            :step="0.1"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.imageRatio')">
          <ElInputNumber
            v-model="editingImage"
            :min="0"
            :precision="4"
            :step="0.1"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.audioRatio')">
          <ElInputNumber
            v-model="editingAudio"
            :min="0"
            :precision="4"
            :step="0.1"
          />
        </ElFormItem>
        <ElFormItem :label="t('systemSettings.billing.audioCompletionRatio')">
          <ElInputNumber
            v-model="editingAudioCompletion"
            :min="0"
            :precision="4"
            :step="0.1"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="editingModel = ''">
          {{ t('common.cancel') }}
        </ElButton>
        <ElButton
          type="primary"
          @click="saveEdit"
        >
          {{ t('common.save') }}
        </ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped lang="scss">
.model-ratio-form {
  &__header {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-3);
    align-items: center;
    justify-content: space-between;
    margin-bottom: var(--ys-spacing-3);
  }

  &__mono {
    font-family: var(--el-font-family-mono, monospace);
    font-size: var(--ys-font-size-xs);
  }

  &__muted {
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-placeholder);
  }
}
</style>
