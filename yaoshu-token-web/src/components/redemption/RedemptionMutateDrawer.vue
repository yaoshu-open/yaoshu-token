<script setup lang="ts">
/**
 * 兑换码创建/编辑抽屉。
 * 创建模式：name + quota + count(1-100) + expiredTime
 * 编辑模式：name + quota + expiredTime（count 隐藏）
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElRadioGroup,
  ElRadio,
  ElDatePicker,
  ElButton,
} from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useRedemptionActions } from '@/composables/redemption/useRedemptionActions'
import {
  REDEMPTION_COUNT_MAX,
  REDEMPTION_COUNT_MIN,
  REDEMPTION_NAME_MAX_LENGTH,
} from '@/api/redemption/constants'
import type { RedemptionFormData } from '@/api/redemption/types'

const { t } = useI18n()

const props = defineProps<{
  visible: boolean
  editingId: number | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'success'): void
}>()

const { handleCreate, handleUpdate } = useRedemptionActions(async () => {
  emit('success')
})

const formRef = ref<FormInstance>()
const submitting = ref(false)
const isPermanent = ref(true)
const customExpiredTime = ref<number>(Math.floor(Date.now() / 1000) + 86400 * 30)

const formData = ref<RedemptionFormData>({
  name: '',
  quota: 500000,
  count: 1,
  expiredTime: 0,
})

const isCreateMode = computed(() => props.editingId == null)

const rules = computed<FormRules>(() => ({
  name: [
    { required: true, message: t('redemption.form.nameRequired'), trigger: 'blur' },
    {
      max: REDEMPTION_NAME_MAX_LENGTH,
      message: t('redemption.form.nameMaxLength', { max: REDEMPTION_NAME_MAX_LENGTH }),
      trigger: 'blur',
    },
  ],
  quota: [
    { required: true, message: t('redemption.form.quotaRequired'), trigger: 'blur' },
  ],
  count: [
    {
      required: true,
      type: 'number',
      min: REDEMPTION_COUNT_MIN,
      max: REDEMPTION_COUNT_MAX,
      message: t('redemption.form.countRange'),
      trigger: 'blur',
    },
  ],
}))

watch(
  () => props.visible,
  (val) => {
    if (val) {
      if (props.editingId != null) {
        // 编辑模式：由父组件通过 editingId 触发，此处仅重置表单结构
        // 实际数据由父组件传入或在此处 fetch（简化：编辑时仅允许改 name/quota/expiredTime）
        isPermanent.value = true
        customExpiredTime.value = Math.floor(Date.now() / 1000) + 86400 * 30
        formData.value = {
          id: props.editingId,
          name: '',
          quota: 500000,
          count: 1,
          expiredTime: 0,
        }
      } else {
        isPermanent.value = true
        customExpiredTime.value = Math.floor(Date.now() / 1000) + 86400 * 30
        formData.value = {
          name: '',
          quota: 500000,
          count: 1,
          expiredTime: 0,
        }
      }
    }
  }
)

function handlePermanentChange(val: string | number | boolean | undefined): void {
  const permanent = val === true
  isPermanent.value = permanent
  formData.value.expiredTime = permanent ? 0 : customExpiredTime.value
}

function handleCustomTimeChange(val: Date | string): void {
  const ts = typeof val === 'string' ? new Date(val).getTime() : val.getTime()
  customExpiredTime.value = Math.floor(ts / 1000)
  formData.value.expiredTime = customExpiredTime.value
}

async function handleSubmit(): Promise<void> {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    if (isPermanent.value) {
      formData.value.expiredTime = 0
    } else {
      formData.value.expiredTime = customExpiredTime.value
    }

    if (isCreateMode.value) {
      const ok = await handleCreate({
        name: formData.value.name,
        quota: formData.value.quota,
        count: formData.value.count,
        expiredTime: formData.value.expiredTime,
      })
      if (ok) {
        emit('update:visible', false)
      }
    } else if (formData.value.id) {
      const ok = await handleUpdate({
        id: formData.value.id,
        name: formData.value.name,
        quota: formData.value.quota,
        expiredTime: formData.value.expiredTime,
        statusOnly: false,
      })
      if (ok) {
        emit('update:visible', false)
      }
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-drawer
    :model-value="visible"
    :title="isCreateMode ? t('redemption.form.title.create') : t('redemption.form.title.edit')"
    size="480px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-width="120px"
      label-position="right"
    >
      <el-form-item
        :label="t('redemption.form.name')"
        prop="name"
      >
        <el-input
          v-model="formData.name"
          :placeholder="t('redemption.form.namePlaceholder')"
          :maxlength="REDEMPTION_NAME_MAX_LENGTH"
          show-word-limit
          clearable
        />
      </el-form-item>
      <el-form-item
        :label="t('redemption.form.quota')"
        prop="quota"
      >
        <el-input-number
          v-model="formData.quota"
          :min="1"
          :step="100000"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item
        v-if="isCreateMode"
        :label="t('redemption.form.count')"
        prop="count"
      >
        <el-input-number
          v-model="formData.count"
          :min="REDEMPTION_COUNT_MIN"
          :max="REDEMPTION_COUNT_MAX"
          style="width: 100%"
        />
        <div class="redemption-drawer__hint">
          {{ t('redemption.form.countHint') }}
        </div>
      </el-form-item>
      <el-form-item :label="t('redemption.form.expiredTime')">
        <el-radio-group
          :model-value="isPermanent"
          @change="handlePermanentChange"
        >
          <el-radio :value="true">
            {{ t('redemption.form.permanent') }}
          </el-radio>
          <el-radio :value="false">
            {{ t('redemption.form.customTime') }}
          </el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item
        v-if="!isPermanent"
        label=" "
      >
        <el-date-picker
          :model-value="customExpiredTime * 1000"
          type="datetime"
          :placeholder="t('redemption.form.pickTime')"
          style="width: 100%"
          @update:model-value="handleCustomTimeChange"
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
          :loading="submitting"
          @click="handleSubmit"
        >
          {{ isCreateMode ? t('common.create') : t('common.save') }}
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
.redemption-drawer__hint {
  margin-top: 4px;
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
}
</style>
