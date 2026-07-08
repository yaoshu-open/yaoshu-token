<script setup lang="ts">
// 职责：从 query 取 email + token，调后端 reset API → 返回新密码 → 提供复制按钮

import { computed, onMounted, ref } from 'vue'
import { ElAlert, ElButton, ElInput, ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { resetPassword } from '@/api/auth'
import { useCountdown } from '@/composables/useCountdown'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const email = computed(() => {
  const v = route.query.email
  return typeof v === 'string' ? v : ''
})
const token = computed(() => {
  const v = route.query.token
  return typeof v === 'string' ? v : ''
})

const newPassword = ref<string>('')
const loading = ref<boolean>(false)
const copied = ref<boolean>(false)
const { secondsLeft, isActive, start: startCountdown } = useCountdown({
  initialSeconds: 30
})

const isValidResetLink = computed(() => Boolean(email.value && token.value))

onMounted(() => {
  if (!isValidResetLink.value) {
    ElMessage.error(t('auth.resetPassword.invalidLink'))
  }
})

async function handleSubmit(): Promise<void> {
  if (!isValidResetLink.value || !email.value || !token.value) {
    ElMessage.error(t('auth.resetPassword.invalidLink'))
    return
  }

  startCountdown()
  loading.value = true
  try {
    const pwd = await resetPassword({ email: email.value, token: token.value })
    newPassword.value = pwd
    // 自动复制到剪贴板
    try {
      await navigator.clipboard.writeText(pwd)
      ElMessage.success(t('auth.resetPassword.passwordCopied'))
    } catch {
      ElMessage.success(t('auth.resetPassword.success'))
    }
  } catch {
    // 错误由 request 拦截器处理
  } finally {
    loading.value = false
  }
}

async function handleCopy(): Promise<void> {
  if (!newPassword.value) return
  try {
    await navigator.clipboard.writeText(newPassword.value)
    copied.value = true
    ElMessage.success(t('auth.resetPassword.passwordCopied'))
    setTimeout(() => {
      copied.value = false
    }, 2000)
  } catch {
    ElMessage.error(t('common.copyFailed'))
  }
}

function backToLogin(): void {
  router.replace('/sign-in')
}
</script>

<template>
  <div class="reset-password-view">
    <div class="reset-password-view__header">
      <h2 class="reset-password-view__title">
        {{ t('auth.resetPassword.title') }}
      </h2>
      <p class="reset-password-view__description">
        {{
          newPassword
            ? t('auth.resetPassword.successDescription')
            : t('auth.resetPassword.description')
        }}
      </p>
    </div>

    <ElAlert
      v-if="!isValidResetLink"
      :title="t('auth.resetPassword.invalidLink')"
      type="error"
      :closable="false"
      class="reset-password-view__alert"
    />

    <div class="reset-password-view__field">
      <label class="reset-password-view__label">
        {{ t('auth.resetPassword.emailLabel') }}
      </label>
      <ElInput
        :model-value="email"
        type="email"
        disabled
        size="large"
        :placeholder="t('auth.resetPassword.emailPlaceholder')"
      />
    </div>

    <div
      v-if="newPassword"
      class="reset-password-view__new-password"
    >
      <label class="reset-password-view__label">
        {{ t('auth.resetPassword.newPasswordLabel') }}
      </label>
      <div class="reset-password-view__new-password-row">
        <ElInput
          :model-value="newPassword"
          disabled
          size="large"
          class="reset-password-view__new-password-input"
        />
        <ElButton
          size="large"
          @click="handleCopy"
        >
          <i
            class="i-ep-document-copy"
            :class="{ 'i-ep-check': copied }"
          />
        </ElButton>
      </div>
      <p class="reset-password-view__hint">
        {{ t('auth.resetPassword.copiedHint') }}
      </p>
    </div>

    <ElButton
      type="primary"
      size="large"
      :loading="loading"
      :disabled="!isValidResetLink || isActive"
      class="reset-password-view__submit"
      @click="newPassword ? backToLogin() : handleSubmit()"
    >
      {{
        newPassword
          ? t('auth.resetPassword.backToLogin')
          : isActive
            ? t('auth.resetPassword.retry', { seconds: secondsLeft })
            : t('auth.resetPassword.confirm')
      }}
    </ElButton>

    <ElButton
      v-if="!newPassword"
      link
      type="primary"
      class="reset-password-view__back"
      @click="backToLogin"
    >
      {{ t('auth.otp.backToLogin') }}
    </ElButton>
  </div>
</template>

<style scoped lang="scss">
.reset-password-view {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-4);
  width: 100%;

  &__header {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-2);
  }

  &__title {
    margin: 0;
    font-size: var(--ys-font-size-2xl);
    font-weight: 600;
    text-align: center;
    letter-spacing: -0.025em;

    @media (width >= 640px) {
      text-align: left;
    }
  }

  &__description {
    margin: 0;
    font-size: var(--ys-font-size-base);
    color: var(--el-text-color-secondary);
    text-align: center;

    @media (width >= 640px) {
      text-align: left;
    }
  }

  &__alert {
    margin-bottom: var(--ys-spacing-2);
  }

  &__field,
  &__new-password {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  &__label {
    font-size: var(--ys-font-size-sm);
    color: var(--el-text-color-primary);
  }

  &__new-password-row {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
  }

  &__new-password-input {
    :deep(.el-input__inner) {
      font-family: monospace;
    }
  }

  &__hint {
    margin: 0;
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-secondary);
  }

  &__submit {
    width: 100%;
  }

  &__back {
    width: 100%;
  }
}
</style>
