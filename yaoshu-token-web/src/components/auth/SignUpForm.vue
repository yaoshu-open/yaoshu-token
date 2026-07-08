<script setup lang="ts">
// 职责：用户名+密码+确认密码+邮箱（可选）+邮箱验证码（email_verification=true 时）+ Turnstile + 法律同意

import { computed, onMounted, reactive, ref } from 'vue'
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  FormInstance,
  FormRules
} from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { useStatus } from '@/composables/useStatus'
import { register } from '@/api/auth'
import { getAffiliateCode, saveAffiliateCode } from '@/utils/auth-storage'
import { useTurnstile } from '@/composables/auth/useTurnstile'
import { useEmailVerification } from '@/composables/auth/useEmailVerification'
import { useAuthRedirect } from '@/composables/auth/useAuthRedirect'
import LegalConsent from './LegalConsent.vue'
import OAuthProviders from './OAuthProviders.vue'
import Turnstile from './Turnstile.vue'

interface Emits {
  (e: 'success'): void
}

const emit = defineEmits<Emits>()

const { t } = useI18n()
const route = useRoute()
const { status } = useStatus()
const { redirectToLogin } = useAuthRedirect()
const {
  isTurnstileEnabled,
  turnstileSiteKey,
  turnstileToken,
  setTurnstileToken,
  validateTurnstile
} = useTurnstile()
const {
  isSending: isSendingCode,
  secondsLeft,
  isActive,
  sendCode
} = useEmailVerification({
  turnstileToken: turnstileToken.value,
  validateTurnstile
})

const formRef = ref<FormInstance>()
const isLoading = ref<boolean>(false)
const verificationCode = ref<string>('')
const agreedToLegal = ref<boolean>(false)

const formData = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  email: ''
})

// 确认密码自定义校验器
const validateConfirmPassword = (
  _rule: unknown,
  value: string,
  callback: (error?: Error) => void
) => {
  if (value !== formData.password) {
    callback(new Error(t('auth.signUp.passwordMismatch')))
    return
  }
  callback()
}

const rules = computed<FormRules>(() => ({
  username: [
    {
      required: true,
      message: t('auth.signUp.usernameRequired'),
      trigger: 'blur'
    }
  ],
  password: [
    { required: true, message: t('auth.signUp.passwordRequired'), trigger: 'blur' },
    {
      min: 8,
      max: 20,
      message: t('auth.signUp.passwordLength'),
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    {
      required: true,
      message: t('auth.signUp.confirmPasswordRequired'),
      trigger: 'blur'
    },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ],
  email: [
    {
      type: 'email',
      message: t('auth.signUp.emailInvalid'),
      trigger: 'blur'
    }
  ]
}))

const emailVerificationRequired = computed(
  () => Boolean(status.value?.emailVerification) === true
)
const hasUserAgreement = computed(
  () => Boolean(status.value?.userAgreementEnabled) === true
)
const hasPrivacyPolicy = computed(
  () => Boolean(status.value?.privacyPolicyEnabled) === true
)
const requiresLegalConsent = computed(
  () => hasUserAgreement.value || hasPrivacyPolicy.value
)
const oauthRegisterEnabled = computed(
  () => (status.value?.oauthRegisterEnabled ?? true) !== false
)
const turnstileReady = computed(
  () => !isTurnstileEnabled.value || Boolean(turnstileToken.value)
)

onMounted(() => {
  agreedToLegal.value = !requiresLegalConsent.value

  // URL 内 ?aff=xxx 邀请码：存 localStorage，注册时一起提交
  const aff = route.query.aff
  if (typeof aff === 'string' && aff.trim()) {
    saveAffiliateCode(aff.trim())
  }
})

async function handleSubmit(): Promise<void> {
  if (!formRef.value) return

  if (requiresLegalConsent.value && !agreedToLegal.value) return

  // 邮箱必填场景的额外校验
  if (emailVerificationRequired.value) {
    if (!formData.email) return
    if (!verificationCode.value) return
  }

  if (!validateTurnstile()) return

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  isLoading.value = true
  try {
    await register({
      username: formData.username,
      password: formData.password,
      email: formData.email || undefined,
      verificationCode: verificationCode.value || undefined,
      affCode: getAffiliateCode(),
      turnstile: turnstileToken.value
    })
    emit('success')
    redirectToLogin()
  } catch {
    // 错误由 request 拦截器处理
  } finally {
    isLoading.value = false
  }
}

async function handleSendVerificationCode(): Promise<void> {
  await sendCode(formData.email || '')
}
</script>

<template>
  <div class="sign-up-form">
    <ElForm
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-position="top"
      class="sign-up-form__form"
      @submit.prevent="handleSubmit"
    >
      <ElFormItem
        :label="t('auth.signUp.usernameLabel')"
        prop="username"
      >
        <ElInput
          v-model="formData.username"
          :placeholder="t('auth.signUp.usernamePlaceholder')"
          size="large"
        />
      </ElFormItem>

      <ElFormItem
        :label="t('auth.signUp.passwordLabel')"
        prop="password"
      >
        <ElInput
          v-model="formData.password"
          type="password"
          show-password
          :placeholder="t('auth.signUp.passwordPlaceholder')"
          size="large"
        />
      </ElFormItem>

      <ElFormItem
        :label="t('auth.signUp.confirmPasswordLabel')"
        prop="confirmPassword"
      >
        <ElInput
          v-model="formData.confirmPassword"
          type="password"
          show-password
          :placeholder="t('auth.signUp.confirmPasswordPlaceholder')"
          size="large"
        />
      </ElFormItem>

      <!-- 邮箱 + 验证码（email_verification=true 时） -->
      <template v-if="emailVerificationRequired">
        <ElFormItem
          :label="t('auth.signUp.emailLabel')"
          prop="email"
        >
          <ElInput
            v-model="formData.email"
            type="email"
            :placeholder="t('auth.signUp.emailPlaceholder')"
            size="large"
          />
        </ElFormItem>

        <ElFormItem class="sign-up-form__verification">
          <ElInput
            v-model="verificationCode"
            :placeholder="t('auth.signUp.verificationCodePlaceholder')"
            size="large"
          />
          <ElButton
            type="default"
            size="large"
            :disabled="
              isLoading ||
                isSendingCode ||
                isActive ||
                !formData.email ||
                !turnstileReady
            "
            @click="handleSendVerificationCode"
          >
            {{
              isActive
                ? t('auth.signUp.resendIn', { seconds: secondsLeft })
                : t('auth.signUp.sendCode')
            }}
          </ElButton>
        </ElFormItem>
      </template>

      <div
        v-if="isTurnstileEnabled"
        class="sign-up-form__turnstile"
      >
        <Turnstile
          :site-key="turnstileSiteKey"
          @verify="setTurnstileToken"
        />
      </div>

      <LegalConsent
        v-model:checked="agreedToLegal"
        class="sign-up-form__legal"
      />

      <ElButton
        type="primary"
        size="large"
        :loading="isLoading"
        :disabled="
          (requiresLegalConsent && !agreedToLegal) || !turnstileReady
        "
        class="sign-up-form__submit el-button--brand"
        native-type="submit"
      >
        {{ t('auth.signUp.submit') }}
      </ElButton>

      <OAuthProviders
        v-if="oauthRegisterEnabled"
        :disabled="isLoading || (requiresLegalConsent && !agreedToLegal)"
        class="sign-up-form__oauth"
      />
    </ElForm>
  </div>
</template>

<style scoped lang="scss">
.sign-up-form {
  &__form {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-4);
  }

  &__verification {
    :deep(.el-form-item__content) {
      display: flex;
      gap: var(--ys-spacing-2);
      align-items: center;
    }

    :deep(.el-input) {
      flex: 1;
    }
  }

  &__turnstile {
    margin-top: 8px;
  }

  &__legal {
    margin-top: 4px;
  }

  &__submit {
    width: 100%;
    margin-top: 8px;
  }

  &__oauth {
    margin-top: 8px;
  }
}
</style>
