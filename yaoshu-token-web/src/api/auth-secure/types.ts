// 敏感操作（M2 业务模块）的二次验证，支持 2FA 与 Passkey 双路径

export type VerificationMethod = '2fa' | 'passkey'

export interface VerificationMethods {
  has2FA: boolean
  hasPasskey: boolean
  passkeySupported: boolean
}

export interface SecureVerificationState {
  method: VerificationMethod | null
  loading: boolean
  code: string
  title?: string
  description?: string
}

export interface UseSecureVerificationOptions {
  onSuccess?: (result: unknown, method: VerificationMethod) => void
  onError?: (error: unknown) => void
  successMessage?: string
  autoReset?: boolean
}

export interface StartVerificationOptions {
  preferredMethod?: VerificationMethod
  title?: string
  description?: string
}
