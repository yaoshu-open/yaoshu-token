/**
 * Profile API Service。
 * Vue3 侧遵循 camelCase 契约，request 拦截器自动解包 data，响应类型只标注 data 部分。
 *
 * Mock 闭环：DEV + VITE_PROFILE_MOCK=true 时切换 mock 实现（动态 import 避免污染
 * production build），与 model/channel 模式一致。
 */
import { request } from '@/utils/request'
import { PROFILE_ENDPOINTS, USE_MOCK } from './constants'
import type {
  BindEmailRequest,
  CheckinResponse,
  CheckinStatusResponse,
  CustomOAuthBinding,
  DeleteAccountRequest,
  TwoFADisableRequest,
  TwoFASetupData,
  TwoFAStatus,
  TwoFAVerifyRequest,
  UpdateUserRequest,
  UpdateUserSettingsRequest,
  UserProfile,
} from './types'

// ============================================================================
// 用户资料
// ============================================================================

/** 获取当前用户资料 */
export function getUserProfile(): Promise<UserProfile> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGetUserProfile())
  }
  return request.get<UserProfile>(PROFILE_ENDPOINTS.USER_SELF)
}

/** 更新个人信息（displayName/password/originalPassword/language） */
export function updateUserProfile(data: UpdateUserRequest): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockUpdateUserProfile(data))
  }
  return request.put<void>(PROFILE_ENDPOINTS.USER_SELF, data)
}

/** 更新用户设置（通知设置等） */
export function updateUserSettings(
  data: UpdateUserSettingsRequest
): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockUpdateUserSettings(data))
  }
  return request.put<void>(PROFILE_ENDPOINTS.USER_SETTING, data)
}

/** 删除账号 */
export function deleteUserAccount(data?: DeleteAccountRequest): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockDeleteUserAccount(data))
  }
  return request.delete<void>(PROFILE_ENDPOINTS.USER_SELF, { data })
}

/** 生成/重生成系统访问令牌 */
export function generateAccessToken(): Promise<string> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGenerateAccessToken())
  }
  return request.get<string>(PROFILE_ENDPOINTS.USER_TOKEN)
}

// ============================================================================
// 账号绑定
// ============================================================================

/** 发送邮箱验证码 */
export function sendEmailVerification(
  email: string,
  turnstileToken?: string
): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockSendEmailVerification(email))
  }
  const params: Record<string, string> = { email }
  if (turnstileToken) {
    params.turnstile = turnstileToken
  }
  return request.get<void>(PROFILE_ENDPOINTS.VERIFICATION, { params })
}

/** 绑定邮箱 */
export function bindEmail(data: BindEmailRequest): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockBindEmail(data))
  }
  return request.post<void>(PROFILE_ENDPOINTS.OAUTH_EMAIL_BIND, data)
}

/** 解绑邮箱（验证码二次确认，与绑定接口对称） */
export function unbindEmail(data: { code: string }): Promise<void> {
  return request.post<void>(PROFILE_ENDPOINTS.OAUTH_EMAIL_UNBIND, data)
}

/** 绑定微信 */
export function bindWeChat(code: string): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockBindWeChat(code))
  }
  return request.get<void>(PROFILE_ENDPOINTS.OAUTH_WECHAT_BIND, {
    params: { code },
  })
}

/** 获取自定义 OAuth 绑定列表 */
export function getSelfOAuthBindings(): Promise<CustomOAuthBinding[]> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGetSelfOAuthBindings())
  }
  return request.get<CustomOAuthBinding[]>(PROFILE_ENDPOINTS.OAUTH_BINDINGS)
}

/** 解绑自定义 OAuth */
export function unbindCustomOAuth(providerId: string): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockUnbindCustomOAuth(providerId))
  }
  return request.delete<void>(`${PROFILE_ENDPOINTS.OAUTH_BINDINGS}/${providerId}`)
}

// ============================================================================
// 2FA
// ============================================================================

/** 获取 2FA 状态 */
export function get2FAStatus(): Promise<TwoFAStatus> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGet2FAStatus())
  }
  return request.get<TwoFAStatus>(PROFILE_ENDPOINTS.TWOFA_STATUS)
}

/** 2FA 启用：获取 secret + QR 码 */
export function get2FASecret(): Promise<TwoFASetupData> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGet2FASecret())
  }
  return request.get<TwoFASetupData>(PROFILE_ENDPOINTS.TWOFA_SECRET)
}

/** 2FA 启用：验证验证码完成启用 */
export function verify2FA(data: TwoFAVerifyRequest): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockVerify2FA(data))
  }
  return request.post<void>(PROFILE_ENDPOINTS.TWOFA_VERIFY, data)
}

/** 2FA 关闭（需密码验证） */
export function disable2FA(data: TwoFADisableRequest): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockDisable2FA(data))
  }
  return request.delete<void>(PROFILE_ENDPOINTS.TWOFA, { data })
}

/** 获取/重新生成 2FA 备用码 */
export function get2FABackupCodes(): Promise<string[]> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGet2FABackupCodes())
  }
  return request.get<string[]>(PROFILE_ENDPOINTS.TWOFA_BACKUP)
}

// ============================================================================
// 签到
// ============================================================================

/** 获取签到状态+记录（month 格式 YYYY-MM） */
export function getCheckinStatus(month: string): Promise<CheckinStatusResponse> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGetCheckinStatus(month))
  }
  return request.get<CheckinStatusResponse>(PROFILE_ENDPOINTS.CHECKIN, {
    params: { month },
    _silent: true,
  })
}

/** 执行签到 */
export function performCheckin(turnstileToken?: string): Promise<CheckinResponse> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockPerformCheckin())
  }
  const params: Record<string, string> = {}
  if (turnstileToken) {
    params.turnstile = turnstileToken
  }
  return request.post<CheckinResponse>(PROFILE_ENDPOINTS.CHECKIN, undefined, {
    params,
    _silent: true,
  })
}
