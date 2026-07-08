// 职责：ArrayBuffer ↔ Base64URL 转换 + PublicKeyCredential 创建/断言结果构建 + 环境支持检测
// 被消费方：composables/auth/usePasskeyManagement.ts（注册流程）+ useSecureVerification.ts（验证流程）+ UserAuthForm.vue（登录流程）

type NodeBufferCtor = {
  from(input: string, encoding: string): { toString(encoding: string): string }
}

// Base64URL → ArrayBuffer（后端下发的 challenge/user.id/allowCredentials.id 都需转换）
export function base64UrlToArrayBuffer(value?: string | null): ArrayBuffer {
  if (!value) return new ArrayBuffer(0)

  const padding = '='.repeat((4 - (value.length % 4)) % 4)
  const base64 = (value + padding).replace(/-/g, '+').replace(/_/g, '/')

  const globalRef = globalThis as typeof globalThis & {
    Buffer?: NodeBufferCtor
  }

  const decode =
    typeof globalRef.atob === 'function'
      ? globalRef.atob.bind(globalRef)
      : (input: string) => {
          if (typeof globalRef.Buffer !== 'undefined') {
            return globalRef.Buffer.from(input, 'base64').toString('binary')
          }
          throw new Error('Base64 decoding is not supported in this environment')
        }

  const binary = decode(base64)
  const buffer = new ArrayBuffer(binary.length)
  const bytes = new Uint8Array(buffer)

  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i)
  }

  return buffer
}

// ArrayBuffer → Base64URL（前端把 attestationObject/clientDataJSON/signature 等回传后端）
export function arrayBufferToBase64Url(
  buffer?: ArrayBuffer | ArrayBufferLike | null
): string {
  if (!buffer) return ''

  const globalRef = globalThis as typeof globalThis & {
    Buffer?: NodeBufferCtor
  }

  const bytes = new Uint8Array(buffer)
  let binary = ''
  for (let i = 0; i < bytes.byteLength; i += 1) {
    binary += String.fromCharCode(bytes[i])
  }

  const encode =
    typeof globalRef.btoa === 'function'
      ? globalRef.btoa.bind(globalRef)
      : (input: string) => {
          if (typeof globalRef.Buffer !== 'undefined') {
            return globalRef.Buffer.from(input, 'binary').toString('base64')
          }
          throw new Error('Base64 encoding is not supported in this environment')
        }

  return encode(binary)
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '')
}

type CredentialOptionsPayload = Record<string, unknown> | null | undefined

function extractOptions<T extends Record<string, unknown>>(
  payload: CredentialOptionsPayload
): T {
  const obj = (payload ?? {}) as Record<string, unknown>
  const options =
    (obj.publicKey as Record<string, unknown> | undefined) ??
    (obj.PublicKey as Record<string, unknown> | undefined) ??
    (obj.response as Record<string, unknown> | undefined) ??
    (obj.Response as Record<string, unknown> | undefined)

  if (!options) {
    throw new Error('Unable to parse Passkey options from response')
  }
  return options as T
}

// 后端 PublicKeyCredentialCreationOptions → 浏览器可消费的形态
// 编码 challenge / user.id / excludeCredentials[].id 为 ArrayBuffer
export function prepareCredentialCreationOptions(
  payload: CredentialOptionsPayload
): PublicKeyCredentialCreationOptions {
  const options = extractOptions<Record<string, unknown>>(payload)
  const challenge = options.challenge
  const userRaw = options.user as Record<string, unknown> | undefined
  const excludeCredentialsRaw = options.excludeCredentials as
    | Array<Record<string, unknown>>
    | undefined

  const publicKey = {
    ...options,
    challenge: base64UrlToArrayBuffer(challenge as string | undefined),
    user: {
      ...(userRaw ?? {}),
      id: base64UrlToArrayBuffer(userRaw?.id as string | undefined)
    }
  } as PublicKeyCredentialCreationOptions & Record<string, unknown>

  if (Array.isArray(excludeCredentialsRaw)) {
    // WebAuthn 规范要求 PublicKeyCredentialDescriptor 含 type 字段（默认 'public-key'）
    publicKey.excludeCredentials = excludeCredentialsRaw.map((item) => ({
      type: 'public-key' as const,
      ...item,
      id: base64UrlToArrayBuffer(item.id as string | undefined)
    }))
  }

  // attestationFormats 空数组时移除（部分浏览器不接受空数组）
  const attestationFormats = options.attestationFormats as
    | unknown[]
    | undefined
  if (Array.isArray(attestationFormats) && attestationFormats.length === 0) {
    delete publicKey.attestationFormats
  }

  return publicKey
}

// 后端 PublicKeyCredentialRequestOptions → 浏览器可消费的形态
// 编码 challenge / allowCredentials[].id 为 ArrayBuffer
export function prepareCredentialRequestOptions(
  payload: CredentialOptionsPayload
): PublicKeyCredentialRequestOptions {
  const options = extractOptions<Record<string, unknown>>(payload)
  const challenge = options.challenge
  const allowCredentialsRaw = options.allowCredentials as
    | Array<Record<string, unknown>>
    | undefined

  const publicKey = {
    ...options,
    challenge: base64UrlToArrayBuffer(challenge as string | undefined)
  } as PublicKeyCredentialRequestOptions & Record<string, unknown>

  if (Array.isArray(allowCredentialsRaw)) {
    publicKey.allowCredentials = allowCredentialsRaw.map((item) => ({
      type: 'public-key' as const,
      ...item,
      id: base64UrlToArrayBuffer(item.id as string | undefined)
    }))
  }

  return publicKey
}

// 构建注册完成时的回传 payload（ PublicKeyCredential → JSON-safe object ）
export function buildRegistrationResult(
  credential: PublicKeyCredential | null
): Record<string, unknown> | null {
  if (!credential) return null

  const response = credential.response as AuthenticatorAttestationResponse & {
    getTransports?: () => string[]
  }

  const transports =
    typeof response.getTransports === 'function'
      ? response.getTransports()
      : undefined

  return {
    id: credential.id,
    rawId: arrayBufferToBase64Url(credential.rawId),
    type: credential.type,
    authenticatorAttachment: credential.authenticatorAttachment,
    response: {
      attestationObject: arrayBufferToBase64Url(response.attestationObject),
      clientDataJSON: arrayBufferToBase64Url(response.clientDataJSON),
      transports
    },
    clientExtensionResults: credential.getClientExtensionResults?.() ?? {}
  }
}

// 构建登录/验证完成时的回传 payload（ PublicKeyCredential → JSON-safe object ）
export function buildAssertionResult(
  credential: PublicKeyCredential | null
): Record<string, unknown> | null {
  if (!credential) return null

  const response = credential.response as AuthenticatorAssertionResponse

  return {
    id: credential.id,
    rawId: arrayBufferToBase64Url(credential.rawId),
    type: credential.type,
    authenticatorAttachment: credential.authenticatorAttachment,
    response: {
      authenticatorData: arrayBufferToBase64Url(response.authenticatorData),
      clientDataJSON: arrayBufferToBase64Url(response.clientDataJSON),
      signature: arrayBufferToBase64Url(response.signature),
      userHandle: response.userHandle
        ? arrayBufferToBase64Url(response.userHandle)
        : null
    },
    clientExtensionResults: credential.getClientExtensionResults?.() ?? {}
  }
}

// 当前环境是否支持 Passkey/WebAuthn
export async function isPasskeySupported(): Promise<boolean> {
  if (typeof window === 'undefined') return false
  const { PublicKeyCredential } = window
  if (!PublicKeyCredential) return false

  // 优先检测条件调解（autofill 流）
  if (
    typeof PublicKeyCredential.isConditionalMediationAvailable === 'function'
  ) {
    try {
      const available =
        await PublicKeyCredential.isConditionalMediationAvailable()
      if (available) return true
    } catch {
      // ignore，降级走 platform authenticator 检测
    }
  }

  if (
    typeof PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable ===
    'function'
  ) {
    try {
      return await PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable()
    } catch {
      return false
    }
  }

  return true
}

// 创建凭证（navigator.credentials.create 的薄包装，便于 mock）
export async function createCredential(
  options: PublicKeyCredentialCreationOptions
) {
  return navigator.credentials.create({ publicKey: options })
}

// 获取凭证（navigator.credentials.get 的薄包装，便于 mock）
export async function getCredential(
  options: PublicKeyCredentialRequestOptions
) {
  return navigator.credentials.get({ publicKey: options })
}
