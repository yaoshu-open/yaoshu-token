<script setup lang="ts">
/**
 * 将邀请返利额度转入主余额。
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatQuotaWithCurrency } from '@/utils/currency'

interface Props {
  visible: boolean
  maxQuota: number
  transferring?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  transferring: false,
})

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'confirm', quota: number): void
}>()

const { t } = useI18n()

const transferAmount = ref<number>(0)

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})

watch(dialogVisible, (val) => {
  if (val) transferAmount.value = props.maxQuota
})

const canConfirm = computed(
  () => transferAmount.value > 0 && transferAmount.value <= props.maxQuota
)

function handleConfirm(): void {
  if (!canConfirm.value) return
  emit('confirm', transferAmount.value)
}
</script>

<template>
  <ElDialog
    v-model="dialogVisible"
    :title="t('wallet.transfer.title')"
    width="400px"
    align-center
  >
    <div class="transfer-dialog">
      <div class="transfer-dialog__info">
        <span class="transfer-dialog__label">{{ t('wallet.transfer.available') }}</span>
        <span class="transfer-dialog__value">{{ formatQuotaWithCurrency(maxQuota) }}</span>
      </div>
      <ElInput
        v-model="transferAmount"
        type="number"
        :min="0"
        :max="maxQuota"
        :placeholder="t('wallet.transfer.placeholder')"
      >
        <template #append>
          {{ t('wallet.transfer.unit') }}
        </template>
      </ElInput>
    </div>
    <template #footer>
      <ElButton @click="dialogVisible = false">
        {{ t('common.cancel') }}
      </ElButton>
      <ElButton
        type="primary"
        :loading="transferring"
        :disabled="!canConfirm"
        @click="handleConfirm"
      >
        {{ t('wallet.transfer.confirm') }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped lang="scss">
.transfer-dialog {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-4);

  &__info {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__label {
    font-size: var(--ys-font-size-base);
    color: var(--el-text-color-secondary);
  }

  &__value {
    font-size: var(--ys-font-size-lg);
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }
}
</style>
