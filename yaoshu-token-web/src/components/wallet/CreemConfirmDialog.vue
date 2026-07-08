<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatCreemPrice } from '@/utils/wallet/format'
import { formatQuotaWithCurrency } from '@/utils/currency'
import type { CreemProduct } from '@/api/wallet/types'

interface Props {
  visible: boolean
  product: CreemProduct | null
  processing?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  processing: false,
})

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'confirm', productId: string): void
}>()

const { t } = useI18n()

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})

const displayPrice = computed(() =>
  props.product ? formatCreemPrice(props.product.price, props.product.currency) : ''
)

const displayQuota = computed(() =>
  props.product ? formatQuotaWithCurrency(props.product.quota) : ''
)

function handleConfirm(): void {
  if (!props.product) return
  emit('confirm', props.product.productId)
}
</script>

<template>
  <ElDialog
    v-model="dialogVisible"
    :title="t('wallet.creem.confirmTitle')"
    width="400px"
    align-center
  >
    <div
      v-if="product"
      class="creem-confirm"
    >
      <div class="creem-confirm__row">
        <span class="creem-confirm__label">{{ t('wallet.creem.product') }}</span>
        <span class="creem-confirm__value">{{ product.name }}</span>
      </div>
      <div class="creem-confirm__row">
        <span class="creem-confirm__label">{{ t('wallet.creem.price') }}</span>
        <span class="creem-confirm__value creem-confirm__value--strong">{{ displayPrice }}</span>
      </div>
      <div class="creem-confirm__row">
        <span class="creem-confirm__label">{{ t('wallet.creem.quota') }}</span>
        <span class="creem-confirm__value">{{ displayQuota }}</span>
      </div>
    </div>
    <template #footer>
      <ElButton @click="dialogVisible = false">
        {{ t('common.cancel') }}
      </ElButton>
      <ElButton
        type="primary"
        :loading="processing"
        @click="handleConfirm"
      >
        {{ t('wallet.creem.confirmBuy') }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped lang="scss">
.creem-confirm {
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

    &--strong {
      font-size: 18px;
      font-weight: 700;
      color: var(--el-color-primary);
    }
  }
}
</style>
