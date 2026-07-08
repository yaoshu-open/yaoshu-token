<script setup lang="ts">
// 职责：tabs 切换 2FA 验证码输入 + Passkey 触发；methods 为空时引导用户去 profile 启用
// 纯展示组件，所有状态由父组件通过 props 注入，事件 emit 给父组件

import { computed } from 'vue'
import { ElButton, ElDialog, ElInput, ElTabs, ElTabPane, ElEmpty } from 'element-plus'
import { useI18n } from 'vue-i18n'
import type {
  SecureVerificationState,
  VerificationMethod,
  VerificationMethods
} from '@/api/auth-secure/types'

interface Props {
  modelValue: boolean
  methods: VerificationMethods
  state: SecureVerificationState
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'verify', method: VerificationMethod, code?: string): void
  (e: 'cancel'): void
  (e: 'code-change', code: string): void
  (e: 'method-change', method: VerificationMethod): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const { t } = useI18n()

const availableTabs = computed<VerificationMethod[]>(() => {
  const tabs: VerificationMethod[] = []
  if (props.methods.has2FA) tabs.push('2fa')
  if (props.methods.hasPasskey && props.methods.passkeySupported) {
    tabs.push('passkey')
  }
  return tabs
})

const activeMethod = computed<VerificationMethod | null>(
  () => props.state.method ?? (availableTabs.value[0] ?? null)
)

const title = computed(() => {
  if (props.state.title) return props.state.title
  return availableTabs.value.length > 0
    ? t('auth.secureVerification.title')
    : t('auth.secureVerification.unavailableTitle')
})

const description = computed(() => {
  if (props.state.description) return props.state.description
  return availableTabs.value.length > 0
    ? t('auth.secureVerification.description')
    : t('auth.secureVerification.unavailableDescription')
})

const verifyDisabled = computed(() => {
  if (props.state.loading) return true
  if (!activeMethod.value) return true
  if (activeMethod.value === '2fa') {
    return !props.state.code.trim() || props.state.code.length < 6
  }
  return false
})

function handleVerify(): void {
  if (!activeMethod.value) return
  const payload =
    activeMethod.value === '2fa' ? props.state.code : undefined
  emit('verify', activeMethod.value, payload)
}

function handleMethodChange(method: string | number): void {
  emit('method-change', String(method) as VerificationMethod)
}

function handleCodeInput(value: string): void {
  emit('code-change', value)
}

function handleEnter(): void {
  if (!verifyDisabled.value) {
    handleVerify()
  }
}
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    :title="title"
    width="480px"
    append-to-body
    :show-close="!state.loading"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <p class="secure-verification-dialog__description">
      {{ description }}
    </p>

    <div
      v-if="availableTabs.length === 0"
      class="secure-verification-dialog__empty"
    >
      <ElEmpty
        :description="t('auth.secureVerification.emptyHint')"
        :image-size="64"
      />
    </div>

    <ElTabs
      v-else
      :model-value="activeMethod ?? undefined"
      @tab-change="handleMethodChange"
    >
      <ElTabPane
        v-if="methods.has2FA"
        name="2fa"
        :label="t('auth.secureVerification.authenticatorCode')"
      >
        <p class="secure-verification-dialog__hint">
          {{ t('auth.secureVerification.codeHint') }}
        </p>
        <ElInput
          :model-value="state.code"
          :placeholder="t('auth.secureVerification.codePlaceholder')"
          :disabled="state.loading"
          inputmode="numeric"
          maxlength="8"
          @update:model-value="handleCodeInput"
          @keydown.enter="handleEnter"
        />
      </ElTabPane>

      <ElTabPane
        v-if="methods.hasPasskey && methods.passkeySupported"
        name="passkey"
        :label="t('auth.secureVerification.passkey')"
      >
        <div class="secure-verification-dialog__passkey">
          <i class="i-ep-key secure-verification-dialog__passkey-icon" />
          <div class="secure-verification-dialog__passkey-text">
            <p class="secure-verification-dialog__passkey-title">
              {{ t('auth.secureVerification.passkeyTitle') }}
            </p>
            <p class="secure-verification-dialog__passkey-hint">
              {{ t('auth.secureVerification.passkeyHint') }}
            </p>
          </div>
        </div>
        <p
          v-if="!methods.passkeySupported"
          class="secure-verification-dialog__passkey-unsupported"
        >
          {{ t('auth.passkey.notSupported') }}
        </p>
      </ElTabPane>
    </ElTabs>

    <template #footer>
      <ElButton
        :disabled="state.loading"
        @click="emit('cancel')"
      >
        {{ t('common.cancel') }}
      </ElButton>
      <ElButton
        type="primary"
        :loading="state.loading"
        :disabled="availableTabs.length === 0 || verifyDisabled"
        @click="handleVerify"
      >
        {{ t('auth.secureVerification.verify') }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped lang="scss">
.secure-verification-dialog {
  &__description {
    margin: 0 0 var(--ys-spacing-4);
    font-size: var(--ys-font-size-base);
    color: var(--el-text-color-secondary);
    text-align: left;
  }

  &__empty {
    padding: var(--ys-spacing-6) 0;
  }

  &__hint {
    margin: 0 0 var(--ys-spacing-3);
    font-size: var(--ys-font-size-sm);
    color: var(--el-text-color-secondary);
  }

  &__passkey {
    display: flex;
    gap: var(--ys-spacing-3);
    align-items: center;
    justify-content: center;
    padding: var(--ys-spacing-4);
    background: var(--el-fill-color-light);
    border-radius: var(--el-border-radius-base);
  }

  &__passkey-icon {
    flex-shrink: 0;
    width: 24px;
    height: 24px;
    font-size: var(--ys-font-size-2xl);
    color: var(--el-color-primary);
  }

  &__passkey-text {
    text-align: left;
  }

  &__passkey-title {
    margin: 0 0 var(--ys-spacing-1);
    font-size: var(--ys-font-size-sm);
    font-weight: 500;
    color: var(--el-text-color-primary);
  }

  &__passkey-hint {
    margin: 0;
    font-size: var(--ys-font-size-sm);
    color: var(--el-text-color-secondary);
  }

  &__passkey-unsupported {
    margin: var(--ys-spacing-3) 0 0;
    font-size: var(--ys-font-size-sm);
    color: var(--el-color-danger);
  }
}
</style>
