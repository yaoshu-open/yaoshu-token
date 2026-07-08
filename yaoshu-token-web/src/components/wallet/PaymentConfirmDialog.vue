<script setup lang="ts">
/**
 * 展示金额 + 支付方式，确认后 emit confirm。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatCurrency } from '@/utils/wallet/format'
import { getPaymentMethodName } from '@/utils/wallet/billing'

interface Props {
  visible: boolean
  amount: number
  paymentType: string
  calculating?: boolean
  processing?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  calculating: false,
  processing: false,
})

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'confirm'): void
}>()

const { t } = useI18n()

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})

const displayAmount = computed(() => formatCurrency(props.amount))
const paymentLabel = computed(() => getPaymentMethodName(props.paymentType, t))
</script>

<template>
  <ElDialog
    v-model="dialogVisible"
    :title="t('wallet.payment.confirmTitle')"
    width="400px"
    align-center
  >
    <div class="payment-confirm">
      <div class="payment-confirm__row">
        <span class="payment-confirm__label">{{ t('wallet.payment.amount') }}</span>
        <span
          v-if="calculating"
          class="payment-confirm__value"
        >
          <ElIcon class="is-loading"><i class="i-ep-loading" /></ElIcon>
        </span>
        <span
          v-else
          class="payment-confirm__value payment-confirm__value--strong"
        >
          {{ displayAmount }}
        </span>
      </div>
      <div class="payment-confirm__row">
        <span class="payment-confirm__label">{{ t('wallet.payment.method') }}</span>
        <span class="payment-confirm__value">{{ paymentLabel }}</span>
      </div>
    </div>
    <template #footer>
      <ElButton @click="dialogVisible = false">
        {{ t('common.cancel') }}
      </ElButton>
      <ElButton
        type="primary"
        :loading="processing"
        :disabled="calculating || amount <= 0"
        @click="emit('confirm')"
      >
        {{ t('wallet.payment.confirmPay') }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped lang="scss">
.payment-confirm {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-4);

  &__row {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__label {
    font-size: var(--ys-font-size-base);
    color: var(--el-text-color-secondary);
  }

  &__value {
    font-size: var(--ys-font-size-base);
    font-variant-numeric: tabular-nums;

    &--strong {
      font-size: 18px;
      font-weight: 700;
    }
  }
}
</style>
