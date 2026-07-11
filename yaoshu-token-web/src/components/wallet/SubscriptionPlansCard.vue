<script setup lang="ts">
/**
 * 嵌入钱包页，展示用户当前订阅、计费偏好选择器和可用套餐网格。
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  getPublicPlans,
  getSelfSubscriptionFull,
  updateBillingPreference,
  cancelSelfSubscription,
  enableSelfAutoRenew,
} from '@/api/subscription'
import type {
  PlanRecord,
  SubscriptionPlan,
  SelfSubscriptionData,
  UserSubscriptionRecord,
} from '@/api/subscription/types'
import {
  formatDuration,
  formatResetPeriod,
  formatTimestamp,
  getRemainingDays,
} from '@/utils/subscription-format'
import { formatQuotaWithCurrency } from '@/utils/currency'
import SubscriptionPurchaseDialog from './SubscriptionPurchaseDialog.vue'

interface Props {
  topupInfo?: {
    enableStripe?: boolean
    enableCreem?: boolean
    enableWaffoPancake?: boolean
    enableOnlineTopUp?: boolean
    paymentComplianceConfirmed?: boolean
  } | null
  userQuota?: number
}

const props = withDefaults(defineProps<Props>(), {
  topupInfo: null,
  userQuota: 0,
})

const emit = defineEmits<{
  'purchase-success': []
}>()

const { t } = useI18n()

// ---- 数据状态 ----
const plans = ref<PlanRecord[]>([])
const selfData = ref<SelfSubscriptionData | null>(null)
const loading = ref(false)

// ---- 购买对话框 ----
const purchaseDialogVisible = ref(false)
const selectedPlan = ref<SubscriptionPlan | null>(null)
const isUpgradeMode = ref(false)

// ---- 取消/开启自动续期 ----
const cancelling = ref(false)
const renewing = ref(false)

// ---- 计费偏好 ----
// 默认订阅优先：有订阅时优先消耗订阅额度，到期/无订阅时自然回退到钱包
const billingPreference = ref<string>('subscription_first')
const billingUpdating = ref(false)

const billingOptions = computed(() => [
  { value: 'subscription_first', label: t('wallet.subscription.plans.billingStrategy.subscriptionFirst') },
  { value: 'wallet_first', label: t('wallet.subscription.plans.billingStrategy.walletFirst') },
  { value: 'subscription_only', label: t('wallet.subscription.plans.billingStrategy.subscriptionOnly') },
  { value: 'wallet_only', label: t('wallet.subscription.plans.billingStrategy.walletOnly') },
])

// ---- 订阅状态配置 ----
const statusConfig = computed<Record<string, { type: 'success' | 'info' | 'warning'; label: string }>>(() => ({
  active: { type: 'success', label: t('wallet.subscription.plans.status.active') },
  expired: { type: 'info', label: t('wallet.subscription.plans.status.expired') },
  cancelled: { type: 'warning', label: t('wallet.subscription.plans.status.cancelled') },
}))

function getStatusType(status: string): 'success' | 'info' | 'warning' {
  return statusConfig.value[status]?.type ?? 'info'
}

function getStatusLabel(status: string): string {
  return statusConfig.value[status]?.label ?? status
}

// ---- 计算属性 ----

/** 是否存在活跃订阅 */
const hasActiveSubscription = computed(() => {
  return (
    selfData.value?.subscriptions?.some(
      (r) => r.subscription.status === 'active'
    ) ?? false
  )
})

/** 当前活跃订阅对应的套餐 */
const activePlan = computed<SubscriptionPlan | null>(() => {
  if (!hasActiveSubscription.value) return null
  const activeSub = selfData.value?.subscriptions?.find(
    (r) => r.subscription.status === 'active'
  )
  if (!activeSub) return null
  return plans.value.find((r) => r.plan.id === activeSub.subscription.planId)?.plan ?? null
})

/** 判断套餐是否可升级（高于当前等级） */
function canUpgrade(plan: SubscriptionPlan): boolean {
  if (!activePlan.value) return true
  return plan.sortOrder > activePlan.value.sortOrder
}

/** 判断是否为当前订阅的套餐 */
function isCurrentPlan(plan: SubscriptionPlan): boolean {
  return activePlan.value?.id === plan.id
}

/** 当前订阅是否为免费试用 */
const isFreeTrial = computed(
  () => currentSubscription.value?.subscription.type === 'free_trial'
)

/** 当前展示的订阅（优先活跃，其次过期、取消） */
const currentSubscription = computed<UserSubscriptionRecord | null>(() => {
  const subs = selfData.value?.subscriptions
  if (!subs?.length) return null
  return (
    subs.find((r) => r.subscription.status === 'active') ||
    subs.find((r) => r.subscription.status === 'expired') ||
    subs.find((r) => r.subscription.status === 'cancelled') ||
    subs[0] ||
    null
  )
})

/** 当前订阅是否为活跃状态 */
const isSubscriptionActive = computed(
  () => currentSubscription.value?.subscription.status === 'active'
)

/** 当前订阅是否开启自动续期（默认 true，仅显式 false 才为关闭） */
const isAutoRenew = computed(
  () => currentSubscription.value?.subscription.autoRenew !== false
)

/** 剩余天数 */
const remainingDays = computed(() => {
  const sub = currentSubscription.value?.subscription
  if (!sub) return 0
  return getRemainingDays(sub.endTime)
})

/** 额度使用百分比 */
const usagePercent = computed(() => {
  const sub = currentSubscription.value?.subscription
  if (!sub || !sub.amountTotal) return 0
  return Math.min(100, Math.round((sub.amountUsed / sub.amountTotal) * 100))
})

/** 进度条颜色（消费 EP 语义色，商业版自动跟随品牌色） */
const progressColor = computed(() => {
  if (usagePercent.value > 80) return 'var(--el-color-danger)'
  if (usagePercent.value > 60) return 'var(--el-color-warning)'
  return 'var(--el-color-primary)'
})

// ---- 工具方法 ----

/** 根据套餐 ID 获取标题（区分免费试用） */
function getPlanTitle(planId: number): string {
  // planId=0 且当前订阅为 free_trial → 显示"免费试用"
  const isFreeTrial = currentSubscription.value?.subscription.type === 'free_trial'
  if (planId === 0 && isFreeTrial) {
    return t('wallet.subscription.plans.freeTrial')
  }
  const record = plans.value.find((r) => r.plan.id === planId)
  return record?.plan.title || t('wallet.subscription.plans.planTitle', { id: planId })
}

/** 计费偏好选项是否禁用 */
function isBillingOptionDisabled(value: string): boolean {
  if (!hasActiveSubscription.value) {
    return value === 'subscription_first' || value === 'subscription_only'
  }
  return false
}

/** 获取指定套餐的用户购买次数 */
function getPurchaseCount(planId: number): number {
  if (!selfData.value?.allSubscriptions) return 0
  return selfData.value.allSubscriptions.filter(
    (r) => r.subscription.planId === planId
  ).length
}

/** 是否达到购买上限 */
function isPurchaseLimitReached(plan: SubscriptionPlan): boolean {
  if (!plan.maxPurchasePerUser || plan.maxPurchasePerUser <= 0) return false
  return getPurchaseCount(plan.id) >= plan.maxPurchasePerUser
}

/** 格式化价格（带货币符号） */
function formatPrice(plan: SubscriptionPlan): string {
  if (plan.currency === 'CNY') return `¥${plan.priceAmount}`
  if (plan.currency === 'USD') return `$${plan.priceAmount}`
  return `${plan.currency} ${plan.priceAmount}`
}

// ---- 数据加载 ----
async function loadData(): Promise<void> {
  loading.value = true
  try {
    const [plansRes, selfRes] = await Promise.all([
      getPublicPlans(),
      getSelfSubscriptionFull(),
    ])
    plans.value = plansRes || []
    selfData.value = selfRes || null
    billingPreference.value = selfData.value?.billingPreference || 'subscription_first'
  } catch {
    // 错误由请求拦截器统一提示
  } finally {
    loading.value = false
  }
}

// ---- 计费偏好切换（乐观更新 + 失败回滚） ----
async function handleBillingChange(val: string): Promise<void> {
  const prev = billingPreference.value
  billingPreference.value = val // 乐观更新
  billingUpdating.value = true
  try {
    await updateBillingPreference(val)
    ElMessage.success(t('wallet.subscription.plans.billingPreferenceUpdated'))
  } catch {
    billingPreference.value = prev // 失败回滚
  } finally {
    billingUpdating.value = false
  }
}

// ---- 购买对话框 ----
function openPurchaseDialog(plan: SubscriptionPlan): void {
  selectedPlan.value = plan
  isUpgradeMode.value = hasActiveSubscription.value && canUpgrade(plan)
  purchaseDialogVisible.value = true
}

function handlePurchaseSuccess(): void {
  purchaseDialogVisible.value = false
  emit('purchase-success')
  loadData()
}

// ---- 关闭自动续期 ----
async function handleCancelSubscription(): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('wallet.subscription.plans.cancelConfirmMsg'),
      t('wallet.subscription.plans.cancelConfirmTitle'),
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        type: 'warning',
      }
    )
  } catch {
    return
  }
  cancelling.value = true
  try {
    await cancelSelfSubscription()
    ElMessage.success(t('wallet.subscription.plans.cancelSuccess'))
    emit('purchase-success')
    await loadData()
  } catch {
    // 错误由请求拦截器统一提示
  } finally {
    cancelling.value = false
  }
}

// ---- 重新开启自动续期 ----
async function handleEnableAutoRenew(): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('wallet.subscription.plans.enableAutoRenewConfirmMsg'),
      t('wallet.subscription.plans.enableAutoRenewConfirmTitle'),
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        type: 'info',
      }
    )
  } catch {
    return
  }
  renewing.value = true
  try {
    await enableSelfAutoRenew()
    ElMessage.success(t('wallet.subscription.plans.enableAutoRenewSuccess'))
    emit('purchase-success')
    await loadData()
  } catch {
    // 错误由请求拦截器统一提示
  } finally {
    renewing.value = false
  }
}

onMounted(() => {
  loadData()
})

// 在线支付（Stripe/Creem/Epay 等）跳转外部页面，用户返回时自动刷新数据
function handleVisibilityChange(): void {
  if (!document.hidden) {
    loadData()
    emit('purchase-success')
  }
}

onMounted(() => {
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onUnmounted(() => {
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<template>
  <div
    v-if="plans.length > 0"
    class="subscription-plans"
  >
    <!-- 我的订阅 + 计费偏好：双列网格填满全宽 -->
    <div class="subscription-plans__top">
    <!-- 我的订阅区域 -->
    <ElCard
      class="subscription-plans__card"
      shadow="never"
    >
      <template #header>
        <div class="subscription-plans__card-header">
          <span class="subscription-plans__card-title">{{ t('wallet.subscription.plans.mySubscription') }}</span>
        </div>
      </template>

      <div
        v-if="loading"
        class="subscription-plans__skeleton"
      >
        <ElSkeleton
          :rows="3"
          animated
        />
      </div>

      <div
        v-else-if="currentSubscription"
        class="subscription-plans__sub"
      >
        <!-- 状态与套餐名 -->
        <div class="subscription-plans__sub-top">
          <ElTag
            :type="getStatusType(currentSubscription.subscription.status)"
            size="small"
            effect="light"
          >
            {{ getStatusLabel(currentSubscription.subscription.status) }}
          </ElTag>
          <!-- 免费试用徽章 -->
          <ElTag
            v-if="isFreeTrial"
            type="primary"
            size="small"
            effect="plain"
          >
            {{ t('wallet.subscription.plans.freeTrial') }}
          </ElTag>
          <!-- 自动续期标签（仅付费订阅显示） -->
          <ElTag
            v-else-if="isSubscriptionActive"
            :type="isAutoRenew ? 'success' : 'warning'"
            size="small"
            effect="plain"
          >
            {{ isAutoRenew
              ? t('wallet.subscription.plans.status.autoRenewOn')
              : t('wallet.subscription.plans.status.autoRenewOff') }}
          </ElTag>
          <span class="subscription-plans__sub-plan">
            {{ getPlanTitle(currentSubscription.subscription.planId) }}
          </span>
        </div>

        <!-- 到期时间与剩余天数 -->
        <div class="subscription-plans__sub-details">
          <div class="subscription-plans__sub-detail">
            <span class="subscription-plans__sub-label">{{ t('wallet.subscription.plans.expireTime') }}</span>
            <span class="subscription-plans__sub-value">
              {{ formatTimestamp(currentSubscription.subscription.endTime) }}
            </span>
          </div>
          <template v-if="isSubscriptionActive">
            <div class="subscription-plans__sub-detail">
              <span class="subscription-plans__sub-label">{{ t('wallet.subscription.plans.remainingDays') }}</span>
              <span class="subscription-plans__sub-value subscription-plans__sub-value--accent">
                {{ t('wallet.subscription.plans.remainingDaysValue', { days: remainingDays }) }}
              </span>
            </div>
          </template>
        </div>

        <!-- 额度使用进度条 -->
        <template v-if="isSubscriptionActive">
          <div class="subscription-plans__usage">
            <div class="subscription-plans__usage-header">
              <span class="subscription-plans__usage-label">{{ t('wallet.subscription.plans.quotaUsage') }}</span>
              <span class="subscription-plans__usage-text">
                {{ formatQuotaWithCurrency(currentSubscription.subscription.amountUsed) }} /
                {{ formatQuotaWithCurrency(currentSubscription.subscription.amountTotal) }}
              </span>
            </div>
            <ElProgress
              :percentage="usagePercent"
              :stroke-width="8"
              :color="progressColor"
              :show-text="false"
            />
          </div>
          <!-- 续期操作（免费试用不显示，不涉及续期） -->
          <div
            v-if="!isFreeTrial"
            class="subscription-plans__renew-actions"
          >
            <ElButton
              v-if="isAutoRenew"
              :loading="cancelling"
              size="small"
              plain
              type="warning"
              @click="handleCancelSubscription"
            >
              {{ t('wallet.subscription.plans.cancelSubscription') }}
            </ElButton>
            <ElButton
              v-else
              :loading="renewing"
              size="small"
              type="primary"
              @click="handleEnableAutoRenew"
            >
              {{ t('wallet.subscription.plans.enableAutoRenew') }}
            </ElButton>
            <span class="subscription-plans__renew-hint">
              {{ t('wallet.subscription.plans.autoRenewHint') }}
            </span>
          </div>
        </template>
      </div>

      <ElEmpty
        v-else
        :description="t('wallet.subscription.plans.noActiveSubscription')"
        :image-size="60"
      />
    </ElCard>

    <!-- SPI 扩展点：订阅用量进度卡片（商业版注入，开源版不渲染） -->
    <!-- 排在计费偏好之前：用户先看用量再决定偏好 -->
    <slot name="progress" />

    <!-- 计费偏好选择器 -->
    <ElCard
      class="subscription-plans__card"
      shadow="never"
    >
      <template #header>
        <div class="subscription-plans__card-header">
          <span class="subscription-plans__card-title">{{ t('wallet.subscription.plans.billingPreference') }}</span>
        </div>
      </template>

      <div class="subscription-plans__billing">
        <div class="subscription-plans__billing-row">
          <span class="subscription-plans__billing-label">{{ t('wallet.subscription.plans.deductionStrategy') }}</span>
          <ElSelect
            :model-value="billingPreference"
            :loading="billingUpdating"
            :placeholder="t('wallet.subscription.plans.deductionStrategyPlaceholder')"
            style="width: 200px"
            @change="handleBillingChange"
          >
            <ElOption
              v-for="opt in billingOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
              :disabled="isBillingOptionDisabled(opt.value)"
            />
          </ElSelect>
        </div>
        <p class="subscription-plans__billing-hint">
          <span v-if="!hasActiveSubscription">
            {{ t('wallet.subscription.plans.noActiveSubscriptionHint') }}
          </span>
          <span v-else>{{ t('wallet.subscription.plans.hasActiveSubscriptionHint') }}</span>
        </p>
      </div>
    </ElCard>
    </div>

    <!-- 可用套餐网格 -->
    <div class="subscription-plans__grid">
      <ElCard
        v-for="(record, index) in plans"
        :key="record.plan.id"
        class="subscription-plans__plan"
        :class="{
          'subscription-plans__plan--recommended': index === 0,
          'subscription-plans__plan--current': isCurrentPlan(record.plan),
        }"
        shadow="hover"
      >
        <!-- 推荐徽章 -->
        <div
          v-if="index === 0 && !isCurrentPlan(record.plan)"
          class="subscription-plans__recommend"
        >
          {{ t('wallet.subscription.plans.recommend') }}
        </div>

        <!-- 当前订阅徽章 -->
        <div
          v-if="isCurrentPlan(record.plan)"
          class="subscription-plans__current-badge"
        >
          {{ t('wallet.subscription.plans.currentPlan') }}
        </div>

        <!-- 套餐标题 -->
        <div class="subscription-plans__plan-header">
          <h3 class="subscription-plans__plan-title">
            {{ record.plan.title }}
          </h3>
          <p
            v-if="record.plan.subtitle"
            class="subscription-plans__plan-subtitle"
          >
            {{ record.plan.subtitle }}
          </p>
        </div>

        <!-- 价格 -->
        <div class="subscription-plans__plan-price">
          <span class="subscription-plans__plan-price-amount">
            {{ formatPrice(record.plan) }}
          </span>
          <span class="subscription-plans__plan-price-period">
            / {{ formatDuration(record.plan) }}
          </span>
        </div>

        <!-- 套餐详情 -->
        <div class="subscription-plans__plan-details">
          <div class="subscription-plans__plan-detail">
            <span class="subscription-plans__plan-detail-label">{{ t('wallet.subscription.plans.validPeriod') }}</span>
            <span class="subscription-plans__plan-detail-value">
              {{ formatDuration(record.plan) }}
            </span>
          </div>
          <div class="subscription-plans__plan-detail">
            <span class="subscription-plans__plan-detail-label">{{ t('wallet.subscription.plans.resetPeriod') }}</span>
            <span class="subscription-plans__plan-detail-value">
              {{ formatResetPeriod(record.plan) }}
            </span>
          </div>
          <div class="subscription-plans__plan-detail">
            <span class="subscription-plans__plan-detail-label">{{ t('wallet.subscription.plans.totalQuota') }}</span>
            <span class="subscription-plans__plan-detail-value">
              {{ formatQuotaWithCurrency(record.plan.totalAmount) }}
            </span>
          </div>
          <div class="subscription-plans__plan-detail">
            <span class="subscription-plans__plan-detail-label">{{ t('wallet.subscription.plans.purchaseLimit') }}</span>
            <span class="subscription-plans__plan-detail-value">
              {{
                record.plan.maxPurchasePerUser > 0
                  ? record.plan.maxPurchasePerUser + ' ' + t('wallet.subscription.plans.times')
                  : t('wallet.subscription.plans.unlimited')
              }}
            </span>
          </div>
        </div>

        <!-- 购买次数提示 -->
        <div
          v-if="record.plan.maxPurchasePerUser > 0"
          class="subscription-plans__plan-purchase"
        >
          {{ t('wallet.subscription.plans.purchased', { count: getPurchaseCount(record.plan.id), limit: record.plan.maxPurchasePerUser }) }}
        </div>

        <!-- 订阅按钮 -->
        <ElButton
          v-if="isCurrentPlan(record.plan)"
          type="info"
          class="subscription-plans__plan-btn"
          disabled
        >
          {{ t('wallet.subscription.plans.currentPlan') }}
        </ElButton>
        <ElButton
          v-else-if="hasActiveSubscription && !canUpgrade(record.plan)"
          type="info"
          class="subscription-plans__plan-btn"
          disabled
        >
          {{ t('wallet.subscription.plans.cannotDowngrade') }}
        </ElButton>
        <ElButton
          v-else
          type="primary"
          class="subscription-plans__plan-btn"
          :disabled="isPurchaseLimitReached(record.plan)"
          @click="openPurchaseDialog(record.plan)"
        >
          {{ isPurchaseLimitReached(record.plan)
            ? t('wallet.subscription.plans.purchaseLimitReached')
            : (hasActiveSubscription && canUpgrade(record.plan)
              ? t('wallet.subscription.plans.upgrade')
              : t('wallet.subscription.plans.subscribeNow')) }}
        </ElButton>
      </ElCard>
    </div>

    <!-- 购买对话框 -->
    <SubscriptionPurchaseDialog
      v-model:visible="purchaseDialogVisible"
      :plan="selectedPlan"
      :is-upgrade="isUpgradeMode"
      :enable-stripe="props.topupInfo?.enableStripe"
      :enable-creem="props.topupInfo?.enableCreem"
      :enable-waffo-pancake="props.topupInfo?.enableWaffoPancake"
      :enable-online-top-up="props.topupInfo?.enableOnlineTopUp"
      :user-quota="props.userQuota"
      :purchase-limit="selectedPlan?.maxPurchasePerUser"
      :purchase-count="selectedPlan ? getPurchaseCount(selectedPlan.id) : 0"
      @purchase-success="handlePurchaseSuccess"
    />
  </div>
</template>

<style scoped lang="scss">
.subscription-plans {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-4);

  &__top {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
    gap: var(--ys-spacing-4);

    @media (width <= 640px) {
      grid-template-columns: 1fr;
    }
  }

  &__card {
    border-radius: var(--ys-radius-md);

    :deep(.el-card__header) {
      padding: var(--ys-spacing-3) var(--ys-spacing-5);
    }

    :deep(.el-card__body) {
      padding: var(--ys-spacing-5);
    }
  }

  &__card-header {
    display: flex;
    align-items: center;
  }

  &__card-title {
    font-size: 15px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  &__skeleton {
    padding: var(--ys-spacing-1) 0;
  }

  // ---- 我的订阅区域 ----
  &__sub {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-4);
  }

  &__sub-top {
    display: flex;
    gap: 10px;
    align-items: center;
  }

  &__sub-plan {
    font-size: 15px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  &__sub-details {
    display: flex;
    gap: var(--ys-spacing-8);
  }

  &__sub-detail {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-1);
  }

  &__sub-label {
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-secondary);
  }

  &__sub-value {
    font-size: var(--ys-font-size-base);
    font-weight: 500;
    font-variant-numeric: tabular-nums;
    color: var(--el-text-color-primary);

    &--accent {
      color: var(--el-color-success);
    }
  }

  &__usage {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-2);
  }

  &__usage-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__usage-label {
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-secondary);
  }

  &__usage-text {
    font-size: var(--ys-font-size-sm);
    font-weight: 500;
    font-variant-numeric: tabular-nums;
    color: var(--el-text-color-regular);
  }

  &__renew-actions {
    display: flex;
    gap: var(--ys-spacing-3);
    align-items: center;
  }

  &__renew-hint {
    font-size: var(--ys-font-size-xs);
    line-height: 1.5;
    color: var(--el-text-color-secondary);
  }

  // ---- 计费偏好选择器 ----
  &__billing {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-2);
  }

  &__billing-row {
    display: flex;
    gap: var(--ys-spacing-3);
    align-items: center;
  }

  &__billing-label {
    font-size: var(--ys-font-size-base);
    color: var(--el-text-color-regular);
    white-space: nowrap;
  }

  &__billing-hint {
    margin: 0;
    font-size: var(--ys-font-size-xs);
    line-height: 1.5;
    color: var(--el-text-color-secondary);
  }

  // ---- 可用套餐网格 ----
  &__grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(280px, 380px));
    gap: var(--ys-spacing-4);

    @media (width <= 640px) {
      grid-template-columns: 1fr;
    }
  }

  &__plan {
    position: relative;
    display: flex;
    flex-direction: column;
    border-radius: var(--ys-radius-md);
    transition: transform 0.2s ease, box-shadow 0.2s ease;

    &:hover {
      transform: translateY(-2px);
    }

    :deep(.el-card__body) {
      display: flex;
      flex: 1;
      flex-direction: column;
      padding: var(--ys-spacing-5);
    }

    &--recommended {
      border-color: var(--el-color-primary);
    }

    &--current {
      border-color: var(--el-color-success);
    }
  }

  &__recommend {
    position: absolute;
    top: 0;
    right: 0;
    padding: 2px var(--ys-spacing-3);
    font-size: var(--ys-font-size-xs);
    font-weight: 600;
    line-height: 20px;
    color: #fff;
    background: var(--el-color-primary);
    border-radius: 0 var(--ys-radius-md);
  }

  &__current-badge {
    position: absolute;
    top: 0;
    right: 0;
    padding: 2px var(--ys-spacing-3);
    font-size: var(--ys-font-size-xs);
    font-weight: 600;
    line-height: 20px;
    color: #fff;
    background: var(--el-color-success);
    border-radius: 0 var(--ys-radius-md);
  }

  &__plan-header {
    margin-bottom: var(--ys-spacing-3);
  }

  &__plan-title {
    margin: 0;
    font-size: var(--ys-font-size-lg);
    font-weight: 700;
    color: var(--el-text-color-primary);
  }

  &__plan-subtitle {
    margin: var(--ys-spacing-1) 0 0;
    font-size: var(--ys-font-size-sm);
    line-height: 1.4;
    color: var(--el-text-color-secondary);
  }

  &__plan-price {
    display: flex;
    gap: var(--ys-spacing-1);
    align-items: baseline;
    padding-bottom: var(--ys-spacing-4);
    margin-bottom: var(--ys-spacing-4);
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  &__plan-price-amount {
    font-size: var(--ys-font-size-3xl);
    font-weight: 700;
    font-variant-numeric: tabular-nums;
    color: var(--el-color-primary);
  }

  &__plan-price-period {
    font-size: var(--ys-font-size-sm);
    color: var(--el-text-color-secondary);
  }

  &__plan-details {
    display: flex;
    flex: 1;
    flex-direction: column;
    gap: 10px;
    margin-bottom: var(--ys-spacing-4);
  }

  &__plan-detail {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: var(--ys-font-size-sm);
  }

  &__plan-detail-label {
    color: var(--el-text-color-secondary);
  }

  &__plan-detail-value {
    font-weight: 500;
    font-variant-numeric: tabular-nums;
    color: var(--el-text-color-primary);
  }

  &__plan-purchase {
    margin-bottom: var(--ys-spacing-3);
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-secondary);
    text-align: right;
  }

  &__plan-btn {
    width: 100%;
  }
}
</style>
