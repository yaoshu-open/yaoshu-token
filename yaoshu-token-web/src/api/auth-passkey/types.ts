// WebAuthn 的 PublicKeyCredential options 字段类型复杂，统一用 unknown 接收
// 在 utils/passkey.ts 的 prepareCredential*Options 内做结构化解析

export interface PasskeyStatus {
  enabled: boolean
  lastUsedAt?: string | null
  backupEligible?: boolean
  backupState?: boolean
  [key: string]: unknown
}

// begin 阶段返回的 options 结构因浏览器/后端实现差异较大（publicKey/response/Response 三种字段名）
// 用 unknown 接收，prepareCredential*Options 内做 fallback 解析
// 加 index signature 让此类型兼容 utils/passkey.ts 的 Record<string, unknown> 参数
export interface PasskeyOptionsPayload {
  options?: unknown
  publicKey?: unknown
  response?: unknown
  Response?: unknown
  [key: string]: unknown
}
