// 职责：sendCode(email) 调 API + 启动倒计时；返回倒计时状态供 UI 显示「Xs 后重发」
// 被消费方：SignUpForm（注册时邮箱验证）

import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { sendEmailVerification } from '@/api/auth'
import { useCountdown } from '@/composables/useCountdown'

const EMAIL_VERIFICATION_COUNTDOWN = 30 // 秒

interface UseEmailVerificationOptions {
  turnstileToken?: string
  validateTurnstile?: () => boolean
}

export function useEmailVerification(options?: UseEmailVerificationOptions) {
  const { t } = useI18n()
  const isSending = ref<boolean>(false)
  const {
    secondsLeft,
    isActive,
    start: startCountdown
  } = useCountdown({ initialSeconds: EMAIL_VERIFICATION_COUNTDOWN })

  // 发送邮箱验证码：成功启动倒计时；失败由 request 拦截器弹错
  async function sendCode(email: string): Promise<boolean> {
    if (!email) {
      ElMessage.error(t('auth.email.required'))
      return false
    }

    if (options?.validateTurnstile && !options.validateTurnstile()) {
      return false
    }

    isSending.value = true
    try {
      await sendEmailVerification(email, options?.turnstileToken)
      startCountdown()
      ElMessage.success(t('auth.email.verificationSent'))
      return true
    } catch {
      // 错误已由 request 拦截器统一处理（ElMessage.error）
      return false
    } finally {
      isSending.value = false
    }
  }

  return {
    isSending,
    secondsLeft,
    isActive,
    sendCode
  }
}
