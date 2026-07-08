<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElDrawer, ElForm, ElFormItem, ElInput, ElInputNumber, ElSwitch, ElButton, ElDatePicker, ElMessage } from 'element-plus'
import { createToken, getToken, updateToken } from '@/api/token'
import { isFeatureHidden } from '@/plugins/spi/registry'
import { useSystemConfig } from '@/composables/useSystemConfig'
import { getCurrencyDisplay } from '@/utils/currency'
import type { TokenFormData } from '@/api/token/types'

const { t } = useI18n()

// PD-03：商业版无倍率概念，隐藏 group 相关字段（分组 + 跨分组重试）
const groupHidden = isFeatureHidden('group-ratio')

// 货币显示：统一使用系统配置的显示货币（与表格 formatQuotaWithCurrency 一致）
// 内部额度 quota = USD金额 × quotaPerUnit；显示值 = USD金额 × 汇率
const { currency } = useSystemConfig()
const quotaPerUnit = computed(() => currency.value.quotaPerUnit)
const currencyDisplay = computed(() => getCurrencyDisplay())
// 显示货币符号（¥ / $ / 自定义符号 / 无 tokens 模式返回 Tokens）
const currencySymbol = computed(() => {
  const meta = currencyDisplay.value.meta
  if (meta.kind === 'tokens') return 'Tokens'
  return meta.symbol
})

const props = defineProps<{
  visible: boolean
  editingId: number | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'success'): void
}>()

const loading = ref(false)
const submitting = ref(false)
const formData = ref<TokenFormData>({
  name: '',
  remainQuota: 500000,
  expiredTime: -1,
  unlimitedQuota: false,
  modelLimitsEnabled: false,
  modelLimits: '',
  allowIps: '',
  group: 'default',
  crossGroupRetry: false,
})

// 显示货币金额（双向换算：原生 quota → USD → 显示货币；提交时反向换算回原生 quota）
const remainQuotaDisplay = computed({
  get: () => {
    const meta = currencyDisplay.value.meta
    const amountUSD = formData.value.remainQuota / quotaPerUnit.value
    if (meta.kind === 'tokens') {
      return amountUSD * quotaPerUnit.value
    }
    return amountUSD * meta.exchangeRate
  },
  set: (val: number) => {
    const meta = currencyDisplay.value.meta
    let amountUSD: number
    if (meta.kind === 'tokens') {
      amountUSD = val / quotaPerUnit.value
    } else {
      amountUSD = val / meta.exchangeRate
    }
    formData.value.remainQuota = Math.floor(amountUSD * quotaPerUnit.value)
  },
})

watch(
  () => props.visible,
  async (val) => {
    if (val) {
      if (props.editingId != null) {
        loading.value = true
        try {
          const token = await getToken(props.editingId)
          Object.assign(formData.value, {
            id: token.id,
            name: token.name,
            remainQuota: token.remainQuota,
            expiredTime: token.expiredTime,
            unlimitedQuota: token.unlimitedQuota,
            modelLimitsEnabled: token.modelLimitsEnabled,
            modelLimits: token.modelLimits || '',
            allowIps: token.allowIps || '',
            group: token.group || 'default',
            crossGroupRetry: token.crossGroupRetry,
          })
        } catch {
          ElMessage.error(t('token.form.loadFailed'))
        } finally {
          loading.value = false
        }
      } else {
        formData.value = {
          name: '', remainQuota: 500000, expiredTime: -1,
          unlimitedQuota: false, modelLimitsEnabled: false,
          modelLimits: '', allowIps: '', group: 'default', crossGroupRetry: false,
        }
      }
    }
  }
)

async function handleSubmit(): Promise<void> {
  if (!formData.value.name.trim()) {
    ElMessage.warning(t('token.form.nameRequired'))
    return
  }
  submitting.value = true
  try {
    if (formData.value.id) {
      await updateToken({ ...formData.value, id: formData.value.id })
      ElMessage.success(t('token.actions.editSuccess'))
    } else {
      await createToken(formData.value)
      ElMessage.success(t('token.actions.createSuccess'))
    }
    emit('success')
    emit('update:visible', false)
  } catch {
    ElMessage.error(t('token.form.operationFailed'))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-drawer
    :model-value="visible"
    :title="editingId ? t('token.form.editTitle') : t('token.form.addTitle')"
    size="500px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-form
      v-loading="loading"
      :model="formData"
      label-width="140px"
      label-position="right"
    >
      <el-form-item :label="t('token.form.name')">
        <el-input
          v-model="formData.name"
          :placeholder="t('token.form.namePlaceholder')"
          clearable
        />
      </el-form-item>
      <el-form-item
        v-if="!groupHidden"
        :label="t('token.form.group')"
      >
        <el-input
          v-model="formData.group"
          placeholder="default"
        />
      </el-form-item>
      <el-form-item :label="t('token.form.unlimitedQuota')">
        <el-switch v-model="formData.unlimitedQuota" />
      </el-form-item>
      <el-form-item
        v-if="!formData.unlimitedQuota"
        :label="t('token.form.remainQuota')"
      >
        <el-input-number
          v-model="remainQuotaDisplay"
          :min="0"
          :precision="2"
          :step="1"
          style="width: 100%"
        />
        <span class="token-drawer__unit">{{ currencySymbol }}</span>
      </el-form-item>
      <el-form-item :label="t('token.form.expireTime')">
        <el-switch
          :model-value="formData.expiredTime !== -1"
          @change="(val: string | number | boolean) => (formData.expiredTime = val ? Math.floor(Date.now() / 1000) + 86400 * 30 : -1)"
        />
      </el-form-item>
      <el-form-item
        v-if="formData.expiredTime !== -1"
        :label="t('token.form.expireTime')"
      >
        <el-date-picker
          :model-value="formData.expiredTime * 1000"
          type="datetime"
          style="width: 100%"
          @update:model-value="(val: number) => formData.expiredTime = val ? Math.floor(val / 1000) : -1"
        />
      </el-form-item>
      <el-form-item :label="t('token.form.models')">
        <el-switch v-model="formData.modelLimitsEnabled" />
      </el-form-item>
      <el-form-item
        v-if="formData.modelLimitsEnabled"
        :label="t('token.form.models')"
      >
        <el-input
          v-model="formData.modelLimits"
          type="textarea"
          :rows="3"
          :placeholder="t('token.form.modelsPlaceholder')"
        />
      </el-form-item>
      <el-form-item :label="t('token.form.ips')">
        <el-input
          v-model="formData.allowIps"
          type="textarea"
          :rows="2"
          :placeholder="t('token.form.ipsPlaceholder')"
        />
      </el-form-item>
      <el-form-item
        v-if="!groupHidden"
        :label="t('token.form.crossGroupRetry')"
      >
        <el-switch v-model="formData.crossGroupRetry" />
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
          {{ editingId ? t('common.save') : t('common.create') }}
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
.token-drawer__unit {
  margin-left: 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}
</style>
