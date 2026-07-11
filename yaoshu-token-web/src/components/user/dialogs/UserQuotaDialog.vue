<script setup lang="ts">
/**
 * 用户额度调整对话框。
 * 含 T-US-01 额度原生输入切换（金额 ↔ 原生额度）。
 *
 * 金额模式：用户输入的是显示货币金额（CNY/USD/CUSTOM），提交时换算为 raw quota。
 * 原生模式：用户直接输入 raw quota 值。
 */
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElDialog, ElForm, ElFormItem, ElInputNumber, ElRadioGroup, ElRadioButton, ElButton, ElMessage, ElSwitch } from 'element-plus'
import { manageUserQuota } from '@/api/user'
import { useSystemConfig } from '@/composables/useSystemConfig'
import { formatCurrencyFromUSD, getCurrencyLabel } from '@/utils/currency'
import type { User } from '@/api/user/types'

const { t } = useI18n()
const { currency } = useSystemConfig()

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

// 金额模式：用户输入的是显示货币金额（如 CNY 金额）
const displayAmount = ref(0)

// T-US-01 额度原生输入切换
const useRawQuota = ref(false)
const rawQuota = ref(0)

// 货币配置
const quotaPerUnit = computed(() => currency.value.quotaPerUnit)
/** 显示货币 → USD 的汇率（CNY 模式下 usdExchangeRate=7，USD 模式下=1） */
const exchangeRateToUSD = computed(() => {
  const type = currency.value.quotaDisplayType
  switch (type) {
    case 'CNY': return currency.value.usdExchangeRate
    case 'CUSTOM': return currency.value.customCurrencyExchangeRate
    case 'USD':
    default: return 1
  }
})

/** 当前货币标签（如 CNY/USD） */
const currencyLabel = computed(() => getCurrencyLabel())

const displayQuota = computed({
  get: () => useRawQuota.value ? rawQuota.value : displayAmount.value,
  set: (val: number) => {
    if (useRawQuota.value) {
      rawQuota.value = val
    } else {
      displayAmount.value = val
    }
  },
})

watch(
  () => props.visible,
  (val) => {
    if (val) {
      action.value = 'add'
      displayAmount.value = 0
      rawQuota.value = 0
      useRawQuota.value = false
    }
  }
)

/** 预览：显示用户输入对应的显示货币金额 */
const previewDisplay = computed(() => {
  if (useRawQuota.value) {
    // 原生模式：raw quota → USD → 显示货币
    return formatCurrencyFromUSD(rawQuota.value / quotaPerUnit.value)
  } else {
    // 金额模式：用户输入的就是显示货币金额，直接显示
    const type = currency.value.quotaDisplayType
    if (type === 'TOKENS') {
      return formatCurrencyFromUSD(displayAmount.value / quotaPerUnit.value)
    }
    // CNY/USD/CUSTOM：输入的就是该货币金额
    const symbol = type === 'CNY' ? '¥' : type === 'CUSTOM' ? currency.value.customCurrencySymbol : '$'
    return `${symbol}${displayAmount.value}`
  }
})

async function handleSubmit(): Promise<void> {
  if (!props.user) return
  submitting.value = true
  try {
    // 计算提交的 raw quota 值
    let quota: number
    if (useRawQuota.value) {
      quota = rawQuota.value
    } else {
      // 金额模式：先把显示货币金额换算为 USD，再乘以 quotaPerUnit
      const amountUSD = displayAmount.value / exchangeRateToUSD.value
      quota = Math.floor(amountUSD * quotaPerUnit.value)
    }
    await manageUserQuota({
      id: props.user.id,
      action: 'add_quota',
      value: quota,
      mode: action.value,
    })
    ElMessage.success(t('user.quota.adjusted'))
    emit('success')
    emit('update:visible', false)
  } catch {
    // 错误由请求拦截器统一提示
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
        <span
          v-if="!useRawQuota"
          style="margin-left: 8px; font-size: var(--ys-font-size-xs); color: var(--el-text-color-secondary); white-space: nowrap"
        >
          ({{ currencyLabel }})
        </span>
      </el-form-item>
      <el-form-item :label="t('user.quota.preview')">
        <span style="font-size: var(--ys-font-size-sm); color: var(--el-text-color-secondary)">
          ≈ {{ previewDisplay }}
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
