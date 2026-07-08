<script setup lang="ts">
// 职责：用户名+密码登录 + Passkey 登录 + OAuth 入口 + 微信扫码 + Turnstile + 法律同意

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
import { useStatus } from '@/composables/useStatus'
import { useAuthStore } from '@/store/modules/auth'
import { wechatLoginByCode } from '@/api/auth'
import { beginPasskeyLogin, finishPasskeyLogin } from '@/api/auth-passkey'
import {
  buildAssertionResult,
  isPasskeySupported,
  prepareCredentialRequestOptions
} from '@/utils/passkey'
import { useTurnstile } from '@/composables/auth/useTurnstile'
import { useAuthRedirect } from '@/composables/auth/useAuthRedirect'
import { isFeatureHidden } from '@/plugins/spi/registry'
import LegalConsent from './LegalConsent.vue'
import OAuthProviders from './OAuthProviders.vue'
import Turnstile from './Turnstile.vue'
import WeChatLoginDialog from './WeChatLoginDialog.vue'

interface Props {
  redirectTo?: string
}

interface Emits {
  (e: 'success', payload: { require2fa: boolean }): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const { t } = useI18n()
const { status } = useStatus()
const authStore = useAuthStore()
const { handleLoginSuccess, redirectTo2FA } = useAuthRedirect()
const {
  isTurnstileEnabled,
  turnstileSiteKey,
  turnstileToken,
  setTurnstileToken,
  validateTurnstile
} = useTurnstile()

const formRef = ref<FormInstance>()
const isLoading = ref<boolean>(false)
const isPasskeyLoading = ref<boolean>(false)
const isWeChatDialogOpen = ref<boolean>(false)
const isWeChatSubmitting = ref<boolean>(false)
const agreedToLegal = ref<boolean>(false)
const passkeySupported = ref<boolean>(false)

const formData = reactive({
  username: '',
  password: ''
})

const rules = computed<FormRules>(() => ({
  username: [
    {
      required: true,
      message: t('auth.signIn.usernameRequired'),
      trigger: 'blur'
    }
  ],
  password: [
    { required: true, message: t('auth.signIn.passwordRequired'), trigger: 'blur' },
    {
      min: 8,
      message: t('auth.signIn.passwordMinLength'),
      trigger: 'blur'
    }
  ]
}))

// 各种登录方式开关（从 status 派生）
// SPI 功能开关：商业版后端未实现 Passkey 时隐藏登录页 Passkey 按钮
const passkeyHidden = isFeatureHidden('passkey')
const passkeyLoginEnabled = computed(
  () => !passkeyHidden && Boolean(status.value?.passkeyLogin) === true
)
const passwordLoginEnabled = computed(
  () => (status.value?.passwordLoginEnabled ?? true) !== false
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
const hasWeChatLogin = computed(() => Boolean(status.value?.wechatLogin))
const hasOAuthLogin = computed(
  () =>
    Boolean(status.value?.githubOauth) ||
    Boolean(status.value?.discordOauth) ||
    Boolean(status.value?.oidcEnabled) ||
    Boolean(status.value?.linuxdoOauth) ||
    Boolean(status.value?.telegramOauth) ||
    (status.value?.customOauthProviders?.length ?? 0) > 0
)
const hasAlternativeLogin = computed(
  () => passkeyLoginEnabled.value || hasWeChatLogin.value || hasOAuthLogin.value
)

const passkeyButtonDisabled = computed(
  () =>
    isPasskeyLoading.value ||
    !passkeySupported.value ||
    (requiresLegalConsent.value && !agreedToLegal.value)
)

onMounted(() => {
  agreedToLegal.value = !requiresLegalConsent.value
  isPasskeySupported()
    .then((s) => {
      passkeySupported.value = s
    })
    .catch(() => {
      passkeySupported.value = false
    })
})

// 用户名密码登录
async function handleSubmit(): Promise<void> {
  if (!formRef.value) return

  if (requiresLegalConsent.value && !agreedToLegal.value) {
    return
  }

  if (!validateTurnstile()) return

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  isLoading.value = true
  try {
    const result = await authStore.login({
      username: formData.username,
      password: formData.password,
      turnstile: turnstileToken.value
    })

    if (result.require2fa) {
      redirectTo2FA()
      emit('success', { require2fa: true })
      return
    }

    await handleLoginSuccess(null, props.redirectTo)
    emit('success', { require2fa: false })
  } catch {
    // 错误已由 request 拦截器弹错
  } finally {
    isLoading.value = false
  }
}

// Passkey 登录：begin → navigator.credentials.get → finish → handleLoginSuccess
async function handlePasskeyLogin(): Promise<void> {
  if (requiresLegalConsent.value && !agreedToLegal.value) return
  if (!passkeySupported.value || !navigator?.credentials) return

  isPasskeyLoading.value = true
  try {
    const beginResponse = await beginPasskeyLogin()
    const publicKey = prepareCredentialRequestOptions(beginResponse)
    const credential = (await navigator.credentials.get({
      publicKey
    })) as PublicKeyCredential | null

    if (!credential) return

    const assertion = buildAssertionResult(credential)
    if (!assertion) throw new Error(t('auth.passkey.invalidResponse'))

    const userData = (await finishPasskeyLogin(assertion)) as {
      id?: number
    } | null
    await handleLoginSuccess(userData, props.redirectTo)
    emit('success', { require2fa: false })
  } catch (error: unknown) {
    if (error instanceof DOMException && error.name === 'NotAllowedError') {
      // 用户取消，不提示错误
      return
    }
    console.error('[Passkey] Login error', error)
  } finally {
    isPasskeyLoading.value = false
  }
}

// 微信登录：打开 dialog → 用户输入验证码 → wechatLoginByCode
function handleOpenWeChatDialog(): void {
  if (requiresLegalConsent.value && !agreedToLegal.value) return
  isWeChatDialogOpen.value = true
}

async function handleWeChatConfirm(code: string): Promise<void> {
  isWeChatSubmitting.value = true
  try {
    const userData = (await wechatLoginByCode(code)) as { id?: number } | null
    isWeChatDialogOpen.value = false
    await handleLoginSuccess(userData, props.redirectTo)
    emit('success', { require2fa: false })
  } catch {
    // 错误由 request 拦截器处理
  } finally {
    isWeChatSubmitting.value = false
  }
}
</script>

<template>
  <div class="user-auth-form">
    <!-- 替代登录方式（Passkey + OAuth + 微信）— 密码登录可用时置于密码表单之上 -->
    <div
      v-if="hasAlternativeLogin"
      class="user-auth-form__alternative"
    >
      <ElButton
        v-if="passkeyLoginEnabled"
        type="default"
        :disabled="passkeyButtonDisabled"
        class="user-auth-form__passkey-btn"
        @click="handlePasskeyLogin"
      >
        <i
          class="i-ep-key user-auth-form__btn-icon"
          :class="{ 'i-ep-loading': isPasskeyLoading }"
        />
        {{ t('auth.passkey.signInWith') }}
      </ElButton>
      <p
        v-if="passkeyLoginEnabled && !passkeySupported"
        class="user-auth-form__passkey-hint"
      >
        {{ t('auth.passkey.notSupported') }}
      </p>

      <OAuthProviders
        :disabled="isLoading || (requiresLegalConsent && !agreedToLegal)"
        :on-we-chat-login="hasWeChatLogin ? handleOpenWeChatDialog : undefined"
        :is-we-chat-loading="isWeChatSubmitting"
      />
    </div>

    <!-- 密码登录表单 -->
    <ElForm
      v-if="passwordLoginEnabled"
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-position="top"
      class="user-auth-form__form"
      @submit.prevent="handleSubmit"
    >
      <ElFormItem
        :label="t('auth.signIn.usernameLabel')"
        prop="username"
      >
        <ElInput
          v-model="formData.username"
          :placeholder="t('auth.signIn.usernamePlaceholder')"
          size="large"
        />
      </ElFormItem>

      <ElFormItem
        :label="t('auth.signIn.passwordLabel')"
        prop="password"
      >
        <ElInput
          v-model="formData.password"
          type="password"
          show-password
          :placeholder="t('auth.signIn.passwordPlaceholder')"
          size="large"
        />
      </ElFormItem>

      <div class="user-auth-form__forgot-row">
        <RouterLink
          to="/forgot-password"
          class="user-auth-form__forgot-link"
        >
          {{ t('auth.signIn.forgotPassword') }}
        </RouterLink>
      </div>

      <ElButton
        type="primary"
        size="large"
        :loading="isLoading"
        :disabled="requiresLegalConsent && !agreedToLegal"
        class="user-auth-form__submit el-button--brand"
        native-type="submit"
      >
        {{ t('auth.signIn.submit') }}
      </ElButton>

      <div
        v-if="isTurnstileEnabled"
        class="user-auth-form__turnstile"
      >
        <Turnstile
          :site-key="turnstileSiteKey"
          @verify="setTurnstileToken"
        />
      </div>
    </ElForm>

    <LegalConsent
      v-model:checked="agreedToLegal"
      class="user-auth-form__legal"
    />

    <!-- 微信登录 dialog -->
    <WeChatLoginDialog
      v-if="hasWeChatLogin"
      v-model="isWeChatDialogOpen"
      :qr-code-url="(status?.wechatQrCodeUrl as string) || ''"
      @confirm="handleWeChatConfirm"
    />
  </div>
</template>

<style scoped lang="scss">
.user-auth-form {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-4);

  &__alternative {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-2);
  }

  &__passkey-btn {
    gap: var(--ys-spacing-2);
    justify-content: center;
    width: 100%;
    height: 44px;
  }

  &__passkey-hint {
    margin: 0;
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-secondary);
  }

  &__btn-icon {
    width: 16px;
    height: 16px;
    font-size: var(--ys-font-size-lg);
  }

  &__form {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-4);
  }

  &__forgot-row {
    display: flex;
    justify-content: flex-end;
    margin-top: calc(var(--ys-spacing-2) * -1);
  }

  &__forgot-link {
    font-size: var(--ys-font-size-sm);
    font-weight: 500;
    color: var(--el-text-color-secondary);
    text-decoration: none;

    &:hover {
      opacity: 0.75;
    }
  }

  // 浏览器 autofill 蓝色背景覆盖：强制铺满输入框，避免半截蓝色阴影
  :deep(.el-input__inner) {
    &:-webkit-autofill,
    &:-webkit-autofill:hover,
    &:-webkit-autofill:focus {
      transition: background-color 99999s ease-in-out 0s;
      -webkit-text-fill-color: var(--el-text-color-primary);
      box-shadow: 0 0 0 1000px var(--el-bg-color) inset;
    }
  }

  &__submit {
    width: 100%;
    margin-top: 8px;
  }

  &__turnstile {
    margin-top: 8px;
  }

  &__legal {
    margin-top: 4px;
  }
}
</style>
