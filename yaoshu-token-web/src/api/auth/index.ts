// 职责：login/register/logout/login2fa/sendPasswordResetEmail/sendEmailVerification/
//       getOAuthState/wechatLoginByCode/bindEmail/resetPassword/oauthCallback
// 遵循 M0 request 拦截器约定（/api/* 自动解包 data），返回值类型只标注 data 部分
// Mock 闭环：DEV + VITE_AUTH_MOCK=true 时切换 mock 实现，不污染业务消费方

import { request } from '@/utils/request'
import type { UserInfo } from '@/api/user/types'
import type {
  LoginPayload,
  LoginResponseData,
  TwoFAPayload,
  RegisterPayload,
  ResetPasswordPayload,
  OAuthCallbackResponseData,
  OAuthStateResponse,
  WeChatLoginResponseData,
  ResetPasswordResponseData
} from './types'

const USE_MOCK = import.meta.env.DEV && import.meta.env.VITE_AUTH_MOCK === 'true'

// ============================================================================
// Login & Logout
// ============================================================================

// 用户名密码登录：POST /api/user/login?turnstile=xxx
// 后端 sa-token 鉴权：token 在响应体 data.token（Result 格式），auth store 据此 setAuthToken 持久化
export async function login(payload: LoginPayload): Promise<LoginResponseData> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockLogin(payload))
  }
  const turnstile = payload.turnstile ?? ''
  const response = await request.postWithHeaders<LoginResponseData>(
    `/api/user/login?turnstile=${encodeURIComponent(turnstile)}`,
    { username: payload.username, password: payload.password }
  )
  // 后端 sa-token 鉴权：token 在响应体 data.token（Result 格式 {code,msg,flag,data}），响应头不下发 token
  const body = response.data as { data: LoginResponseData }
  return body.data
}

// 2FA 登录验证：POST /api/user/login/2fa
// response data 内为完整 UserInfo
export function login2fa(payload: TwoFAPayload): Promise<UserInfo> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockLogin2FA(payload.code) as Promise<UserInfo>)
  }
  return request.post<UserInfo>('/api/user/login/2fa', payload)
}

// 用户登出：GET /api/user/logout
// 后端清理 session，前端由 store.logout() 清本地 token
export function logout(): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockLogout())
  }
  return request.get<void>('/api/user/logout')
}

// ============================================================================
// Registration & Email Verification
// ============================================================================

// 用户注册：POST /api/user/register?turnstile=xxx
// response 无业务 data，失败由拦截器 reject
export function register(payload: RegisterPayload): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockRegister(payload))
  }
  return request.post<void>('/api/user/register', payload, {
    params: { turnstile: payload.turnstile ?? '' }
  })
}

// 发送邮箱验证码：GET /api/verification?email=xxx&turnstile=xxx
export function sendEmailVerification(
  email: string,
  turnstile?: string
): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockSendEmailVerification(email))
  }
  return request.get<void>('/api/verification', {
    params: { email, turnstile }
  })
}

// 绑定邮箱到 OAuth 账号：POST /api/oauth/email/bind
export function bindEmail(email: string, code: string): Promise<void> {
  return request.post<void>('/api/oauth/email/bind', { email, code })
}

// ============================================================================
// Password Management
// ============================================================================

// 发送密码重置邮件：GET /api/reset_password?email=xxx&turnstile=xxx
export function sendPasswordResetEmail(
  email: string,
  turnstile?: string
): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockSendPasswordResetEmail(email))
  }
  return request.get<void>('/api/reset_password', {
    params: { email, turnstile }
  })
}

// 重置密码（落地页提交）：POST /api/user/reset
export function resetPassword(
  payload: ResetPasswordPayload
): Promise<ResetPasswordResponseData> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockResetPassword(payload))
  }
  return request.post<ResetPasswordResponseData>('/api/user/reset', payload)
}

// ============================================================================
// OAuth
// ============================================================================

// 获取 OAuth state token：utils/oauth.ts 已封装，此处不重复
// 消费方直接 import { getOAuthState } from '@/utils/oauth'

// OAuth provider 回调：GET /api/oauth/:provider?code=xxx&state=xxx
// response data 内 message='bind' 表示绑定成功，data 内为 UserInfo 表示登录成功
// 注：拦截器解包后，message 字段已不在顶层 data，需要后端契约明确
export function oauthCallback(
  provider: string,
  code: string,
  state?: string
): Promise<OAuthCallbackResponseData> {
  if (USE_MOCK) {
    return import('./mock').then((m) =>
      m.mockOAuthCallback(provider) as Promise<OAuthCallbackResponseData>
    )
  }
  return request.get<OAuthCallbackResponseData>(`/api/oauth/${provider}`, {
    params: { code, state }
  })
}

// 微信扫码登录验证码换取 session：GET /api/oauth/wechat?code=xxx
export function wechatLoginByCode(code: string): Promise<WeChatLoginResponseData> {
  if (USE_MOCK) {
    return import('./mock').then((m) =>
      m.mockWechatLogin(code) as Promise<WeChatLoginResponseData>
    )
  }
  return request.get<WeChatLoginResponseData>('/api/oauth/wechat', {
    params: { code }
  })
}

// 导出 OAuthStateResponse 类型别名供消费方引用
export type { OAuthStateResponse }
