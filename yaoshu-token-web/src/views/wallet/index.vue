<script setup lang="ts">
/**
 * 编排：WalletStatsCard / RechargeFormCard / AffiliateRewardsCard + 4 弹窗。
 * 支付路由：根据 paymentType 分发到 usePayment/useWaffoPayment/useWaffoPancakePayment/useCreemPayment。
 * SUB-01: UserSubscriptionsDialog 管理员用户订阅查看弹窗（已实现）。
 */
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getSelfUser } from '@/api/wallet'
import { useTopupInfo } from '@/composables/wallet/useTopupInfo'
import { usePayment } from '@/composables/wallet/usePayment'
import { useWaffoPayment } from '@/composables/wallet/useWaffoPayment'
import { useWaffoPancakePayment } from '@/composables/wallet/useWaffoPancakePayment'
import { useCreemPayment } from '@/composables/wallet/useCreemPayment'
import { useAffiliate } from '@/composables/wallet/useAffiliate'
import { generateAffiliateLink } from '@/utils/wallet/affiliate'
import WalletStatsCard from '@/components/wallet/WalletStatsCard.vue'
import RechargeFormCard from '@/components/wallet/RechargeFormCard.vue'
import AffiliateRewardsCard from '@/components/wallet/AffiliateRewardsCard.vue'
import SubscriptionPlansCard from '@/components/wallet/SubscriptionPlansCard.vue'
import PaymentConfirmDialog from '@/components/wallet/PaymentConfirmDialog.vue'
import TransferDialog from '@/components/wallet/TransferDialog.vue'
import BillingHistoryDialog from '@/components/wallet/BillingHistoryDialog.vue'
import CreemConfirmDialog from '@/components/wallet/CreemConfirmDialog.vue'
import SpiSlot from '@/plugins/spi/SpiSlot.vue'
import type { CreemProduct, UserWalletData } from '@/api/wallet/types'

const { t } = useI18n()

// ---- 用户钱包数据 ----
const user = ref<UserWalletData | null>(null)
const userLoading = ref(false)

async function fetchUser(): Promise<void> {
  userLoading.value = true
  try {
    user.value = await getSelfUser()
  } catch {
    user.value = null
  } finally {
    userLoading.value = false
  }
}

// ---- 充值配置 ----
const { topupInfo, loading: topupLoading, fetchTopupInfo } = useTopupInfo()

// ---- 支付 composables ----
const { processPayment, processing: paymentProcessing } = usePayment()
const { processWaffoPayment, processing: waffoProcessing } = useWaffoPayment()
const { processWaffoPancakePayment, processing: waffoPancakeProcessing } =
  useWaffoPancakePayment()
const { processCreemPayment, processing: creemProcessing } = useCreemPayment()

// ---- 邀请返利 ----
const {
  affiliateLink,
  loading: affiliateLoading,
  transferring,
  fetchAffiliateLink,
  transferQuota,
} = useAffiliate()

const fullAffiliateLink = ref('')
async function refreshAffiliateLink(): Promise<void> {
  await fetchAffiliateLink()
  fullAffiliateLink.value = affiliateLink.value
    ? generateAffiliateLink(affiliateLink.value)
    : ''
}

// ---- 弹窗状态 ----
const paymentConfirmVisible = ref(false)
const transferVisible = ref(false)
const billingVisible = ref(false)
const creemConfirmVisible = ref(false)

const pendingAmount = ref(0)
const pendingPaymentType = ref('')
const pendingCreemProduct = ref<CreemProduct | null>(null)

// ---- 充值路由 ----
function handleRecharge(amount: number, paymentType: string): void {
  pendingAmount.value = amount
  pendingPaymentType.value = paymentType
  paymentConfirmVisible.value = true
}

async function confirmPayment(): Promise<void> {
  const amount = pendingAmount.value
  const paymentType = pendingPaymentType.value
  if (amount <= 0 || !paymentType) return

  let success = false
  if (paymentType === 'stripe' || !paymentType.startsWith('waffo')) {
    success = await processPayment(amount, paymentType)
  } else if (paymentType === 'waffo_pancake') {
    success = await processWaffoPancakePayment(amount)
  } else if (paymentType.startsWith('waffo:')) {
    const index = Number.parseInt(paymentType.split(':')[1] ?? '0', 10)
    success = await processWaffoPayment(amount, index)
  } else {
    success = await processPayment(amount, paymentType)
  }

  if (success) {
    paymentConfirmVisible.value = false
    await refreshWallet()
  }
}

// ---- Creem 购买 ----
function handleBuyCreem(productId: string): void {
  const product = topupInfo.value?.creemProducts?.find((p) => p.productId === productId)
  if (!product) return
  pendingCreemProduct.value = product
  creemConfirmVisible.value = true
}

async function confirmCreem(productId: string): Promise<void> {
  const success = await processCreemPayment(productId)
  if (success) {
    creemConfirmVisible.value = false
    await refreshWallet()
  }
}

// ---- 邀请转账 ----
async function handleTransfer(quota: number): Promise<void> {
  const success = await transferQuota(quota)
  if (success) {
    transferVisible.value = false
    await refreshWallet()
  }
}

// ---- 刷新 ----
async function refreshWallet(): Promise<void> {
  await Promise.all([fetchUser(), fetchTopupInfo(), refreshAffiliateLink()])
}

onMounted(refreshWallet)
</script>

<template>
  <div class="wallet-page">
    <div class="wallet-page__hero">
      <h1 class="wallet-page__title">
        {{ t('nav.wallet') }}
      </h1>
      <p class="wallet-page__desc">
        {{ t('wallet.description') }}
      </p>
      <ElButton
        text
        type="primary"
        @click="billingVisible = true"
      >
        {{ t('wallet.billing.title') }}
      </ElButton>
    </div>

    <WalletStatsCard
      :user="user"
      :loading="userLoading"
    />

    <!-- SPI 扩展点：定制实现注入订阅用量进度卡片，无注入时不渲染 -->
    <SpiSlot name="wallet-subscription" />

    <!-- 订阅套餐展示+购买（后端 API 已就绪，Vue3 迁移阶段二 SUB-02） -->
    <SubscriptionPlansCard
      :topup-info="topupInfo"
      :user-quota="user?.quota ?? 0"
      @purchase-success="refreshWallet"
    />

    <div class="wallet-page__grid">
      <RechargeFormCard
        :topup-info="topupInfo"
        :loading="topupLoading"
        :compliance-confirmed="topupInfo?.paymentComplianceConfirmed ?? true"
        :processing="paymentProcessing || waffoProcessing || waffoPancakeProcessing"
        :creem-processing="creemProcessing"
        @recharge="handleRecharge"
        @buy-creem="handleBuyCreem"
        @refresh="refreshWallet"
      />
      <AffiliateRewardsCard
        :user="user"
        :affiliate-link="fullAffiliateLink"
        :loading="affiliateLoading"
        :compliance-confirmed="topupInfo?.paymentComplianceConfirmed ?? true"
        @transfer="transferVisible = true"
      />
    </div>

    <PaymentConfirmDialog
      v-model:visible="paymentConfirmVisible"
      :amount="pendingAmount"
      :payment-type="pendingPaymentType"
      :processing="paymentProcessing || waffoProcessing || waffoPancakeProcessing"
      @confirm="confirmPayment"
    />
    <TransferDialog
      v-model:visible="transferVisible"
      :max-quota="user?.affQuota ?? 0"
      :transferring="transferring"
      @confirm="handleTransfer"
    />
    <BillingHistoryDialog v-model:visible="billingVisible" />
    <CreemConfirmDialog
      v-model:visible="creemConfirmVisible"
      :product="pendingCreemProduct"
      :processing="creemProcessing"
      @confirm="confirmCreem"
    />
  </div>
</template>

<style scoped lang="scss">
.wallet-page {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-6);
  padding: var(--ys-spacing-8) var(--ys-spacing-6);

  &__hero {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-2);
  }

  &__title {
    margin: 0;
    font-size: var(--ys-font-size-2xl);
    font-weight: 600;
    letter-spacing: -0.025em;
  }

  &__desc {
    margin: 0;
    font-size: var(--ys-font-size-base);
    color: var(--el-text-color-secondary);
  }

  &__grid {
    display: grid;
    grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr);
    gap: var(--ys-spacing-4);

    @media (width <= 1024px) {
      grid-template-columns: 1fr;
    }
  }
}
</style>
