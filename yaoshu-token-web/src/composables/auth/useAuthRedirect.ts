// 职责：handleLoginSuccess（拉 self + 写 uid + 恢复语言 + 跳转）+ redirectTo2FA/Login/Register
// 被消费方：UserAuthForm / OtpForm / OAuth 回调 view / WeChatLoginDialog

import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/store/modules/auth'
import { saveUserId } from '@/utils/auth-storage'
function getSavedLanguage(user: Record<string, unknown>): string | undefined {
  if (typeof user.language === 'string') {
    return user.language
  }

  if (typeof user.setting !== 'string') {
    return undefined
  }

  try {
    const setting = JSON.parse(user.setting) as { language?: unknown }
    return typeof setting.language === 'string' ? setting.language : undefined
  } catch {
    return undefined
  }
}

export function useAuthRedirect() {
  const router = useRouter()
  const { locale } = useI18n()
  const authStore = useAuthStore()

  // 登录成功后的统一处理：可选写 uid → 拉用户信息 → 恢复语言 → 跳转目标
  // userData.id 可选（部分 API 直接在 response 内下发 user info；不传则 fetchUserInfo 拉）
  async function handleLoginSuccess(
    userData?: { id?: number } | null,
    redirectTo?: string
  ): Promise<void> {
    if (userData?.id) {
      saveUserId(userData.id)
    }

    // 拉取完整 UserInfo 并写入 store（fetchUserInfo 内部失败不阻塞跳转）
    await authStore.fetchUserInfo()

    // 恢复用户保存的语言偏好
    if (authStore.userInfo) {
      const savedLang = getSavedLanguage(
        authStore.userInfo as Record<string, unknown>
      )
      if (savedLang && savedLang !== locale.value) {
        locale.value = savedLang
      }
    }

    // 跳转：优先 redirect 参数，否则默认控制台
    const targetPath = redirectTo || '/dashboard'
    router.replace(targetPath)
  }

  function redirectTo2FA(): void {
    router.replace('/otp')
  }

  function redirectToLogin(): void {
    router.replace('/sign-in')
  }

  function redirectToRegister(): void {
    router.replace('/sign-up')
  }

  return {
    handleLoginSuccess,
    redirectTo2FA,
    redirectToLogin,
    redirectToRegister
  }
}
