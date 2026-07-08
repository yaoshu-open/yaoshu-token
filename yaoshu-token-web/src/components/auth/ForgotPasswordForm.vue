<script setup lang="ts">
// 职责：邮箱输入 + 发送重置邮件 + 30s 倒计时 + Turnstile

import { computed, reactive, ref } from 'vue'
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  FormInstance,
  FormRules
} from 'element-plus'
import { useI18n } from 'vue-i18n'
import { sendPasswordResetEmail } from '@/api/auth'
import { useTurnstile } from '@/composables/auth/useTurnstile'
import { useCountdown } from '@/composables/useCountdown'
import Turnstile from './Turnstile.vue'

const PASSWORD_RESET_COUNTDOWN = 30

interface Emits {
  (e: 'success'): void
}

const emit = defineEmits<Emits>()

const { t } = useI18n()
const {
  isTurnstileEnabled,
  turnstileSiteKey,
  turnstileToken,
  setTurnstileToken,
  validateTurnstile
} = useTurnstile()
const { secondsLeft, isActive, start: startCountdown } = useCountdown({
  initialSeconds: PASSWORD_RESET_COUNTDOWN
})

const formRef = ref<FormInstance>()
const isLoading = ref<boolean>(false)

const formData = reactive({
  email: ''
})

const rules = computed<FormRules>(() => ({
  email: [
    {
      required: true,
      message: t('auth.forgotPassword.emailRequired'),
      trigger: 'blur'
    },
    {
      type: 'email',
      message: t('auth.forgotPassword.emailInvalid'),
      trigger: 'blur'
    }
  ]
}))

const turnstileReady = computed(
  () => !isTurnstileEnabled.value || Boolean(turnstileToken.value)
)

async function handleSubmit(): Promise<void> {
  if (!formRef.value) return
  if (!validateTurnstile()) return

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  isLoading.value = true
  try {
    await sendPasswordResetEmail(formData.email, turnstileToken.value)
    formData.email = ''
    formRef.value.resetFields()
    startCountdown()
    emit('success')
  } catch {
    // 错误由 request 拦截器处理
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <ElForm
    ref="formRef"
    :model="formData"
    :rules="rules"
    label-position="top"
    class="forgot-password-form"
    @submit.prevent="handleSubmit"
  >
    <ElFormItem
      :label="t('auth.forgotPassword.emailLabel')"
      prop="email"
    >
      <ElInput
        v-model="formData.email"
        type="email"
        :placeholder="t('auth.forgotPassword.emailPlaceholder')"
        size="large"
      />
    </ElFormItem>

    <ElButton
      type="primary"
      size="large"
      :loading="isLoading"
      :disabled="isActive || !turnstileReady"
      class="forgot-password-form__submit"
      native-type="submit"
    >
      {{
        isActive
          ? t('auth.forgotPassword.resendIn', { seconds: secondsLeft })
          : t('auth.forgotPassword.submit')
      }}
    </ElButton>

    <div
      v-if="isTurnstileEnabled"
      class="forgot-password-form__turnstile"
    >
      <Turnstile
        :site-key="turnstileSiteKey"
        @verify="setTurnstileToken"
      />
    </div>
  </ElForm>
</template>

<style scoped lang="scss">
.forgot-password-form {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-2);

  &__submit {
    width: 100%;
    margin-top: 8px;
  }

  &__turnstile {
    margin-top: 8px;
  }
}
</style>
