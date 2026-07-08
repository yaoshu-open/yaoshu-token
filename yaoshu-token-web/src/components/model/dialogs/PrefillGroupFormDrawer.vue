<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElDrawer, ElForm, ElFormItem, ElInput, ElSelect, ElOption, ElButton } from 'element-plus'
import type { PrefillGroup, PrefillGroupFormData } from '@/api/model/types'

const { t } = useI18n()

const props = defineProps<{
  visible: boolean
  group: PrefillGroup | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'submit', data: PrefillGroupFormData): void
}>()

const formData = ref<PrefillGroupFormData>({
  name: '',
  type: 'model',
  items: [],
  description: '',
})

// items 文本桥接（el-input v-model 只接受 string，formData.items 是 string | string[]）
const itemsText = computed({
  get: () => Array.isArray(formData.value.items) ? formData.value.items.join(', ') : formData.value.items,
  set: (val: string) => { formData.value.items = val },
})

watch(
  () => props.visible,
  (val) => {
    if (val) {
      if (props.group) {
        formData.value = {
          id: props.group.id,
          name: props.group.name,
          type: props.group.type,
          items: props.group.items,
          description: props.group.description || '',
        }
      } else {
        formData.value = { name: '', type: 'model', items: [], description: '' }
      }
    }
  }
)

function handleSubmit(): void {
  if (!formData.value.name.trim()) return
  emit('submit', { ...formData.value })
}
</script>

<template>
  <el-drawer
    :model-value="visible"
    :title="group ? t('model.dialog.prefillGroup.editTitle') : t('model.dialog.prefillGroup.createTitle')"
    size="400px"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-form
      :model="formData"
      label-width="100px"
    >
      <el-form-item :label="t('model.dialog.prefillGroup.name')">
        <el-input
          v-model="formData.name"
          :placeholder="t('model.dialog.prefillGroup.namePlaceholder')"
        />
      </el-form-item>
      <el-form-item :label="t('model.dialog.prefillGroup.type')">
        <el-select
          v-model="formData.type"
          style="width: 100%"
        >
          <el-option
            :label="t('model.dialog.prefillGroup.model')"
            value="model"
          />
          <el-option
            :label="t('model.dialog.prefillGroup.tag')"
            value="tag"
          />
          <el-option
            :label="t('model.dialog.prefillGroup.endpoint')"
            value="endpoint"
          />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('model.dialog.prefillGroup.items')">
        <el-input
          v-model="itemsText"
          type="textarea"
          :rows="4"
          :placeholder="t('model.dialog.prefillGroup.itemsPlaceholder')"
        />
      </el-form-item>
      <el-form-item :label="t('model.dialog.prefillGroup.description')">
        <el-input
          v-model="formData.description"
          :placeholder="t('model.dialog.prefillGroup.descriptionPlaceholder')"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <div style="display: flex; gap: var(--ys-spacing-2); justify-content: flex-end">
        <el-button @click="emit('update:visible', false)">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          @click="handleSubmit"
        >
          {{ group ? t('model.dialog.prefillGroup.save') : t('model.dialog.prefillGroup.create') }}
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>
