<script setup lang="ts">
/**
 * 用户额度调整对话框。
 * 含 T-US-01 额度原生输入切换（金额 ↔ 原生额度）。
 */
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElDialog, ElForm, ElFormItem, ElInputNumber, ElRadioGroup, ElRadioButton, ElButton, ElMessage, ElSwitch } from 'element-plus'
import { manageUserQuota } from '@/api/user'
import type { User } from '@/api/user/types'

const { t } = useI18n()

const props = defineProps<{
  visible: boolean
  user: User | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'success'): void
}>()

const submitting = ref(false)
const action = ref<'add' | 'subtract' | 'override'>('add')
const quotaAmount = ref(0)

// T-US-01 额度原生输入切换
const useRawQuota = ref(false)
const rawQuota = ref(0)

// 金额 ↔ 原生额度转换（500000 原生 = $1）
const QUOTA_PER_DOLLAR = 500000

const displayQuota = computed({
  get: () => useRawQuota.value ? rawQuota.value : quotaAmount.value,
  set: (val: number) => {
    if (useRawQuota.value) {
      rawQuota.value = val
      quotaAmount.value = Math.floor(val / QUOTA_PER_DOLLAR)
    } else {
      quotaAmount.value = val
      rawQuota.value = val * QUOTA_PER_DOLLAR
    }
  },
})

watch(
  () => props.visible,
  (val) => {
    if (val) {
      action.value = 'add'
      quotaAmount.value = 0
      rawQuota.value = 0
      useRawQuota.value = false
    }
  }
)

async function handleSubmit(): Promise<void> {
  if (!props.user) return
  submitting.value = true
  try {
    const quota = useRawQuota.value ? rawQuota.value : quotaAmount.value * QUOTA_PER_DOLLAR
    await manageUserQuota({
      userId: props.user.id,
      quota,
      action: action.value,
    })
    ElMessage.success(t('user.quota.adjusted'))
    emit('success')
    emit('update:visible', false)
  } catch {
    ElMessage.error(t('user.quota.failed'))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="t('user.quota.title')"
    width="450px"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-form label-width="120px">
      <el-form-item :label="t('user.quota.user')">
        <el-input
          :model-value="user?.username ?? ''"
          disabled
        />
      </el-form-item>
      <el-form-item :label="t('user.quota.currentQuota')">
        <el-input
          :model-value="user?.quota?.toString() ?? '0'"
          disabled
        />
      </el-form-item>
      <el-form-item :label="t('user.quota.action')">
        <el-radio-group v-model="action">
          <el-radio-button value="add">
            {{ t('user.quota.add') }}
          </el-radio-button>
          <el-radio-button value="subtract">
            {{ t('user.quota.subtract') }}
          </el-radio-button>
          <el-radio-button value="override">
            {{ t('user.quota.override') }}
          </el-radio-button>
        </el-radio-group>
      </el-form-item>
      <!-- T-US-01 额度原生输入切换 -->
      <el-form-item :label="t('user.quota.rawInput')">
        <el-switch v-model="useRawQuota" />
        <span style="margin-left: 8px; font-size: var(--ys-font-size-xs); color: var(--el-text-color-secondary)">
          {{ useRawQuota ? t('user.quota.nativeQuota') : t('user.quota.amount') }}
        </span>
      </el-form-item>
      <el-form-item :label="useRawQuota ? t('user.quota.quotaRaw') : t('user.quota.amount')">
        <el-input-number
          v-model="displayQuota"
          :min="0"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item
        v-if="useRawQuota"
        :label="t('user.quota.preview')"
      >
        <span style="font-size: var(--ys-font-size-sm); color: var(--el-text-color-secondary)">
          ≈ ${{ (rawQuota / QUOTA_PER_DOLLAR).toFixed(2) }}
        </span>
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
          {{ t('common.confirm') }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>
