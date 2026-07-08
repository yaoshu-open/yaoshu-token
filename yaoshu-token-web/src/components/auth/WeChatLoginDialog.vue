<script setup lang="ts">
// 职责：二维码展示 + 验证码输入 + 提交，emit('confirm', code) 由父组件调 wechatLoginByCode API

import { ref, watch } from 'vue'
import { ElDialog, ElInput, ElButton } from 'element-plus'
import { useI18n } from 'vue-i18n'

interface Props {
  modelValue: boolean
  qrCodeUrl: string
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm', code: string): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const { t } = useI18n()
const wechatCode = ref<string>('')

// dialog 关闭时重置验证码输入
watch(
  () => props.modelValue,
  (open) => {
    if (!open) {
      wechatCode.value = ''
    }
  }
)

function handleClose(): void {
  emit('update:modelValue', false)
}

function handleConfirm(): void {
  if (!wechatCode.value.trim()) return
  emit('confirm', wechatCode.value.trim())
}
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    :title="t('auth.wechat.dialogTitle')"
    width="400px"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="wechat-login-dialog">
      <p class="wechat-login-dialog__hint">
        {{ t('auth.wechat.dialogHint') }}
      </p>

      <div
        v-if="qrCodeUrl"
        class="wechat-login-dialog__qr"
      >
        <img
          :src="qrCodeUrl"
          :alt="t('auth.wechat.qrAlt')"
          class="wechat-login-dialog__qr-img"
        >
      </div>
      <p
        v-else
        class="wechat-login-dialog__qr-missing"
      >
        {{ t('auth.wechat.qrNotConfigured') }}
      </p>

      <div class="wechat-login-dialog__input">
        <label class="wechat-login-dialog__label">
          {{ t('auth.wechat.verificationCode') }}
        </label>
        <ElInput
          v-model="wechatCode"
          :placeholder="t('auth.wechat.verificationCodePlaceholder')"
          autocomplete="one-time-code"
        />
      </div>
    </div>

    <template #footer>
      <ElButton @click="handleClose">
        {{ t('common.cancel') }}
      </ElButton>
      <ElButton
        type="primary"
        :disabled="!wechatCode.trim()"
        @click="handleConfirm"
      >
        {{ t('common.confirm') }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped lang="scss">
.wechat-login-dialog {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-4);

  &__hint {
    margin: 0;
    font-size: var(--ys-font-size-sm);
    line-height: 1.6;
    color: var(--el-text-color-secondary);
  }

  &__qr {
    display: flex;
    justify-content: center;
  }

  &__qr-img {
    width: 160px;
    height: 160px;
    object-fit: contain;
    border: 1px solid var(--el-border-color);
    border-radius: var(--el-border-radius-base);
  }

  &__qr-missing {
    padding: var(--ys-spacing-8);
    margin: 0;
    font-size: var(--ys-font-size-sm);
    color: var(--el-text-color-secondary);
    text-align: center;
    background: var(--el-fill-color-light);
    border-radius: var(--el-border-radius-base);
  }

  &__input {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  &__label {
    font-size: var(--ys-font-size-sm);
    color: var(--el-text-color-primary);
  }
}
</style>
