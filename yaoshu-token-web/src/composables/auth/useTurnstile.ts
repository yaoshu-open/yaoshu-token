// 职责：从 useStatus 读 turnstileCheck/turnstileSiteKey，暴露 token 状态 + 校验方法
// 被消费方：UserAuthForm/SignUpForm/ForgotPasswordForm（任何需要人机校验的表单）

import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useStatus } from '@/composables/useStatus'

export function useTurnstile() {
  const { t } = useI18n()
  const { status } = useStatus()
  const turnstileToken = ref<string>('')

  // status 经拦截器解包 Result.data 后为扁平结构，直接读取 turnstile* 字段（camelCase）
  // 用 computed 让 isTurnstileEnabled / turnstileSiteKey 跟随 status 变化（首次拉取完成后自动更新）
  const isTurnstileEnabled = computed(() => {
    const check = status.value?.turnstileCheck ?? false
    const siteKey = status.value?.turnstileSiteKey ?? ''
    return Boolean(check && siteKey)
  })
  const turnstileSiteKey = computed(
    () => status.value?.turnstileSiteKey ?? ''
  )

  // 校验 Turnstile 是否就绪：启用但未通过时弹提示
  function validateTurnstile(): boolean {
    if (isTurnstileEnabled.value && !turnstileToken.value) {
      ElMessage.info(t('auth.turnstile.validating'))
      return false
    }
    return true
  }

  function setTurnstileToken(token: string): void {
    turnstileToken.value = token
  }

  return {
    isTurnstileEnabled,
    turnstileSiteKey,
    turnstileToken,
    setTurnstileToken,
    validateTurnstile
  }
}
