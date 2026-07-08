<script setup lang="ts">
/**
 * 渠道模型配置分区：模型列表 / 分组 / 模型映射 / 获取上游模型。
 */
import { computed, ref, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElButton, ElFormItem, ElInput, ElMessage, ElOption, ElSelect } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import { getAllModels, getGroups, fetchModels } from '@/api/channel'
import { MODEL_FETCHABLE_TYPES } from '@/api/channel/constants'
import { useChannelMutateFormContext } from '@/composables/channel/useChannelMutateForm'
import { parseModelsString, formatModelsArray } from '@/lib/channel/channel-form'
import FetchModelsDialog from '../dialogs/FetchModelsDialog.vue'

const { t } = useI18n()
const { form, errors } = useChannelMutateFormContext()

// ============================================================================
// 选项数据
// ============================================================================

const modelOptions = ref<string[]>([])
const groupOptions = ref<string[]>([])
const customModel = ref('')

// 模型多选值（string ↔ string[] 转换）
const modelsArray = computed<string[]>({
  get: () => parseModelsString(form.models),
  set: (val: string[]) => {
    form.models = formatModelsArray(val)
  }
})

// 分组多选值（直接使用 form.group）
const groupsArray = computed<string[]>({
  get: () => form.group,
  set: (val: string[]) => {
    form.group = val
  }
})

const canFetchModels = computed(() => MODEL_FETCHABLE_TYPES.has(form.type))

// ============================================================================
// 获取上游模型
// ============================================================================

const fetchDialogVisible = ref(false)
const fetchLoading = ref(false)
const fetchedModels = ref<string[]>([])

async function handleFetchModels(): Promise<void> {
  fetchLoading.value = true
  try {
    const res = await fetchModels({
      baseUrl: form.base_url,
      type: form.type,
      key: form.key
    })
    fetchedModels.value = res
    fetchDialogVisible.value = true
  } catch {
    ElMessage.error(t('channel.edit.models.fetchFailed'))
  } finally {
    fetchLoading.value = false
  }
}

function handleFetchDialogConfirm(selectedModels: string[]): void {
  const existing = new Set(parseModelsString(form.models))
  const toAdd = selectedModels.filter((m) => !existing.has(m))
  if (toAdd.length > 0) {
    const merged = [...parseModelsString(form.models), ...toAdd]
    form.models = formatModelsArray(merged)
    ElMessage.success(t('channel.edit.models.fetchAdded', { count: toAdd.length }))
  } else {
    ElMessage.info(t('channel.edit.models.fetchNoNew'))
  }
  fetchDialogVisible.value = false
}

// ============================================================================
// 自定义模型添加
// ============================================================================

function addCustomModel(): void {
  const model = customModel.value.trim()
  if (!model) return
  const existing = parseModelsString(form.models)
  if (!existing.includes(model)) {
    existing.push(model)
    form.models = formatModelsArray(existing)
    if (!modelOptions.value.includes(model)) {
      modelOptions.value.push(model)
    }
  }
  customModel.value = ''
}

// ============================================================================
// 模型映射模板
// ============================================================================

const MODEL_MAPPING_TEMPLATE = JSON.stringify({ 'gpt-3.5-turbo': 'gpt-3.5-turbo-0125' }, null, 2)

function fillModelMappingTemplate(): void {
  form.model_mapping = MODEL_MAPPING_TEMPLATE
}

function clearModelMapping(): void {
  form.model_mapping = ''
}

// ============================================================================
// 初始化
// ============================================================================

onMounted(async () => {
  try {
    const [modelsRes, groupsRes] = await Promise.all([getAllModels(), getGroups()])
    // 拦截器已解包：modelsRes 直接是 ChannelModel[]
    if (modelsRes && modelsRes.length > 0) {
      modelOptions.value = modelsRes.map((m) => m.id)
    }
    // groupsRes 直接是 string[]
    if (groupsRes && groupsRes.length > 0) {
      groupOptions.value = groupsRes
    }
  } catch {
    // 静默失败，选项为空不影响手动输入
  }
})

// 当类型变化时，若新类型不可获取上游模型，关闭弹窗
watch(
  () => form.type,
  () => {
    if (!canFetchModels.value) {
      fetchDialogVisible.value = false
    }
  }
)

function errorText(key: string | undefined): string {
  return key ? t(key) : ''
}
</script>

<template>
  <div class="channel-section">
    <h3 class="channel-section__title">
      {{ t('channel.edit.models.title') }}
    </h3>
    <p class="channel-section__subtitle">
      {{ t('channel.edit.models.subtitle') }}
    </p>

    <!-- 模型列表 -->
    <el-form-item
      :label="t('channel.edit.models.models')"
      prop="models"
      :error="errorText(errors.models)"
      required
    >
      <el-select
        v-model="modelsArray"
        multiple
        filterable
        allow-create
        :placeholder="t('channel.edit.models.modelsPlaceholder')"
        class="w-full"
      >
        <el-option
          v-for="model in modelOptions"
          :key="model"
          :label="model"
          :value="model"
        />
      </el-select>
      <div class="channel-section__hint">
        <span>{{ t('channel.edit.models.modelsHint') }}</span>
        <el-button
          v-if="canFetchModels"
          link
          type="primary"
          size="small"
          :loading="fetchLoading"
          :icon="Download"
          @click="handleFetchModels"
        >
          {{ t('channel.edit.models.fetchUpstream') }}
        </el-button>
      </div>
    </el-form-item>

    <!-- 自定义模型 -->
    <el-form-item
      :label="t('channel.edit.models.customModel')"
      prop="customModel"
    >
      <div class="channel-section__custom-model">
        <el-input
          v-model="customModel"
          :placeholder="t('channel.edit.models.customModelPlaceholder')"
          @keyup.enter="addCustomModel"
        />
        <el-button
          type="primary"
          plain
          @click="addCustomModel"
        >
          {{ t('channel.edit.models.addModel') }}
        </el-button>
      </div>
    </el-form-item>

    <!-- 分组 -->
    <el-form-item
      :label="t('channel.edit.models.group')"
      prop="group"
      :error="errorText(errors.group)"
      required
    >
      <el-select
        v-model="groupsArray"
        multiple
        filterable
        allow-create
        :placeholder="t('channel.edit.models.groupPlaceholder')"
        class="w-full"
      >
        <el-option
          v-for="grp in groupOptions"
          :key="grp"
          :label="grp"
          :value="grp"
        />
      </el-select>
    </el-form-item>

    <!-- 模型映射 -->
    <el-form-item
      :label="t('channel.edit.models.modelMapping')"
      prop="model_mapping"
      :error="errorText(errors.model_mapping)"
    >
      <el-input
        v-model="form.model_mapping"
        type="textarea"
        :autosize="{ minRows: 3, maxRows: 10 }"
        :placeholder="t('channel.edit.models.modelMappingPlaceholder')"
      />
      <div class="channel-section__hint">
        <span>{{ t('channel.edit.models.modelMappingHint') }}</span>
        <div class="channel-section__hint-actions">
          <el-button
            link
            type="primary"
            size="small"
            @click="fillModelMappingTemplate"
          >
            {{ t('channel.edit.models.fillTemplate') }}
          </el-button>
          <el-button
            link
            type="danger"
            size="small"
            @click="clearModelMapping"
          >
            {{ t('channel.edit.models.clearMapping') }}
          </el-button>
        </div>
      </div>
    </el-form-item>

    <!-- 获取上游模型弹窗 -->
    <FetchModelsDialog
      v-model="fetchDialogVisible"
      :models="fetchedModels"
      :current-models="modelsArray"
      @confirm="handleFetchDialogConfirm"
    />
  </div>
</template>

<style scoped>
.channel-section__title {
  margin: 0 0 var(--ys-spacing-1);
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.channel-section__subtitle {
  margin: 0 0 var(--ys-spacing-4);
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
}

.channel-section__hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  margin-top: 4px;
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
  white-space: pre-line;
}

.channel-section__hint-actions {
  display: flex;
  gap: var(--ys-spacing-2);
}

.channel-section__custom-model {
  display: flex;
  gap: var(--ys-spacing-2);
  width: 100%;
}

.w-full {
  width: 100%;
}
</style>
