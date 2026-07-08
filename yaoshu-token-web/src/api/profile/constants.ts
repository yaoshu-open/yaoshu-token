
// ============================================================================
// Mock 开关
// ============================================================================

const DEV = import.meta.env.DEV
const VITE_PROFILE_MOCK = import.meta.env.VITE_PROFILE_MOCK === 'true'
export const USE_MOCK = DEV && VITE_PROFILE_MOCK

/** 默认配额预警阈值（500,000 = $1） */
export const DEFAULT_QUOTA_WARNING_THRESHOLD = 500000

/** 通知方式列表 */
export const NOTIFICATION_METHODS = [
  { value: 'email' as const, label: 'Email' },
  { value: 'webhook' as const, label: 'Webhook' },
  { value: 'bark' as const, label: 'Bark' },
  { value: 'gotify' as const, label: 'Gotify' },
] as const

/** Profile 相关端点 */
export const PROFILE_ENDPOINTS = {
  USER_SELF: '/api/user/self',
  USER_SETTING: '/api/user/setting',
  USER_TOKEN: '/api/user/token',
  VERIFICATION: '/api/verification',
  OAUTH_EMAIL_BIND: '/api/oauth/email/bind',
  OAUTH_EMAIL_UNBIND: '/api/oauth/email/unbind',
  OAUTH_WECHAT_BIND: '/api/oauth/wechat/bind',
  OAUTH_BINDINGS: '/api/user/oauth/bindings',
  TWOFA_STATUS: '/api/user/2fa/status',
  TWOFA_SECRET: '/api/user/2fa/secret',
  TWOFA_VERIFY: '/api/user/2fa/verify',
  TWOFA: '/api/user/2fa',
  TWOFA_BACKUP: '/api/user/2fa/backup',
  CHECKIN: '/api/user/checkin',
} as const
