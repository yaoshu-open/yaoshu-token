/**
 * 钱包与支付常量。
 * 后端契约：ai-docs/后端设计/API_Contract/契约_订阅与支付.md。
 */

export const WALLET_ENDPOINTS = {
  TOPUP_INFO: '/api/user/topup/info',
  TOPUP: '/api/user/topup',
  AMOUNT: '/api/user/amount',
  STRIPE_AMOUNT: '/api/user/stripe/amount',
  PAY: '/api/user/pay',
  STRIPE_PAY: '/api/user/stripe/pay',
  CREEM_PAY: '/api/user/creem/pay',
  WAFFO_PAY: '/api/user/waffo/pay',
  WAFFO_PANCAKE_AMOUNT: '/api/user/waffo-pancake/amount',
  WAFFO_PANCAKE_PAY: '/api/user/waffo-pancake/pay',
  AFF: '/api/user/aff',
  AFF_TRANSFER: '/api/user/aff_transfer',
  BILLING_SELF: '/api/user/topup/self',
  BILLING_ALL: '/api/user/topup',
  COMPLETE_ORDER: '/api/user/topup/complete',
  USER_SELF: '/api/user/self',
} as const

/** 默认折扣率（无折扣） */
export const DEFAULT_DISCOUNT_RATE = 1

/** 账单历史默认每页条数 */
export const BILLING_PAGE_SIZE = 20
