<script setup lang="ts">
/**
 * 供应商创建/编辑对话框（T-MO-03 供应商 Tab 内快捷编辑/删除）。
 */
import { reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ElDialog, ElForm, ElFormItem, ElInput, ElSelect, ElOption, ElButton } from 'element-plus'
import { createVendor, deleteVendor, updateVendor, getVendors } from '@/api/model'
import type { Vendor, VendorFormData } from '@/api/model/types'

const { t } = useI18n()

const props = defineProps<{
  visible: boolean
  editingVendor?: Vendor | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'success'): void
}>()

const submitting = ref(false)
const formData = reactive<VendorFormData>({
  name: '',
  description: '',
  icon: '',
  status: 1,
})

const vendorList = ref<Vendor[]>([])

watch(
  () => props.visible,
  async (val) => {
    if (val) {
      if (props.editingVendor) {
        Object.assign(formData, {
          id: props.editingVendor.id,
          name: props.editingVendor.name,
          description: props.editingVendor.description || '',
          icon: props.editingVendor.icon || '',
          status: props.editingVendor.status,
        })
      } else {
        Object.assign(formData, { name: '', description: '', icon: '', status: 1 })
      }
      await loadVendors()
    }
  }
)

async function loadVendors(): Promise<void> {
  try {
    const data = await getVendors({ pageSize: 1000 })
    vendorList.value = data.list
  } catch {
    // 非阻塞
  }
}

async function handleSubmit(): Promise<void> {
  if (!formData.name.trim()) {
    ElMessage.warning(t('model.dialog.vendor.nameRequired'))
    return
  }
  submitting.value = true
  try {
    if (formData.id) {
      await updateVendor({ ...formData, id: formData.id! })
      ElMessage.success(t('model.dialog.vendor.updated'))
    } else {
      await createVendor(formData)
      ElMessage.success(t('model.dialog.vendor.created'))
    }
    emit('success')
    emit('update:visible', false)
  } catch {
    ElMessage.error(t('model.dialog.vendor.failed'))
  } finally {
    submitting.value = false
  }
}

async function handleDeleteVendor(vendor: Vendor): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('model.dialog.vendor.deleteConfirm', { name: vendor.name }),
      t('model.dialog.vendor.warning'),
      { type: 'warning' }
    )
    await deleteVendor(vendor.id)
    ElMessage.success(t('model.dialog.vendor.deleted'))
    await loadVendors()
    emit('success')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('model.dialog.vendor.failed'))
  }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="editingVendor ? t('model.dialog.vendor.editTitle') : t('model.dialog.vendor.manageTitle')"
    width="600px"
    @update:model-value="emit('update:visible', $event)"
  >
    <!-- 供应商表单 -->
    <el-form
      :model="formData"
      label-width="100px"
    >
      <el-form-item :label="t('model.dialog.vendor.name')">
        <el-input
          v-model="formData.name"
          :placeholder="t('model.dialog.vendor.namePlaceholder')"
        />
      </el-form-item>
      <el-form-item :label="t('model.dialog.vendor.description')">
        <el-input
          v-model="formData.description"
          type="textarea"
          :rows="2"
        />
      </el-form-item>
      <el-form-item :label="t('model.dialog.vendor.icon')">
        <el-input
          v-model="formData.icon"
          :placeholder="t('model.dialog.vendor.iconPlaceholder')"
        />
      </el-form-item>
      <el-form-item :label="t('model.dialog.vendor.status')">
        <el-select
          v-model="formData.status"
          style="width: 100%"
        >
          <el-option
            :label="t('model.dialog.vendor.enabled')"
            :value="1"
          />
          <el-option
            :label="t('model.dialog.vendor.disabled')"
            :value="0"
          />
        </el-select>
      </el-form-item>
    </el-form>

    <!-- 供应商列表（T-MO-03 快捷编辑/删除） -->
    <div
      v-if="vendorList.length"
      class="vendor-list"
    >
      <div class="vendor-list__title">
        {{ t('model.dialog.vendor.existingVendors') }}
      </div>
      <div
        v-for="v in vendorList"
        :key="v.id"
        class="vendor-list__item"
      >
        <span class="vendor-list__name">{{ v.name }}</span>
        <el-button
          text
          size="small"
          @click="Object.assign(formData, { id: v.id, name: v.name, description: v.description || '', icon: v.icon || '', status: v.status })"
        >
          {{ t('model.dialog.vendor.edit') }}
        </el-button>
        <el-button
          text
          size="small"
          type="danger"
          @click="handleDeleteVendor(v)"
        >
          {{ t('model.dialog.vendor.delete') }}
        </el-button>
      </div>
    </div>

    <template #footer>
      <div style="display: flex; gap: var(--ys-spacing-2); justify-content: flex-end">
        <el-button @click="emit('update:visible', false)">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="handleSubmit"
        >
          {{ formData.id ? t('model.dialog.vendor.save') : t('model.dialog.vendor.create') }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.vendor-list {
  padding-top: var(--ys-spacing-3);
  margin-top: var(--ys-spacing-4);
  border-top: 1px solid var(--el-border-color-lighter);
}

.vendor-list__title {
  margin-bottom: var(--ys-spacing-2);
  font-size: var(--ys-font-size-base);
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.vendor-list__item {
  display: flex;
  gap: var(--ys-spacing-2);
  align-items: center;
  padding: var(--ys-spacing-1) 0;
}

.vendor-list__name {
  flex: 1;
  font-size: var(--ys-font-size-sm);
}
</style>
