<script setup lang="ts">
import { ElDialog } from 'element-plus'

withDefaults(
  defineProps<{
    modelValue: boolean
    title?: string
    description?: string
    width?: string | number
    loading?: boolean
    confirmDisabled?: boolean
    showCancel?: boolean
    showConfirm?: boolean
    showClose?: boolean
    closeOnClickModal?: boolean
    appendToBody?: boolean
  }>(),
  {
    title: undefined,
    description: undefined,
    width: '600px',
    loading: false,
    confirmDisabled: false,
    showCancel: true,
    showConfirm: true,
    showClose: true,
    closeOnClickModal: false,
    appendToBody: false,
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm'): void
  (e: 'cancel'): void
}>()

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
    :width="width"
    :show-close="showClose"
    :close-on-click-modal="closeOnClickModal"
    :append-to-body="appendToBody"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template
      v-if="$slots.header || title"
      #header
    >
      <slot name="header">
        <div class="dialog-header">
          <h3 class="dialog-header__title">
            {{ title }}
          </h3>
          <p
            v-if="description"
            class="dialog-header__desc"
          >
            {{ description }}
          </p>
        </div>
      </slot>
    </template>

    <div class="dialog-body">
      <slot />
    </div>

    <template
      v-if="showCancel || showConfirm || $slots.footer"
      #footer
    >
      <slot name="footer">
        <div class="dialog-footer">
          <el-button
            v-if="showCancel"
            @click="handleCancel"
          >
            {{ $t('common.cancel') }}
          </el-button>
          <el-button
            v-if="showConfirm"
            type="primary"
            :loading="loading"
            :disabled="confirmDisabled"
            @click="handleConfirm"
          >
            {{ $t('common.confirm') }}
          </el-button>
        </div>
      </slot>
    </template>
  </ElDialog>
</template>

<style scoped lang="scss">
.dialog-header {
  &__title {
    margin: 0;
    font-size: var(--el-font-size-large);
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  &__desc {
    margin: var(--ys-spacing-1) 0 0;
    font-size: var(--el-font-size-base);
    color: var(--el-text-color-secondary);
  }
}

.dialog-body {
  max-height: calc(100vh - 14rem);
  overflow: hidden auto;
}

.dialog-footer {
  display: flex;
  gap: var(--ys-spacing-2);
  justify-content: flex-end;
}
</style>
