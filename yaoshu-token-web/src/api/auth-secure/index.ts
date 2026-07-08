// 职责：检查当前用户可用的验证方法（2FA/Passkey）+ 执行验证流程
// 被消费方：composables/auth/useSecureVerification.ts（被 M2 业务模块的敏感操作消费）

import { request } from '@/utils/request'
import {
  beginPasskeyVerification,
  finishPasskeyVerification,
  getPasskeyStatus
} from '@/api/auth-passkey'
import {
  buildAssertionResult,
  isPasskeySupported,
  prepareCredentialRequestOptions
} from '@/utils/passkey'
import type { VerificationMethod, VerificationMethods } from './types'

// 后端 2FA 状态响应：data 内含 enabled 字段
interface TwoFAStatusResponse {
  enabled?: boolean
  [key: string]: unknown
}

// 探测当前用户可用的二次验证方法
// 并发请求 2FA 状态 + Passkey 状态 + 浏览器 Passkey 支持检测
export async function checkVerificationMethods(): Promise<VerificationMethods> {
  try {
    const [twoFAResponse, passkeyResponse, passkeySupported] =
      await Promise.all([
        request
          .get<TwoFAStatusResponse>('/api/user/2fa/status')
          .catch(() => null),
        getPasskeyStatus().catch(() => null),
        isPasskeySupported().catch(() => false)
      ])

    return {
      has2FA: Boolean(twoFAResponse?.enabled),
      hasPasskey: Boolean(passkeyResponse?.enabled),
      passkeySupported
    }
  } catch (error) {
    console.error('[Secure Verification] Failed to check methods', error)
    return {
      has2FA: false,
      hasPasskey: false,
      passkeySupported: false
    }
  }
}

// 执行验证流程（根据 method 分发到 2FA 或 Passkey 子流程）
export async function verify(
  method: VerificationMethod,
  code?: string
): Promise<void> {
  switch (method) {
    case '2fa':
      return verifyTwoFA(code)
    case 'passkey':
      return verifyPasskey()
    default:
      throw new Error(`Unsupported verification method: ${method}`)
  }
}

// 2FA 验证：POST /api/verify { method: '2fa', code }
async function verifyTwoFA(code?: string | null): Promise<void> {
  const trimmed = code?.trim()
  if (!trimmed) {
    throw new Error('Please enter the verification code or backup code')
  }

  await request.post<void>('/api/verify', { method: '2fa', code: trimmed })
}

// Passkey 验证：begin → navigator.credentials.get → finish → POST /api/verify
async function verifyPasskey(): Promise<void> {
  if (typeof navigator === 'undefined' || !navigator.credentials) {
    throw new Error('Passkey verification is not supported in this environment')
  }

  try {
    const beginResponse = await beginPasskeyVerification()

    const publicKey = prepareCredentialRequestOptions(beginResponse)
    const credential = (await navigator.credentials.get({
      publicKey
    })) as PublicKeyCredential | null

    if (!credential) {
      throw new Error('Passkey verification was cancelled')
    }

    const assertion = buildAssertionResult(credential)
    if (!assertion) {
      throw new Error('Unable to build Passkey assertion')
    }

    await finishPasskeyVerification(assertion)
    await request.post<void>('/api/verify', { method: 'passkey' })
  } catch (error: unknown) {
    if (error instanceof DOMException && error.name === 'NotAllowedError') {
      throw new Error('Passkey verification was cancelled or timed out')
    }
    if (error instanceof DOMException && error.name === 'InvalidStateError') {
      throw new Error(
        'Passkey verification is not available in the current state'
      )
    }
    throw error
  }
}
