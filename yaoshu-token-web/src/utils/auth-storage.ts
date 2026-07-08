// 职责：用户 ID（uid）+ 邀请码（aff）的持久化，与 utils/auth.ts 的 token 隔离
// 被消费方：composables/auth/useAuthRedirect.ts（写 uid）+ UserAuthForm/SignUpForm（读/写 aff）

const STORAGE_KEYS = {
  USER_ID: 'uid',
  AFFILIATE: 'aff'
} as const

// ============================================================================
// User ID Storage
// ============================================================================

export function saveUserId(userId: number | string): void {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.setItem(STORAGE_KEYS.USER_ID, String(userId))
  } catch (error) {
    console.error('Failed to save user ID:', error)
  }
}

export function getUserId(): string | null {
  if (typeof window === 'undefined') return null
  try {
    return window.localStorage.getItem(STORAGE_KEYS.USER_ID)
  } catch (error) {
    console.error('Failed to get user ID:', error)
    return null
  }
}

export function removeUserId(): void {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.removeItem(STORAGE_KEYS.USER_ID)
  } catch (error) {
    console.error('Failed to remove user ID:', error)
  }
}

// ============================================================================
// Affiliate Code Storage
// ============================================================================

export function getAffiliateCode(): string {
  if (typeof window === 'undefined') return ''
  try {
    return window.localStorage.getItem(STORAGE_KEYS.AFFILIATE) ?? ''
  } catch (error) {
    console.error('Failed to get affiliate code:', error)
    return ''
  }
}

export function saveAffiliateCode(code: string): void {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.setItem(STORAGE_KEYS.AFFILIATE, code)
  } catch (error) {
    console.error('Failed to save affiliate code:', error)
  }
}

export function removeAffiliateCode(): void {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.removeItem(STORAGE_KEYS.AFFILIATE)
  } catch (error) {
    console.error('Failed to remove affiliate code:', error)
  }
}
