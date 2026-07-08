<script setup lang="ts">
/**
 * 模型编辑抽屉容器。
 *
 * 职责：创建/编辑模型表单（名称/描述/图标/标签/供应商/端点/匹配规则/状态/同步）。
 * 不负责：列表数据（useModelsData）/ 操作确认（useModelActions）。
 */
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElDrawer, ElForm, ElFormItem, ElInput, ElSelect, ElOption, ElSwitch, ElButton, ElTag } from 'element-plus'
import { useModelMutateForm } from '@/composables/model/useModelMutateForm'
import { NAME_RULE_OPTIONS } from '@/api/model/constants'

const { t } = useI18n()

const emit = defineEmits<{
  (e: 'success'): void
}>()

const {
  visible,
  loading,
  submitting,
  mode,
  formData,
  errors,
  vendors,
  initCreate,
  initUpdate,
  submit,
} = useModelMutateForm()

const tagInput = ref('')

function addTag(): void {
  const tag = tagInput.value.trim()
  if (tag && !formData.tags.includes(tag)) {
    formData.tags.push(tag)
  }
  tagInput.value = ''
}

function removeTag(tag: string): void {
  const idx = formData.tags.indexOf(tag)
  if (idx >= 0) formData.tags.splice(idx, 1)
}

async function handleSubmit(): Promise<void> {
  const success = await submit()
  if (success) emit('success')
}

defineExpose({ initCreate, initUpdate })
</script>

<template>
  <el-drawer
    v-model="visible"
    :title="mode === 'create' ? t('model.form.addTitle') : t('model.form.editTitle')"
    size="500px"
    :close-on-click-modal="false"
  >
    <el-form
      v-loading="loading"
      :model="formData"
      label-width="120px"
      label-position="right"
    >
      <!-- 模型名称 -->
      <el-form-item
        :label="t('model.form.modelName')"
        :error="errors.modelName"
      >
        <el-input
          v-model="formData.modelName"
          :placeholder="t('model.form.modelNamePlaceholder')"
          clearable
        />
      </el-form-item>

      <!-- 描述 -->
      <el-form-item :label="t('model.form.description')">
        <el-input
          v-model="formData.description"
          type="textarea"
          :rows="3"
          :placeholder="t('model.form.descriptionPlaceholder')"
        />
      </el-form-item>

      <!-- 图标 -->
      <el-form-item :label="t('model.form.icon')">
        <el-input
          v-model="formData.icon"
          :placeholder="t('model.form.iconPlaceholder')"
          clearable
        />
      </el-form-item>

      <!-- 标签 -->
      <el-form-item :label="t('model.form.tags')">
        <div class="model-form__tags">
          <el-tag
            v-for="tag in formData.tags"
            :key="tag"
            closable
            size="small"
            style="margin-right: var(--ys-spacing-1); margin-bottom: 4px"
            @close="removeTag(tag)"
          >
            {{ tag }}
          </el-tag>
          <el-input
            v-model="tagInput"
            size="small"
            style="width: 120px"
            :placeholder="t('model.form.addTag')"
            @keyup.enter="addTag"
          />
        </div>
      </el-form-item>

      <!-- 供应商 -->
      <el-form-item :label="t('model.form.vendor')">
        <el-select
          v-model="formData.vendorId"
          :placeholder="t('model.form.selectVendor')"
          clearable
          filterable
          style="width: 100%"
        >
          <el-option
            v-for="vendor in vendors"
            :key="vendor.id"
            :label="vendor.name"
            :value="vendor.id"
          />
        </el-select>
      </el-form-item>

      <!-- 端点 -->
      <el-form-item
        :label="t('model.form.endpoints')"
        :error="errors.endpoints"
      >
        <el-input
          v-model="formData.endpoints"
          type="textarea"
          :rows="2"
          :placeholder="t('model.form.endpointsPlaceholder')"
        />
      </el-form-item>

      <!-- 匹配规则 -->
      <el-form-item :label="t('model.form.matchType')">
        <el-select
          v-model="formData.nameRule"
          style="width: 100%"
        >
          <el-option
            v-for="opt in NAME_RULE_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>

      <!-- 状态 -->
      <el-form-item :label="t('model.form.enabled')">
        <el-switch v-model="formData.status" />
      </el-form-item>

      <!-- 同步官方 -->
      <el-form-item :label="t('model.form.officialSync')">
        <el-switch v-model="formData.syncOfficial" />
      </el-form-item>
    </el-form>

    <template #footer>
      <div style="display: flex; gap: var(--ys-spacing-2); justify-content: flex-end">
        <el-button @click="visible = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="handleSubmit"
        >
          {{ mode === 'create' ? t('model.form.create') : t('model.form.save') }}
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
.model-form__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
  align-items: center;
}
</style>
