import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getToken, removeToken, setToken } from '@/utils/auth'
import { getCurrentUser } from '@/api/user'
import * as authApi from '@/api/auth'
import type { LoginPayload } from '@/api/auth/types'
import type { UserInfo } from '@/api/user/types'

export type { UserInfo }

// login action 返回值：消费方据此决定后续 UI（跳 OTP 页 or 跳 dashboard）
export interface LoginActionResult {
  require2fa: boolean
}

export const useAuthStore = defineStore('auth', () => {
  // token 初始值从 @/utils/auth 读取，保持单一持久化入口（规约-路由与权限）
  const token = ref<string>(getToken())
  const userInfo = ref<UserInfo | null>(null)
  const userInfoLoading = ref<boolean>(false)
  const userInfoError = ref<Error | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  // role 派生（消费方 useUserPermissions 已封装更细粒度判断）
  const role = computed<number>(() => {
    const r = userInfo.value?.role
    return typeof r === 'number' ? r : 0
  })

  // 同步 token 到 state 与 localStorage
  function setAuthToken(value: string): void {
    token.value = value
    setToken(value)
  }

  // 拉取当前登录用户信息（/api/user/self），guards 首次进入受保护路由时调用
  // 401 由 request 响应拦截器统一处理（清 token + 跳登录），此处不重复
  // 其他错误：userInfo 保持 null + ElMessage 提示，不阻塞路由（侧边栏按 null 进入降级渲染）
  async function fetchUserInfo(): Promise<UserInfo | null> {
    if (!token.value) return null
    userInfoLoading.value = true
    userInfoError.value = null
    try {
      const data = await getCurrentUser()
      userInfo.value = data
      return data
    } catch (err) {
      userInfoError.value = err instanceof Error ? err : new Error(String(err))
      // 不清 token：网络错误/5xx 不等于登录失效，给用户重试机会
      // 401 已由拦截器处理，此处 catch 不会再捕获 401（拦截器抛出前已跳转）
      return null
    } finally {
      userInfoLoading.value = false
    }
  }

  // M1-B 登录 action：封装「API 调用 → 2FA 分支判断 → token 写入 → 用户信息拉取」
  // 失败由 request 拦截器统一处理（success=false → reject + ElMessage），此处 catch 不重复弹错
  async function login(payload: LoginPayload): Promise<LoginActionResult> {
    const data = await authApi.login(payload)

    // 2FA 分支：跳 OTP 页由消费方决定，store 仅返回标志位
    if (data.require2fa) {
      return { require2fa: true }
    }

    // 登录成功：写 token（M4 联调确认：token 从响应头提取，由 api/auth login 合并到 data.token）
    if (data.token) {
      setAuthToken(data.token)
      // 拉 UserInfo 失败不阻塞——网络抖动等场景用户已登录，下次访问受保护路由时 guards 会重试
      await fetchUserInfo()
      return { require2fa: false }
    }

    // 无 token 字段：后端契约异常（或 Cookie-based 鉴权需另接），抛错让消费方提示
    throw new Error('Login succeeded but no token received')
  }

  // M1-B 登出 action：调后端 logout → 清本地 token + userInfo
  // 后端 logout 失败不阻塞本地清退（防 token 失效后死循环）
  async function logout(): Promise<void> {
    try {
      await authApi.logout()
    } catch {
      // ignore：网络错误或 token 已失效都不影响本地清退
    }
    clearAuthToken()
  }

  // 清退本地会话（logout 内部 + OAuth 流程 resetSession 复用）
  function clearAuthToken(): void {
    token.value = ''
    userInfo.value = null
    userInfoError.value = null
    removeToken()
  }

  return {
    // state
    token,
    userInfo,
    userInfoLoading,
    userInfoError,
    // getter
    isLoggedIn,
    role,
    // action
    setAuthToken,
    fetchUserInfo,
    login,
    logout,
    clearAuthToken
  }
})
