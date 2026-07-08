<script setup lang="ts">
import { ElButton, ElDialog } from 'element-plus'
import { useI18n } from 'vue-i18n'

withDefaults(
  defineProps<{
    modelValue: boolean
    title: string
    description?: string
    confirmText?: string
    cancelText?: string
    destructive?: boolean
    disabled?: boolean
    loading?: boolean
  }>(),
  {
    description: undefined,
    confirmText: undefined,
    cancelText: undefined,
    destructive: false,
    disabled: false,
    loading: false,
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm'): void
  (e: 'cancel'): void
}>()

const { t } = useI18n()

function handleConfirm() {
  emit('confirm')
}

function handleCancel() {
  emit('cancel')
  emit('update:modelValue', false)
}
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    :title="title"
    width="420px"
    :close-on-click-modal="false"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="confirm-dialog__body">
      <div
        v-if="destructive"
        class="confirm-dialog__icon confirm-dialog__icon--danger"
      >
        <i class="i-ep-warning-filled" />
      </div>
      <p
        v-if="description"
        class="confirm-dialog__desc"
      >
        {{ description }}
      </p>
      <slot />
    </div>

    <template #footer>
      <ElButton
        :disabled="loading"
        @click="handleCancel"
      >
        {{ cancelText ?? t('common.cancel') }}
      </ElButton>
      <ElButton
        :type="destructive ? 'danger' : 'primary'"
        :loading="loading"
        :disabled="disabled"
        @click="handleConfirm"
      >
        {{ confirmText ?? t('common.continue') }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped lang="scss">
.confirm-dialog {
  &__body {
    display: flex;
    gap: var(--ys-spacing-3);
    align-items: flex-start;
  }

  &__icon {
    display: flex;
    flex-shrink: 0;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    font-size: var(--ys-font-size-xl);
    border-radius: 50%;

    &--danger {
      color: var(--el-color-danger);
      background: var(--el-color-danger-light-9);
    }
  }

  &__desc {
    margin: 0;
    font-size: var(--el-font-size-base);
    line-height: 1.6;
    color: var(--el-text-color-regular);
  }
}
</style>
