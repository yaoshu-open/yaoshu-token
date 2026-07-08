<script setup lang="ts">
// 职责：登录二次验证（6 位 OTP / 8 位备用码切换）+ 验证成功跳首页

import { computed, nextTick, onMounted, ref } from 'vue'
import { ElButton, ElInput, ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { login2fa } from '@/api/auth'
import { useAuthStore } from '@/store/modules/auth'
import { useAuthRedirect } from '@/composables/auth/useAuthRedirect'
import { saveUserId } from '@/utils/auth-storage'
import {
  cleanBackupCode,
  formatBackupCode,
  isValidBackupCode,
  isValidOTP
} from '@/utils/validation'

const OTP_LENGTH = 6
const BACKUP_CODE_LENGTH = 9

interface Emits {
  (e: 'success'): void
  (e: 'back-to-login'): void
}

const emit = defineEmits<Emits>()

const { t } = useI18n()
const authStore = useAuthStore()
const { redirectToLogin } = useAuthRedirect()

const isLoading = ref<boolean>(false)
const useBackupCode = ref<boolean>(false)
const code = ref<string>('')

// 6 位 OTP 的分格输入 refs
const otpInputs = ref<Array<InstanceType<typeof ElInput> | null>>([])

const isFormValid = computed(() => {
  if (useBackupCode.value) {
    return code.value.length >= BACKUP_CODE_LENGTH
  }
  return code.value.length >= OTP_LENGTH
})

onMounted(() => {
  // 默认聚焦第一格 OTP 输入
  nextTick(() => {
    otpInputs.value[0]?.focus?.()
  })
})

// 6 位 OTP 输入：单格输入后自动跳到下一格
function handleOtpInput(index: number, value: string): void {
  const digit = value.replace(/\D/g, '').slice(-1)
  // 把当前格更新为单数字
  const codes = code.value.padEnd(OTP_LENGTH, ' ').split('')
  codes[index] = digit || ' '
  code.value = codes.join('').replace(/\s/g, '')

  // 自动跳到下一格
  if (digit && index < OTP_LENGTH - 1) {
    nextTick(() => {
      otpInputs.value[index + 1]?.focus?.()
    })
  }
}

// 单格删除：空时回退到上一格
function handleOtpKeydown(index: number, event: KeyboardEvent): void {
  if (event.key === 'Backspace') {
    const codes = code.value.padEnd(OTP_LENGTH, ' ').split('')
    if (!codes[index] || codes[index] === ' ') {
      event.preventDefault()
      if (index > 0) {
        nextTick(() => {
          otpInputs.value[index - 1]?.focus?.()
        })
      }
    }
  }
}

// 粘贴 6 位 OTP 时分发到各格
function handleOtpPaste(event: ClipboardEvent): void {
  const pasted = event.clipboardData?.getData('text') ?? ''
  const digits = pasted.replace(/\D/g, '').slice(0, OTP_LENGTH)
  if (digits.length > 0) {
    event.preventDefault()
    code.value = digits.padEnd(OTP_LENGTH, ' ').slice(0, OTP_LENGTH).replace(/\s/g, '')
    nextTick(() => {
      otpInputs.value[Math.min(digits.length, OTP_LENGTH - 1)]?.focus?.()
    })
  }
}

// 备用码输入：自动格式化为 XXXX-XXXX
function handleBackupCodeInput(value: string): void {
  code.value = formatBackupCode(value)
}

async function handleSubmit(): Promise<void> {
  // 模式相关校验
  if (useBackupCode.value) {
    if (!isValidBackupCode(code.value)) {
      ElMessage.error(t('auth.otp.backupCodeInvalid'))
      return
    }
  } else {
    if (!isValidOTP(code.value)) {
      ElMessage.error(t('auth.otp.otpInvalid'))
      return
    }
  }

  isLoading.value = true
  try {
    // 备用码发送前去掉连字符
    const finalCode = useBackupCode.value ? cleanBackupCode(code.value) : code.value
    const userData = await login2fa({ code: finalCode })

    // 写 uid + 触发 fetchUserInfo
    if (userData?.id) {
      saveUserId(userData.id)
    }
    // userInfo 写入 authStore（login2fa 已返回完整 UserInfo）
    authStore.setAuthToken(`2fa-session-${userData.id}`) // ⚠️ 待联调：2FA 登录后端是否下发 token
    // 调 store.fetchUserInfo 拉取最新（login2fa 已返回 UserInfo，可跳过二次请求）
    // 此处直接用 login2fa 返回值，避免重复请求
    emit('success')
    redirectToLogin() // 实际跳转目标由守卫决定（已登录则进首页）
  } catch (error) {
    console.error('[OTP] Verification error:', error)
    // 错误已由 request 拦截器弹错
  } finally {
    isLoading.value = false
  }
}

function handleToggleMode(): void {
  useBackupCode.value = !useBackupCode.value
  code.value = ''
}

function handleBackToLogin(): void {
  emit('back-to-login')
  redirectToLogin()
}
</script>

<template>
  <form
    class="otp-form"
    @submit.prevent="handleSubmit"
  >
    <div class="otp-form__field">
      <label class="otp-form__label">
        {{
          useBackupCode
            ? t('auth.otp.backupCodeLabel')
            : t('auth.otp.otpLabel')
        }}
      </label>

      <!-- 备用码模式：单 input -->
      <ElInput
        v-if="useBackupCode"
        :model-value="code"
        :placeholder="t('auth.otp.backupCodePlaceholder')"
        :maxlength="BACKUP_CODE_LENGTH"
        size="large"
        autocomplete="off"
        class="otp-form__backup-input"
        @update:model-value="handleBackupCodeInput"
      />

      <!-- 6 位 OTP 模式：分格输入 -->
      <div
        v-else
        class="otp-form__slots"
        @paste="handleOtpPaste"
      >
        <ElInput
          v-for="i in OTP_LENGTH"
          :key="i"
          :ref="(el) => { otpInputs[i - 1] = el as InstanceType<typeof ElInput> | null }"
          :model-value="code[i - 1] || ''"
          :maxlength="1"
          size="large"
          inputmode="numeric"
          class="otp-form__slot"
          @update:model-value="(val: string) => handleOtpInput(i - 1, val)"
          @keydown="handleOtpKeydown(i - 1, $event as KeyboardEvent)"
        />
      </div>

      <p class="otp-form__hint">
        {{
          useBackupCode
            ? t('auth.otp.backupCodeHint')
            : t('auth.otp.otpHint')
        }}
      </p>
    </div>

    <ElButton
      type="primary"
      size="large"
      :loading="isLoading"
      :disabled="!isFormValid"
      class="otp-form__submit"
      native-type="submit"
    >
      {{ t('auth.otp.submit') }}
    </ElButton>

    <div class="otp-form__actions">
      <ElButton
        link
        type="primary"
        @click="handleToggleMode"
      >
        {{
          useBackupCode
            ? t('auth.otp.useOtp')
            : t('auth.otp.useBackupCode')
        }}
      </ElButton>
      <span class="otp-form__separator">·</span>
      <ElButton
        link
        type="primary"
        @click="handleBackToLogin"
      >
        {{ t('auth.otp.backToLogin') }}
      </ElButton>
    </div>
  </form>
</template>

<style scoped lang="scss">
.otp-form {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-4);

  &__field {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  &__label {
    font-size: var(--ys-font-size-sm);
    color: var(--el-text-color-primary);
  }

  &__slots {
    display: flex;
    gap: var(--ys-spacing-2);
    justify-content: space-between;
  }

  &__slot {
    flex-shrink: 0;
    width: 48px;

    :deep(.el-input__inner) {
      padding: 0;
      font-size: var(--ys-font-size-xl);
      font-weight: 600;
      text-align: center;
    }
  }

  &__backup-input {
    :deep(.el-input__inner) {
      font-family: monospace;
      font-size: var(--ys-font-size-lg);
      text-transform: uppercase;
      letter-spacing: 2px;
    }
  }

  &__hint {
    margin: 0;
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-secondary);
  }

  &__submit {
    width: 100%;
    margin-top: 8px;
  }

  &__actions {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
    justify-content: center;
    font-size: var(--ys-font-size-base);
  }

  &__separator {
    color: var(--el-text-color-secondary);
  }
}
</style>
