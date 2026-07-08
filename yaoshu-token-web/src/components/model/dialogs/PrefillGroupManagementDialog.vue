<script setup lang="ts">
/**
 * 预填组管理对话框（T-MO-04 批量添加到预填组）。
 *
 * 职责：预填组列表 CRUD + 批量添加模型到预填组。
 */
import { ref, watch } from 'vue'
import { ElDialog, ElTable, ElTableColumn, ElButton, ElTag, ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  getPrefillGroups,
  createPrefillGroup,
  updatePrefillGroup,
  deletePrefillGroup,
} from '@/api/model'
import type { PrefillGroup, PrefillGroupFormData } from '@/api/model/types'
import PrefillGroupFormDrawer from './PrefillGroupFormDrawer.vue'

const props = defineProps<{
  visible: boolean
  /** 需要批量添加到预填组的模型名列表（T-MO-04），空则仅管理 */
  selectedModels?: string[]
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
}>()

const { t } = useI18n()

const groups = ref<PrefillGroup[]>([])
const loading = ref(false)
const formDrawerOpen = ref(false)
const editingGroup = ref<PrefillGroup | null>(null)

watch(
  () => props.visible,
  (val) => {
    if (val) fetchGroups()
  }
)

async function fetchGroups(): Promise<void> {
  loading.value = true
  try {
    groups.value = await getPrefillGroups()
  } catch {
    ElMessage.error(t('model.dialog.prefillGroup.loadFailed'))
  } finally {
    loading.value = false
  }
}

function handleCreate(): void {
  editingGroup.value = null
  formDrawerOpen.value = true
}

function handleEdit(group: PrefillGroup): void {
  editingGroup.value = group
  formDrawerOpen.value = true
}

async function handleDelete(group: PrefillGroup): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('model.dialog.prefillGroup.deleteConfirm', { name: group.name }),
      t('common.warning'),
      { type: 'warning' }
    )
    await deletePrefillGroup(group.id)
    ElMessage.success(t('model.dialog.prefillGroup.deleteSuccess'))
    fetchGroups()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('model.dialog.prefillGroup.deleteFailed'))
  }
}

async function handleAddToGroup(group: PrefillGroup): Promise<void> {
  if (!props.selectedModels?.length) return
  try {
    const existingItems = Array.isArray(group.items) ? group.items : [group.items]
    const merged = Array.from(new Set([...existingItems, ...props.selectedModels]))
    await updatePrefillGroup({
      id: group.id,
      type: group.type,
      name: group.name,
      items: merged,
      description: group.description,
    })
    ElMessage.success(t('model.dialog.prefillGroup.addSuccess', { count: props.selectedModels.length, name: group.name }))
    emit('update:visible', false)
  } catch {
    ElMessage.error(t('model.dialog.prefillGroup.addFailed'))
  }
}

async function handleFormSubmit(data: PrefillGroupFormData): Promise<void> {
  try {
    if (editingGroup.value) {
      await updatePrefillGroup({ ...data, id: editingGroup.value.id })
      ElMessage.success(t('model.dialog.prefillGroup.updateSuccess'))
    } else {
      await createPrefillGroup(data)
      ElMessage.success(t('model.dialog.prefillGroup.createSuccess'))
    }
    formDrawerOpen.value = false
    fetchGroups()
  } catch {
    ElMessage.error(t('common.operationFailed'))
  }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="t('model.dialog.prefillGroup.managementTitle')"
    width="700px"
    @update:model-value="emit('update:visible', $event)"
  >
    <div style=" display: flex; justify-content: space-between;margin-bottom: var(--ys-spacing-3)">
      <span
        v-if="selectedModels?.length"
        style=" font-size: var(--ys-font-size-sm);color: var(--el-text-color-secondary)"
      >
        {{ t('model.dialog.prefillGroup.selectedHint', { count: selectedModels.length }) }}
      </span>
      <el-button
        size="small"
        type="primary"
        @click="handleCreate"
      >
        {{ t('model.dialog.prefillGroup.createGroup') }}
      </el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="groups"
      size="small"
      max-height="400"
    >
      <el-table-column
        prop="name"
        :label="t('model.dialog.prefillGroup.columnName')"
        min-width="120"
      />
      <el-table-column
        prop="type"
        :label="t('model.dialog.prefillGroup.columnType')"
        width="100"
      >
        <template #default="{ row }">
          <el-tag size="small">
            {{ row.type }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="description"
        :label="t('model.dialog.prefillGroup.columnDescription')"
        min-width="150"
      />
      <el-table-column
        :label="t('model.dialog.prefillGroup.columnActions')"
        width="200"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            v-if="selectedModels?.length"
            size="small"
            type="primary"
            text
            @click="handleAddToGroup(row as PrefillGroup)"
          >
            {{ t('model.dialog.prefillGroup.add') }}
          </el-button>
          <el-button
            size="small"
            text
            @click="handleEdit(row as PrefillGroup)"
          >
            {{ t('model.dialog.prefillGroup.edit') }}
          </el-button>
          <el-button
            size="small"
            text
            type="danger"
            @click="handleDelete(row as PrefillGroup)"
          >
            {{ t('model.dialog.prefillGroup.deleteBtn') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <PrefillGroupFormDrawer
      v-model:visible="formDrawerOpen"
      :group="editingGroup"
      @submit="handleFormSubmit"
    />
  </el-dialog>
</template>
