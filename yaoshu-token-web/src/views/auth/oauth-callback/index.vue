<script setup lang="ts">
// 职责：OAuth provider 回调落地，处理 login/bind 双模式（window.opener 存在=bind，否则=login）
// 流程：URL 取 code/state → oauthCallback API → getSelf 写入 store → 跳 dashboard
//       bind 模式：localStorage 写入绑定结果，关闭弹窗（如果有 opener）

import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/store/modules/auth'
import { oauthCallback, wechatLoginByCode } from '@/api/auth'
import { saveUserId } from '@/utils/auth-storage'
import OAuthCallbackScreen from '@/components/auth/OAuthCallbackScreen.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const provider = ref<string>(
  typeof route.params.provider === 'string' ? route.params.provider : ''
)
const mode = ref<'login' | 'bind'>(
  typeof window !== 'undefined' && window.opener ? 'bind' : 'login'
)

function safeNavigate(target: string): void {
  router.replace(target).catch(() => {
    /* router replace 失败由 fallback 兜底 */
  })
  // vue-router replace 不带页面刷新，bind 模式下弹窗关闭需 window.location 强制刷新
  if (typeof window !== 'undefined') {
    setTimeout(() => {
      const normalized = target.startsWith('/') ? target : `/${target}`
      const current = window.location.pathname + window.location.search
      if (current !== normalized && current !== `${normalized}/`) {
        window.location.replace(target)
      }
    }, 100)
  }
}

// 写入 OAuth 绑定结果到 localStorage，原窗口（opener）通过 storage 事件感知
function notifyBindingResult(statusResult: 'success' | 'error'): void {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.setItem(
      'oauth:binding:result',
      JSON.stringify({
        provider: provider.value,
        status: statusResult,
        timestamp: Date.now()
      })
    )
  } catch {
    // localStorage 写入失败不阻塞流程
  }
}

// 关闭绑定弹窗
function closeBindingWindow(): void {
  if (typeof window === 'undefined') return
  window.close()
  // 关闭失败（浏览器拦截）时回退到 profile 页
  setTimeout(() => {
    if (!window.closed) {
      window.location.replace('/')
    }
  }, 200)
}

// 拉 UserInfo 并写入 store（用于非 bind 流程的登录态建立）
async function finalizeLogin(): Promise<boolean> {
  try {
    const userInfo = await authStore.fetchUserInfo()
    if (userInfo && authStore.token) {
      if (userInfo.id != null) {
        saveUserId(userInfo.id)
      }
      return true
    }
  } catch {
    // ignore：fetchUserInfo 内部已处理
  }
  return false
}

function redirectAfterLogin(target?: string): void {
  const redirectTo = route.query.redirect
  const to =
    target ||
    (typeof redirectTo === 'string' ? redirectTo : undefined) ||
    '/'
  safeNavigate(to)
  ElMessage.success(t('auth.oauthCallback.signedIn'))
}

onMounted(async () => {
  const code = route.query.code
  const state = route.query.state

  // 微信特殊处理：先调 wechatLoginByCode
  if (provider.value === 'wechat' && typeof code === 'string') {
    try {
      await wechatLoginByCode(code)
    } catch {
      // 错误由 request 拦截器处理
    }
  }

  // 缺少 code 参数：跳登录
  if (!code || typeof code !== 'string') {
    ElMessage.error(t('auth.oauthCallback.missingCode'))
    safeNavigate('/sign-in')
    return
  }

  const isBindingFlow =
    typeof window !== 'undefined' ? Boolean(window.opener) : mode.value === 'bind'
  mode.value = isBindingFlow ? 'bind' : 'login'

  try {
    const data = await oauthCallback(
      provider.value,
      code,
      typeof state === 'string' ? state : undefined
    )

    // 后端 message='bind' 表示绑定成功
    if (data?.message === 'bind') {
      ElMessage.success(t('auth.oauthCallback.bindSuccess'))
      notifyBindingResult('success')
      if (isBindingFlow) {
        closeBindingWindow()
      } else {
        safeNavigate('/')
      }
      return
    }

    // data 内可能直接含 UserInfo 字段（id 等），写入 uid
    if (data && typeof data.id === 'number') {
      saveUserId(data.id)
    }

    // 登录态建立：先尝试 fetchUserInfo，失败则跳登录页
    if (await finalizeLogin()) {
      redirectAfterLogin()
      return
    }

    // fetchUserInfo 失败：跳登录页让用户重新认证
    ElMessage.error(t('auth.oauthCallback.failed'))
    safeNavigate('/sign-in')
  } catch (error) {
    const message =
      (error &&
        typeof error === 'object' &&
        'response' in error &&
        (error as { response?: { data?: { message?: string } } }).response?.data
          ?.message) ||
      (error instanceof Error ? error.message : '') ||
      t('auth.oauthCallback.failed')

    if (isBindingFlow) {
      notifyBindingResult('error')
      ElMessage.error(message)
      return
    }

    // 登录流失败兜底：尝试 finalizeLogin，成功则跳首页
    if (await finalizeLogin()) {
      redirectAfterLogin()
      return
    }

    ElMessage.error(message)
    safeNavigate('/sign-in')
  }
})
</script>

<template>
  <OAuthCallbackScreen
    :provider="provider"
    :mode="mode"
  />
</template>
