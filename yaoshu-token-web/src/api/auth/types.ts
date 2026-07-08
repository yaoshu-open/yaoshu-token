// 注：Vue3 侧遵循 M0 request 拦截器约定（/api/* 自动解包 data），所有响应类型只标注 data 部分
// 不再带 success/message 外层包装（拦截器层已处理）

// ============================================================================
// API Payloads
// ============================================================================

export interface LoginPayload {
  username: string
  password: string
  turnstile?: string
}

export interface TwoFAPayload {
  code: string
}

export interface RegisterPayload {
  username: string
  password: string
  email?: string
  verificationCode?: string
  affCode?: string
  turnstile?: string
}

export interface PasswordResetPayload {
  email: string
  turnstile?: string
}

export interface EmailVerificationPayload {
  email: string
  turnstile?: string
}

export interface BindEmailPayload {
  email: string
  code: string
}

export interface ResetPasswordPayload {
  email: string
  token: string
}

// ============================================================================
// API Response Data（拦截器解包后的 data 部分）
// ============================================================================

// 登录响应 data：require2fa=true 跳 OTP 页；否则视为登录成功
// M4 联调确认：token 从响应头（token header）提取，由 api/auth login 合并到 data.token
export interface LoginResponseData {
  require2fa?: boolean
  id?: number
  token?: string
}

// 2FA 登录响应 data：成功后下发完整 UserInfo
// 复用 api/user/types.ts 的 UserInfo，避免类型重复声明
import type { UserInfo } from '@/api/user/types'

export type Login2FAResponseData = UserInfo

// 注册/登出/发送验证码/发送重置邮件/绑定邮箱：后端 data 部分通常无业务数据，统一 void
// 失败由 request 拦截器（success=false → reject + ElMessage）处理
export type VoidResponse = void

// OAuth state token：GET /api/oauth/state 返回的 CSRF state 字符串
export type OAuthStateResponse = string

// OAuth provider 回调响应 data：含 message 区分 login/bind 模式 + data 内可能直接为 UserInfo
export interface OAuthCallbackResponseData {
  message?: string
  // data 内可能是 UserInfo（登录成功），也可能为空（绑定成功）
  // request 拦截器已对 /api/* 解包，故此处直接为 UserInfo 或 unknown
  // 用 unknown + 类型守卫在消费侧判断
  [key: string]: unknown
}

// 微信登录响应：与 OAuthCallback 一致
export type WeChatLoginResponseData = OAuthCallbackResponseData

// 重置密码响应：成功时 data 部分为后端生成的新密码字符串
export type ResetPasswordResponseData = string

// ============================================================================
// OAuth Providers
// ============================================================================
// 注：SystemStatus / CustomOAuthProviderInfo 类型定义在 @/api/system/types（M1-B 扩展），
// 此处仅定义 UI 派生的 OAuthProvider 类型（OAuthProviders 组件按钮配置）

export type OAuthProviderType =
  | 'github'
  | 'discord'
  | 'oidc'
  | 'linuxdo'
  | 'telegram'
  | 'wechat'

export interface OAuthProvider {
  name: string
  type: OAuthProviderType
  enabled: boolean
  clientId?: string
  authEndpoint?: string
}

// 复用 system/types.ts 的 CustomOAuthProviderInfo，避免重复声明
export type { CustomOAuthProviderInfo } from '@/api/system/types'
