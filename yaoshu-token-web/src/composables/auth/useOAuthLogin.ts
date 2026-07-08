// 职责：各 provider（GitHub/Discord/OIDC/LinuxDO/Telegram/自定义）的登录重定向编排
// 流程：resetSession → getOAuthState → window.open(url, '_self')
// 被消费方：components/auth/OAuthProviders.vue

import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/store/modules/auth'
import { request } from '@/utils/request'
import { getOAuthState, buildCustomOAuthUrl, buildDiscordOAuthUrl, buildGitHubOAuthUrl, buildLinuxDOOAuthUrl, buildOIDCOAuthUrl } from '@/utils/oauth'
import type { CustomOAuthProviderInfo, SystemStatus } from '@/api/system/types'
import type { TelegramUser } from '@/components/auth/TelegramWidget.vue'

// status 类型来自 useStatus（SystemStatus | null），与 useStatus 返回类型一致
type StatusLike = SystemStatus | null

export function useOAuthLogin(status: StatusLike) {
  const { t } = useI18n()
  const isLoading = ref<boolean>(false)
  const githubButtonText = ref<string>('')
  const githubButtonDisabled = ref<boolean>(false)
  let githubTimeoutId: ReturnType<typeof setTimeout> | null = null
  const authStore = useAuthStore()

  githubButtonText.value = t('auth.oauth.github')

  // 重置本地会话：避免携带旧 session 触发 OAuth 流程异常
  async function resetSession(): Promise<void> {
    authStore.clearAuthToken()
    // 调后端 logout 清服务端会话，失败不阻塞（可能本来就已失效）
    try {
      await request.get<void>('/api/user/logout')
    } catch {
      // ignore：网络错误或已失效都不影响 OAuth 重定向
    }
  }
  async function handleGitHubLogin(): Promise<void> {
    const clientId = status?.githubClientId
    if (!clientId || githubButtonDisabled.value) return

    isLoading.value = true
    githubButtonDisabled.value = true
    githubButtonText.value = t('auth.oauth.redirecting', {
      provider: 'GitHub'
    })

    if (githubTimeoutId) clearTimeout(githubTimeoutId)
    githubTimeoutId = setTimeout(() => {
      isLoading.value = false
      githubButtonText.value = t('auth.oauth.timeout')
      githubButtonDisabled.value = true
    }, 20000)

    try {
      await resetSession()
      const state = await getOAuthState()
      if (!state) {
        ElMessage.error(t('auth.oauth.initFailed'))
        if (githubTimeoutId) clearTimeout(githubTimeoutId)
        isLoading.value = false
        githubButtonText.value = t('auth.oauth.github')
        githubButtonDisabled.value = false
        return
      }

      const url = buildGitHubOAuthUrl(clientId, state)
      window.open(url, '_self')
    } catch {
      ElMessage.error(t('auth.oauth.startFailed', { provider: 'GitHub' }))
      if (githubTimeoutId) clearTimeout(githubTimeoutId)
      isLoading.value = false
      githubButtonText.value = t('auth.oauth.github')
      githubButtonDisabled.value = false
    }
  }

  async function handleDiscordLogin(): Promise<void> {
    const clientId = status?.discordClientId
    if (!clientId) return

    isLoading.value = true
    try {
      await resetSession()
      const state = await getOAuthState()
      if (!state) {
        ElMessage.error(t('auth.oauth.initFailed'))
        return
      }

      const url = buildDiscordOAuthUrl(clientId, state)
      window.open(url, '_self')
    } catch {
      ElMessage.error(t('auth.oauth.startFailed', { provider: 'Discord' }))
    } finally {
      isLoading.value = false
    }
  }

  async function handleOIDCLogin(): Promise<void> {
    const authEndpoint = status?.oidcAuthorizationEndpoint
    const clientId = status?.oidcClientId
    if (!authEndpoint || !clientId) return

    isLoading.value = true
    try {
      await resetSession()
      const state = await getOAuthState()
      if (!state) {
        ElMessage.error(t('auth.oauth.initFailed'))
        return
      }

      const url = buildOIDCOAuthUrl(authEndpoint, clientId, state)
      window.open(url, '_self')
    } catch {
      ElMessage.error(t('auth.oauth.startFailed', { provider: 'OIDC' }))
    } finally {
      isLoading.value = false
    }
  }

  async function handleLinuxDOLogin(): Promise<void> {
    const clientId = status?.linuxdoClientId
    if (!clientId) return

    isLoading.value = true
    try {
      await resetSession()
      const state = await getOAuthState()
      if (!state) {
        ElMessage.error(t('auth.oauth.initFailed'))
        return
      }

      const url = buildLinuxDOOAuthUrl(clientId, state)
      window.open(url, '_self')
    } catch {
      ElMessage.error(t('auth.oauth.startFailed', { provider: 'LinuxDO' }))
    } finally {
      isLoading.value = false
    }
  }

  // Telegram 登录回调（M1-B-T3）：TelegramWidget 授权成功后透传参数到后端中转
  // 后端 /api/oauth/telegram/login 用 HmacSHA256 校验签名后按 telegram_id 登录
  async function handleTelegramCallback(user: TelegramUser): Promise<void> {
    isLoading.value = true
    try {
      await resetSession()
      // 后端契约 GET /api/oauth/telegram/login，参数为 Telegram Widget 回调原始字段
      const params = new URLSearchParams({
        id: String(user.id),
        auth_date: String(user.auth_date),
        hash: user.hash
      })
      if (user.first_name) params.set('first_name', user.first_name)
      if (user.last_name) params.set('last_name', user.last_name)
      if (user.username) params.set('username', user.username)
      if (user.photo_url) params.set('photo_url', user.photo_url)

      await request.get<void>(`/api/oauth/telegram/login?${params.toString()}`)
      // 后端 Sa-Token 建立会话后，前端刷新至控制台
      window.location.href = '/console'
    } catch {
      ElMessage.error(t('auth.oauth.startFailed', { provider: 'Telegram' }))
    } finally {
      isLoading.value = false
    }
  }

  async function handleCustomOAuthLogin(
    provider: CustomOAuthProviderInfo
  ): Promise<void> {
    if (!provider.authorizationEndpoint || !provider.clientId) return

    isLoading.value = true
    try {
      await resetSession()
      const state = await getOAuthState()
      if (!state) {
        ElMessage.error(t('auth.oauth.initFailed'))
        return
      }

      const url = buildCustomOAuthUrl(
        provider.authorizationEndpoint,
        provider.clientId,
        state,
        provider.slug,
        provider.scopes
      )
      window.open(url, '_self')
    } catch {
      ElMessage.error(
        t('auth.oauth.startFailed', { provider: provider.name })
      )
    } finally {
      isLoading.value = false
    }
  }

  return {
    isLoading,
    githubButtonText,
    githubButtonDisabled,
    handleGitHubLogin,
    handleDiscordLogin,
    handleOIDCLogin,
    handleLinuxDOLogin,
    handleTelegramCallback,
    handleCustomOAuthLogin
  }
}
