<script setup lang="ts">
/**
 * 支持 5 种支付渠道：余额 / Stripe / Creem / WaffoPancake / Epay。
 */
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  paySubscriptionBalance,
  paySubscriptionStripe,
  paySubscriptionCreem,
  paySubscriptionWaffoPancake,
  paySubscriptionEpay,
} from '@/api/subscription'
import { useSystemConfigStore } from '@/store/modules/system-config'
import type {
  SubscriptionPlan,
  SubscriptionPayRequest,
  SubscriptionPayResponse,
} from '@/api/subscription/types'
import {
  formatDuration,
  formatResetPeriod,
} from '@/utils/subscription-format'
import { formatQuotaWithCurrency } from '@/utils/currency'

interface Props {
  visible: boolean
  plan: SubscriptionPlan | null
  isUpgrade?: boolean
  enableStripe?: boolean
  enableCreem?: boolean
  enableWaffoPancake?: boolean
  enableOnlineTopUp?: boolean
  userQuota?: number
  purchaseLimit?: number
  purchaseCount?: number
}

const props = withDefaults(defineProps<Props>(), {
  isUpgrade: false,
  enableStripe: false,
  enableCreem: false,
  enableWaffoPancake: false,
  enableOnlineTopUp: false,
  userQuota: 0,
  purchaseLimit: 0,
  purchaseCount: 0,
})

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'purchase-success': []
}>()

const { t } = useI18n()
const systemConfigStore = useSystemConfigStore()
const { currency } = storeToRefs(systemConfigStore)

// quotaPerUnit 从系统货币配置获取（与全项目 currency.ts 体系一致，
// 全项目唯一来源 useSystemConfigStore.currency.quotaPerUnit）
const quotaPerUnit = computed(() => currency.value.quotaPerUnit)

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})

// ---- 支付渠道可用性 ----
const hasStripe = computed(
  () => props.enableStripe && !!props.plan?.stripePriceId
)
const hasCreem = computed(
  () => props.enableCreem && !!props.plan?.creemProductId
)
const hasWaffoPancake = computed(
  () => props.enableWaffoPancake && !!props.plan?.waffoPancakeProductId
)
const hasEpay = computed(() => props.enableOnlineTopUp)
const hasBalance = computed(() => props.plan?.allowBalancePay !== false)
const hasOnlinePayment = computed(
  () =>
    hasStripe.value ||
    hasCreem.value ||
    hasWaffoPancake.value ||
    hasEpay.value
)

// ---- 余额支付计算 ----
const balanceCost = computed(() => {
  if (!props.plan) return 0
  return Math.ceil(props.plan.priceAmount * quotaPerUnit.value)
})
const balanceSufficient = computed(() => props.userQuota >= balanceCost.value)

// ---- 购买次数限制 ----
const purchaseLimitReached = computed(() => {
  if (!props.purchaseLimit || props.purchaseLimit <= 0) return false
  return props.purchaseCount >= props.purchaseLimit
})

// ---- 支付处理状态 ----
const processing = ref<string | null>(null)

function buildPayRequest(): SubscriptionPayRequest {
  return { planId: props.plan!.id }
}

// ---- 余额支付 ----
async function handleBalancePay(): Promise<void> {
  if (!props.plan || processing.value) return
  try {
    await ElMessageBox.confirm(
      props.isUpgrade
        ? t('wallet.subscription.purchase.upgradeConfirmMsg', { quota: formatQuotaWithCurrency(balanceCost.value) })
        : t('wallet.subscription.purchase.balancePayConfirmMsg', { quota: formatQuotaWithCurrency(balanceCost.value) }),
      props.isUpgrade
        ? t('wallet.subscription.purchase.upgradeConfirmTitle')
        : t('wallet.subscription.purchase.balancePayConfirmTitle'),
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        type: 'warning',
      }
    )
  } catch {
    return // 用户取消
  }

  processing.value = 'balance'
  try {
    await paySubscriptionBalance(buildPayRequest())
    ElMessage.success(
      props.isUpgrade
        ? t('wallet.subscription.purchase.upgradeSuccess')
        : t('wallet.subscription.purchase.subscribeSuccess')
    )
    emit('purchase-success')
    dialogVisible.value = false
  } catch {
    // 错误由请求拦截器统一提示
  } finally {
    processing.value = null
  }
}

// ---- Stripe 支付 ----
async function handleStripePay(): Promise<void> {
  if (!props.plan || processing.value) return
  processing.value = 'stripe'
  try {
    const res: SubscriptionPayResponse =
      await paySubscriptionStripe(buildPayRequest())
    if (res.payLink) {
      window.open(res.payLink)
    } else {
      ElMessage.error(t('wallet.subscription.purchase.payLinkError'))
    }
  } catch {
    // 错误由请求拦截器统一提示
  } finally {
    processing.value = null
  }
}

// ---- Creem 支付 ----
async function handleCreemPay(): Promise<void> {
  if (!props.plan || processing.value) return
  processing.value = 'creem'
  try {
    const res: SubscriptionPayResponse =
      await paySubscriptionCreem(buildPayRequest())
    if (res.checkoutUrl) {
      window.open(res.checkoutUrl)
    } else {
      ElMessage.error(t('wallet.subscription.purchase.payLinkError'))
    }
  } catch {
    // 错误由请求拦截器统一提示
  } finally {
    processing.value = null
  }
}

// ---- WaffoPancake 支付 ----
async function handleWaffoPancakePay(): Promise<void> {
  if (!props.plan || processing.value) return
  processing.value = 'waffoPancake'
  try {
    const res: SubscriptionPayResponse =
      await paySubscriptionWaffoPancake(buildPayRequest())
    if (res.checkoutUrl) {
      window.location.href = res.checkoutUrl
    } else {
      ElMessage.error(t('wallet.subscription.purchase.payLinkError'))
    }
  } catch {
    // 错误由请求拦截器统一提示
  } finally {
    processing.value = null
  }
}

// ---- Epay 支付：构造 POST 表单提交 ----
async function handleEpayPay(): Promise<void> {
  if (!props.plan || processing.value) return
  processing.value = 'epay'
  try {
    const res: SubscriptionPayResponse =
      await paySubscriptionEpay(buildPayRequest())
    if (res.url) {
      const form = document.createElement('form')
      form.method = 'POST'
      form.action = res.url
      // 提交订单号、token 等字段
      if (res.orderId) {
        const input = document.createElement('input')
        input.type = 'hidden'
        input.name = 'orderId'
        input.value = res.orderId
        form.appendChild(input)
      }
      if (res.token) {
        const input = document.createElement('input')
        input.type = 'hidden'
        input.name = 'token'
        input.value = res.token
        form.appendChild(input)
      }
      document.body.appendChild(form)
      form.submit()
      document.body.removeChild(form)
    } else {
      ElMessage.error(t('wallet.subscription.purchase.payLinkError'))
    }
  } catch {
    // 错误由请求拦截器统一提示
  } finally {
    processing.value = null
  }
}
</script>

<template>
  <ElDialog
    v-model="dialogVisible"
    :title="props.isUpgrade ? t('wallet.subscription.purchase.upgradeTitle') : t('wallet.subscription.purchase.title')"
    width="520px"
    align-center
    :close-on-click-modal="false"
  >
    <div
      v-if="plan"
      class="subscription-purchase"
    >
      <!-- 购买次数限制提示 -->
      <ElAlert
        v-if="purchaseLimitReached"
        type="warning"
        :closable="false"
        show-icon
        :title="t('wallet.subscription.purchase.purchaseLimitReachedTitle')"
        :description="t('wallet.subscription.purchase.purchaseLimitDesc', { limit: props.purchaseLimit, count: props.purchaseCount })"
      />

      <!-- 套餐信息区 -->
      <ElDescriptions
        :column="1"
        border
        size="small"
        class="subscription-purchase__info"
      >
        <ElDescriptionsItem :label="t('wallet.subscription.purchase.planName')">
          {{ plan.title }}
        </ElDescriptionsItem>
        <ElDescriptionsItem
          v-if="plan.subtitle"
          :label="t('wallet.subscription.purchase.planDesc')"
        >
          {{ plan.subtitle }}
        </ElDescriptionsItem>
        <ElDescriptionsItem :label="t('wallet.subscription.purchase.validPeriod')">
          {{ formatDuration(plan) }}
        </ElDescriptionsItem>
        <ElDescriptionsItem :label="t('wallet.subscription.purchase.resetPeriod')">
          {{ formatResetPeriod(plan) }}
        </ElDescriptionsItem>
        <ElDescriptionsItem :label="t('wallet.subscription.purchase.deliveredQuota')">
          {{ formatQuotaWithCurrency(plan.totalAmount) }}
        </ElDescriptionsItem>
        <ElDescriptionsItem
          v-if="plan.upgradeGroup"
          :label="t('wallet.subscription.purchase.upgradeGroup')"
        >
          {{ plan.upgradeGroup }}
        </ElDescriptionsItem>
        <ElDescriptionsItem :label="t('wallet.subscription.purchase.amountPayable')">
          <span class="subscription-purchase__price">
            {{ plan.priceAmount }} {{ plan.currency }}
          </span>
        </ElDescriptionsItem>
      </ElDescriptions>

      <!-- 余额支付区 -->
      <div
        v-if="hasBalance"
        class="subscription-purchase__section"
      >
        <h4 class="subscription-purchase__section-title">
          {{ t('wallet.subscription.purchase.balancePayment') }}
        </h4>
        <div class="subscription-purchase__balance">
          <div class="subscription-purchase__balance-row">
            <span class="subscription-purchase__balance-label">{{ t('wallet.subscription.purchase.requiredQuota') }}</span>
            <span class="subscription-purchase__balance-value">
              {{ formatQuotaWithCurrency(balanceCost) }}
            </span>
          </div>
          <div class="subscription-purchase__balance-row">
            <span class="subscription-purchase__balance-label">{{ t('wallet.subscription.purchase.availableQuota') }}</span>
            <span class="subscription-purchase__balance-value">
              {{ formatQuotaWithCurrency(props.userQuota) }}
            </span>
          </div>
        </div>
        <ElAlert
          v-if="!balanceSufficient"
          type="error"
          :closable="false"
          show-icon
          :title="t('wallet.subscription.purchase.insufficientBalance')"
          :description="t('wallet.subscription.purchase.insufficientBalanceDesc')"
        />
        <ElButton
          type="primary"
          class="el-button--brand"
          :loading="processing === 'balance'"
          :disabled="!balanceSufficient || purchaseLimitReached"
          @click="handleBalancePay"
        >
          {{ props.isUpgrade ? t('wallet.subscription.purchase.upgradeBtn') : t('wallet.subscription.purchase.balancePayBtn') }}
        </ElButton>
      </div>

      <!-- 不允许余额支付提示 -->
      <ElAlert
        v-else
        type="info"
        :closable="false"
        show-icon
        :title="t('wallet.subscription.purchase.notSupportBalancePay')"
      />

      <!-- 在线支付区 -->
      <div
        v-if="hasOnlinePayment"
        class="subscription-purchase__section"
      >
        <h4 class="subscription-purchase__section-title">
          {{ t('wallet.subscription.purchase.onlinePayment') }}
        </h4>
        <div class="subscription-purchase__online">
          <ElButton
            v-if="hasStripe"
            :loading="processing === 'stripe'"
            :disabled="purchaseLimitReached"
            @click="handleStripePay"
          >
            {{ t('wallet.subscription.purchase.stripePay') }}
          </ElButton>
          <ElButton
            v-if="hasCreem"
            :loading="processing === 'creem'"
            :disabled="purchaseLimitReached"
            @click="handleCreemPay"
          >
            {{ t('wallet.subscription.purchase.creemPay') }}
          </ElButton>
          <ElButton
            v-if="hasWaffoPancake"
            :loading="processing === 'waffoPancake'"
            :disabled="purchaseLimitReached"
            @click="handleWaffoPancakePay"
          >
            {{ t('wallet.subscription.purchase.waffoPancakePay') }}
          </ElButton>
          <ElButton
            v-if="hasEpay"
            :loading="processing === 'epay'"
            :disabled="purchaseLimitReached"
            @click="handleEpayPay"
          >
            {{ t('wallet.subscription.purchase.onlinePayBtn') }}
          </ElButton>
        </div>
      </div>
    </div>

    <template #footer>
      <ElButton @click="dialogVisible = false">
        {{ t('common.close') }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped lang="scss">
.subscription-purchase {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-5);

  &__info {
    :deep(.el-descriptions__label) {
      width: 100px;
    }
  }

  &__price {
    font-size: var(--ys-font-size-lg);
    font-weight: 700;
    color: var(--el-color-primary);
  }

  &__section {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-3);
  }

  &__section-title {
    margin: 0;
    font-size: var(--ys-font-size-base);
    font-weight: 600;
    color: var(--el-text-color-regular);
  }

  &__balance {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-2);
    padding: var(--ys-spacing-3);
    background: var(--el-fill-color-light);
    border-radius: var(--el-border-radius-base);
  }

  &__balance-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__balance-label {
    font-size: var(--ys-font-size-sm);
    color: var(--el-text-color-secondary);
  }

  &__balance-value {
    font-size: var(--ys-font-size-base);
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }

  &__online {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-2);
  }
}
</style>
