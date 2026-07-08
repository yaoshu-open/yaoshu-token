<script setup lang="ts">
/**
 * 获取上游模型结果对话框。
 * 展示从上游获取的模型列表，用户勾选后添加到渠道模型列表。
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElButton, ElCheckbox, ElCheckboxGroup, ElDialog, ElInput, ElTag } from 'element-plus'

const props = defineProps<{
  modelValue: boolean
  models: string[]
  currentModels: string[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm', selectedModels: string[]): void
}>()

const { t } = useI18n()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const selected = ref<string[]>([])
const searchKeyword = ref('')

// 新模型（不在当前列表中的）
const newModels = computed(() => {
  const existing = new Set(props.currentModels)
  return props.models.filter((m) => !existing.has(m))
})

// 已存在的模型
const existingModels = computed(() => {
  const existing = new Set(props.currentModels)
  return props.models.filter((m) => existing.has(m))
})

const filteredNewModels = computed(() => {
  if (!searchKeyword.value.trim()) return newModels.value
  const kw = searchKeyword.value.toLowerCase()
  return newModels.value.filter((m) => m.toLowerCase().includes(kw))
})

// 打开时默认全选新模型
watch(visible, (v) => {
  if (v) {
    selected.value = [...newModels.value]
    searchKeyword.value = ''
  }
})

function handleConfirm(): void {
  emit('confirm', selected.value)
}

function selectAllNew(): void {
  selected.value = [...filteredNewModels.value]
}

function clearSelection(): void {
  selected.value = []
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="t('channel.edit.fetchDialog.title')"
    width="600px"
    append-to-body
  >
    <p class="fetch-dialog__desc">
      {{ t('channel.edit.fetchDialog.description') }}
    </p>

    <div
      v-if="newModels.length > 0"
      class="fetch-dialog__section"
    >
      <div class="fetch-dialog__section-header">
        <span class="fetch-dialog__section-title">
          {{ t('channel.edit.fetchDialog.newModels') }}
          <el-tag
            size="small"
            type="success"
          >{{ newModels.length }}</el-tag>
        </span>
        <div class="fetch-dialog__actions">
          <el-button
            link
            type="primary"
            size="small"
            @click="selectAllNew"
          >
            {{ t('channel.edit.fetchDialog.selectAll') }}
          </el-button>
          <el-button
            link
            type="info"
            size="small"
            @click="clearSelection"
          >
            {{ t('channel.edit.fetchDialog.clearSelection') }}
          </el-button>
        </div>
      </div>

      <el-input
        v-model="searchKeyword"
        :placeholder="t('channel.edit.fetchDialog.searchPlaceholder')"
        clearable
        size="small"
        style="margin-bottom: var(--ys-spacing-3)"
      />

      <el-checkbox-group
        v-model="selected"
        class="fetch-dialog__checkbox-group"
      >
        <el-checkbox
          v-for="model in filteredNewModels"
          :key="model"
          :label="model"
          :value="model"
          class="fetch-dialog__checkbox"
        >
          {{ model }}
        </el-checkbox>
      </el-checkbox-group>
    </div>

    <div
      v-if="existingModels.length > 0"
      class="fetch-dialog__section"
    >
      <span class="fetch-dialog__section-title">
        {{ t('channel.edit.fetchDialog.existingModels') }}
        <el-tag
          size="small"
          type="info"
        >{{ existingModels.length }}</el-tag>
      </span>
      <div class="fetch-dialog__existing-tags">
        <el-tag
          v-for="model in existingModels"
          :key="model"
          size="small"
          type="info"
          class="fetch-dialog__tag"
        >
          {{ model }}
        </el-tag>
      </div>
    </div>

    <div
      v-if="props.models.length === 0"
      class="fetch-dialog__empty"
    >
      {{ t('channel.edit.fetchDialog.empty') }}
    </div>

    <template #footer>
      <el-button @click="visible = false">
        {{ t('channel.edit.fetchDialog.cancel') }}
      </el-button>
      <el-button
        type="primary"
        :disabled="selected.length === 0"
        @click="handleConfirm"
      >
        {{ t('channel.edit.fetchDialog.confirm', { count: selected.length }) }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.fetch-dialog__desc {
  margin: 0 0 var(--ys-spacing-4);
  font-size: var(--ys-font-size-sm);
  color: var(--el-text-color-secondary);
}

.fetch-dialog__section {
  margin-bottom: var(--ys-spacing-5);
}

.fetch-dialog__section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--ys-spacing-3);
}

.fetch-dialog__section-title {
  display: flex;
  gap: var(--ys-spacing-2);
  align-items: center;
  font-size: var(--ys-font-size-base);
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.fetch-dialog__actions {
  display: flex;
  gap: var(--ys-spacing-2);
}

.fetch-dialog__checkbox-group {
  display: flex;
  flex-direction: column;
  max-height: 280px;
  padding: var(--ys-spacing-2);
  overflow-y: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--ys-radius-sm);
}

.fetch-dialog__checkbox {
  padding: var(--ys-spacing-1) 0;
  margin-right: 0;
}

.fetch-dialog__existing-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: var(--ys-spacing-2);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--ys-radius-sm);
}

.fetch-dialog__empty {
  padding: var(--ys-spacing-10) 0;
  font-size: var(--ys-font-size-base);
  color: var(--el-text-color-secondary);
  text-align: center;
}
</style>
