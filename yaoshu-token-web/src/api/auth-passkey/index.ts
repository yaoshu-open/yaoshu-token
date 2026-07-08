// 职责：Passkey 状态查询 / 注册（begin+finish）/ 删除 / 登录（begin+finish）/ 验证（begin+finish）
// 8 个端点全部对应后端契约_用户与Token.md §四

import { request } from '@/utils/request'
import type { PasskeyOptionsPayload, PasskeyStatus } from './types'

// 查询当前用户的 Passkey 启用状态：GET /api/user/passkey
export function getPasskeyStatus(): Promise<PasskeyStatus> {
  return request.get<PasskeyStatus>('/api/user/passkey')
}

// 注册开始：POST /api/user/passkey/register/begin
// 返回 PublicKeyCredentialCreationOptions（结构因后端实现不同，用 PasskeyOptionsPayload 接收）
export function beginPasskeyRegistration(): Promise<PasskeyOptionsPayload> {
  return request.post<PasskeyOptionsPayload>(
    '/api/user/passkey/register/begin'
  )
}

// 注册完成：POST /api/user/passkey/register/finish
// payload 由 utils/passkey.ts buildRegistrationResult 构建
export function finishPasskeyRegistration(
  payload: Record<string, unknown>
): Promise<void> {
  return request.post<void>('/api/user/passkey/register/finish', payload)
}

// 删除所有 Passkey：DELETE /api/user/passkey
export function deletePasskey(): Promise<void> {
  return request.delete<void>('/api/user/passkey')
}

// 登录开始：POST /api/user/passkey/login/begin
// 返回 PublicKeyCredentialRequestOptions
export function beginPasskeyLogin(): Promise<PasskeyOptionsPayload> {
  return request.post<PasskeyOptionsPayload>('/api/user/passkey/login/begin')
}

// 登录完成：POST /api/user/passkey/login/finish
// payload 由 utils/passkey.ts buildAssertionResult 构建
export function finishPasskeyLogin(
  payload: Record<string, unknown>
): Promise<unknown> {
  // 登录成功后返回 UserInfo（与 login API 一致），但字段结构未完全联调
  // 用 unknown 接收，消费侧（UserAuthForm）走 handleLoginSuccess 统一处理
  return request.post<unknown>('/api/user/passkey/login/finish', payload)
}

// 敏感操作验证开始：POST /api/user/passkey/verify/begin
export function beginPasskeyVerification(): Promise<PasskeyOptionsPayload> {
  return request.post<PasskeyOptionsPayload>(
    '/api/user/passkey/verify/begin'
  )
}

// 敏感操作验证完成：POST /api/user/passkey/verify/finish
export function finishPasskeyVerification(
  payload: Record<string, unknown>
): Promise<void> {
  return request.post<void>('/api/user/passkey/verify/finish', payload)
}
