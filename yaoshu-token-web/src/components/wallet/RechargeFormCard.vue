<script setup lang="ts">
/**
 * 职责：预设额度选择 + 自定义额度 + 支付方式 + 兑换码 + Creem 产品。
 * 木偶组件：所有支付动作通过 emit 交给容器路由到对应 composable。
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatQuotaWithCurrency } from '@/utils/currency'
import { formatCreemPrice, getDiscountLabel } from '@/utils/wallet/format'
import { getDefaultPaymentType, getMinTopupAmount } from '@/utils/wallet/payment'
import { useRedemption } from '@/composables/wallet/useRedemption'
import type { CreemProduct, TopupInfo, WaffoPayMethod } from '@/api/wallet/types'

interface Props {
  topupInfo: TopupInfo | null
  loading?: boolean
  complianceConfirmed?: boolean
  processing?: boolean
  creemProcessing?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  complianceConfirmed: true,
  processing: false,
  creemProcessing: false,
})

const emit = defineEmits<{
  (e: 'recharge', amount: number, paymentType: string): void
  (e: 'buyCreem', productId: string): void
  (e: 'refresh'): void
}>()

const { t } = useI18n()
const { redeeming, redeemCode } = useRedemption()

// ---- 额度选择 ----
const selectedAmount = ref<number>(0)
const customAmount = ref<string>('')
const isCustom = ref(false)

const minTopup = computed(() => getMinTopupAmount(props.topupInfo))

const presetAmounts = computed(() => {
  if (!props.topupInfo?.amountOptions?.length) return []
  return props.topupInfo.amountOptions.map((value) => ({
    value,
    discount: props.topupInfo?.discount?.[value],
  }))
})

function selectPreset(value: number): void {
  isCustom.value = false
  selectedAmount.value = value
}

function onCustomInput(value: string): void {
  isCustom.value = true
  customAmount.value = value
  const parsed = Number.parseFloat(value)
  selectedAmount.value = Number.isFinite(parsed) ? parsed : 0
}

const effectiveAmount = computed(() => {
  if (isCustom.value) {
    const parsed = Number.parseFloat(customAmount.value)
    return Number.isFinite(parsed) ? parsed : 0
  }
  return selectedAmount.value
})

// ---- 支付方式 ----
interface PaymentOption {
  type: string
  name: string
  iconUrl?: string
  color?: string
}

const paymentOptions = computed<PaymentOption[]>(() => {
  if (!props.topupInfo) return []
  const options: PaymentOption[] = []

  if (props.topupInfo.enableOnlineTopup) {
    for (const method of props.topupInfo.payMethods || []) {
      options.push({
        type: method.type,
        name: method.name || method.type,
        iconUrl: method.icon,
        color: method.color,
      })
    }
  }

  if (props.topupInfo.enableStripeTopup) {
    options.push({ type: 'stripe', name: 'Stripe', color: '#635BFF' })
  }

  if (props.topupInfo.enableWaffoTopup) {
    const waffoMethods = props.topupInfo.waffoPayMethods || []
    waffoMethods.forEach((method: WaffoPayMethod, index: number) => {
      options.push({
        type: `waffo:${index}`,
        name: method.payMethodName || method.name || `Waffo ${index + 1}`,
        iconUrl: method.icon,
      })
    })
  }

  if (props.topupInfo.enableWaffoPancakeTopup) {
    options.push({ type: 'waffo_pancake', name: 'Waffo Pancake' })
  }

  return options
})

const selectedPaymentType = ref<string>('')
const agreedToCompliance = ref(false)

watch(
  () => props.topupInfo,
  (info) => {
    if (info && !selectedPaymentType.value) {
      selectedPaymentType.value = getDefaultPaymentType(info)
    }
  },
  { immediate: true }
)

// ---- 充值校验 ----
const canRecharge = computed(() => {
  if (effectiveAmount.value < minTopup.value) return false
  if (!selectedPaymentType.value) return false
  if (!props.complianceConfirmed && !agreedToCompliance.value) return false
  if (props.processing) return false
  return true
})

function handleRecharge(): void {
  if (!canRecharge.value) return
  emit('recharge', effectiveAmount.value, selectedPaymentType.value)
}

// ---- 兑换码 ----
const redemptionCode = ref('')

async function handleRedeem(): Promise<void> {
  const success = await redeemCode(redemptionCode.value)
  if (success) {
    redemptionCode.value = ''
    emit('refresh')
  }
}

// ---- Creem ----
const creemProducts = computed<CreemProduct[]>(() => {
  return props.topupInfo?.creemProducts || []
})
</script>

<template>
  <div class="recharge-card">
    <div
      v-if="loading"
      class="recharge-card__loading"
    >
      <ElSkeleton
        :rows="6"
        animated
      />
    </div>

    <div
      v-else
      class="recharge-card__body"
    >
      <!-- 预设额度 -->
      <div
        v-if="presetAmounts.length"
        class="recharge-card__section"
      >
        <h4 class="recharge-card__title">
          {{ t('wallet.recharge.presetAmount') }}
        </h4>
        <div class="recharge-card__presets">
          <button
            v-for="preset in presetAmounts"
            :key="preset.value"
            type="button"
            class="recharge-card__preset"
            :class="{ 'is-active': !isCustom && selectedAmount === preset.value }"
            @click="selectPreset(preset.value)"
          >
            <span class="recharge-card__preset-value">{{ preset.value }}</span>
            <span
              v-if="preset.discount && preset.discount < 1"
              class="recharge-card__preset-badge"
            >
              {{ getDiscountLabel(preset.discount) }}
            </span>
          </button>
        </div>
      </div>

      <!-- 自定义额度 -->
      <div class="recharge-card__section">
        <h4 class="recharge-card__title">
          {{ t('wallet.recharge.customAmount') }}
        </h4>
        <ElInput
          :model-value="customAmount"
          type="number"
          :placeholder="t('wallet.recharge.minPlaceholder', { min: minTopup })"
          clearable
          @update:model-value="onCustomInput"
        >
          <template #append>
            {{ t('wallet.recharge.unit') }}
          </template>
        </ElInput>
      </div>

      <!-- 支付方式 -->
      <div
        v-if="paymentOptions.length"
        class="recharge-card__section"
      >
        <h4 class="recharge-card__title">
          {{ t('wallet.recharge.paymentMethod') }}
        </h4>
        <div class="recharge-card__payments">
          <button
            v-for="opt in paymentOptions"
            :key="opt.type"
            type="button"
            class="recharge-card__payment"
            :class="{ 'is-active': selectedPaymentType === opt.type }"
            :style="selectedPaymentType === opt.type && opt.color ? { borderColor: opt.color } : {}"
            @click="selectedPaymentType = opt.type"
          >
            <img
              v-if="opt.iconUrl"
              :src="opt.iconUrl"
              class="recharge-card__payment-icon"
              alt=""
            >
            <i
              v-else
              class="i-ep-credit-card recharge-card__payment-icon-default"
            />
            <span class="recharge-card__payment-name">{{ opt.name }}</span>
          </button>
        </div>
      </div>

      <!-- 合规协议 -->
      <div
        v-if="!complianceConfirmed"
        class="recharge-card__compliance"
      >
        <ElCheckbox v-model="agreedToCompliance">
          {{ t('wallet.recharge.complianceLabel') }}
        </ElCheckbox>
      </div>

      <!-- 充值按钮 -->
      <ElButton
        type="primary"
        size="large"
        class="recharge-card__submit"
        :loading="processing"
        :disabled="!canRecharge"
        @click="handleRecharge"
      >
        {{ t('wallet.recharge.confirmRecharge') }}
      </ElButton>

      <!-- 兑换码 -->
      <div
        v-if="topupInfo?.enableRedemption"
        class="recharge-card__section recharge-card__redemption"
      >
        <h4 class="recharge-card__title">
          {{ t('wallet.recharge.redemptionCode') }}
        </h4>
        <div class="recharge-card__redemption-row">
          <ElInput
            v-model="redemptionCode"
            :placeholder="t('wallet.recharge.redemptionPlaceholder')"
            clearable
            @keyup.enter="handleRedeem"
          />
          <ElButton
            :loading="redeeming"
            @click="handleRedeem"
          >
            {{ t('wallet.recharge.redeem') }}
          </ElButton>
        </div>
      </div>

      <!-- Creem 产品 -->
      <div
        v-if="creemProducts.length"
        class="recharge-card__section"
      >
        <h4 class="recharge-card__title">
          {{ t('wallet.recharge.creemPlans') }}
        </h4>
        <div class="recharge-card__creem">
          <div
            v-for="product in creemProducts"
            :key="product.productId"
            class="recharge-card__creem-item"
          >
            <div class="recharge-card__creem-name">
              {{ product.name }}
            </div>
            <div class="recharge-card__creem-price">
              {{ formatCreemPrice(product.price, product.currency) }}
            </div>
            <div class="recharge-card__creem-quota">
              {{ formatQuotaWithCurrency(product.quota) }}
            </div>
            <ElButton
              size="small"
              :loading="creemProcessing"
              @click="emit('buyCreem', product.productId)"
            >
              {{ t('wallet.recharge.buy') }}
            </ElButton>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.recharge-card {
  overflow: hidden;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--ys-radius-md);

  &__loading {
    padding: var(--ys-spacing-5);
  }

  &__body {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-5);
    padding: var(--ys-spacing-5);
  }

  &__section {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  &__title {
    margin: 0;
    font-size: var(--ys-font-size-sm);
    font-weight: 600;
    color: var(--el-text-color-regular);
  }

  &__presets {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(80px, 1fr));
    gap: var(--ys-spacing-2);
  }

  &__preset {
    position: relative;
    display: flex;
    flex-direction: column;
    gap: 2px;
    align-items: center;
    padding: var(--ys-spacing-2) var(--ys-spacing-1);
    cursor: pointer;
    background: var(--el-fill-color-blank);
    border: 1px solid var(--el-border-color);
    border-radius: var(--ys-radius-base);
    transition: all 0.15s;

    &:hover {
      border-color: var(--el-color-primary);
    }

    &.is-active {
      background: var(--el-color-primary-light-9);
      border-color: var(--el-color-primary);
    }

    &-value {
      font-size: var(--ys-font-size-lg);
      font-weight: 600;
      font-variant-numeric: tabular-nums;
    }

    &-badge {
      font-size: 10px;
      font-weight: 600;
      color: var(--el-color-danger);
    }
  }

  &__payments {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
    gap: var(--ys-spacing-2);
  }

  &__payment {
    display: flex;
    gap: 6px;
    align-items: center;
    padding: var(--ys-spacing-2) var(--ys-spacing-3);
    cursor: pointer;
    background: var(--el-fill-color-blank);
    border: 1px solid var(--el-border-color);
    border-radius: var(--ys-radius-base);
    transition: all 0.15s;

    &:hover {
      border-color: var(--el-color-primary);
    }

    &.is-active {
      background: var(--el-color-primary-light-9);
      border-color: var(--el-color-primary);
    }

    &-icon {
      width: 18px;
      height: 18px;
      object-fit: contain;
    }

    &-icon-default {
      font-size: var(--ys-font-size-lg);
      color: var(--el-text-color-secondary);
    }

    &-name {
      font-size: var(--ys-font-size-sm);
      font-weight: 500;
    }
  }

  &__compliance {
    padding: var(--ys-spacing-2) 0;
  }

  &__submit {
    width: 100%;
  }

  &__redemption {
    padding-top: var(--ys-spacing-3);
    border-top: 1px dashed var(--el-border-color);

    &-row {
      display: flex;
      gap: var(--ys-spacing-2);
    }
  }

  &__creem {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
    gap: var(--ys-spacing-3);

    &-item {
      display: flex;
      flex-direction: column;
      gap: var(--ys-spacing-1);
      align-items: center;
      padding: var(--ys-spacing-4) var(--ys-spacing-3);
      text-align: center;
      border: 1px solid var(--el-border-color);
      border-radius: var(--ys-radius-md);
    }

    &-name {
      font-size: var(--ys-font-size-base);
      font-weight: 600;
    }

    &-price {
      font-size: var(--ys-font-size-xl);
      font-weight: 700;
      font-variant-numeric: tabular-nums;
      color: var(--el-color-primary);
    }

    &-quota {
      font-size: var(--ys-font-size-xs);
      color: var(--el-text-color-secondary);
    }
  }
}
</style>
