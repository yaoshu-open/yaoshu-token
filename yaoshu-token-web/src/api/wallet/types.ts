/**
 * 钱包与支付类型（camelCase）。
 * 后端契约：ai-docs/后端设计/API_Contract/契约_订阅与支付.md。
 * CC-3 约束：所有字段 camelCase。
 */

/** 支付方式配置 */
export interface PaymentMethod {
  name: string
  type: string
  color?: string
  minTopup?: string | number
  icon?: string
}

/** Creem 产品 */
export interface CreemProduct {
  name: string
  productId: string
  price: number
  quota: number
  currency: 'USD' | 'EUR'
}

/** Waffo 支付方式 */
export interface WaffoPayMethod {
  name: string
  icon?: string
  payMethodType?: string
  payMethodName?: string
}

/** 充值配置信息（camelCase） */
export interface TopupInfo {
  enableOnlineTopup: boolean
  enableStripeTopup: boolean
  payMethods: PaymentMethod[]
  minTopup: number
  stripeMinTopup: number
  amountOptions: number[]
  discount: Record<number, number>
  topupLink?: string
  enableCreemTopup?: boolean
  creemProducts?: CreemProduct[]
  enableWaffoTopup?: boolean
  waffoPayMethods?: WaffoPayMethod[]
  waffoMinTopup?: number
  enableWaffoPancakeTopup?: boolean
  waffoPancakeMinTopup?: number
  enableRedemption?: boolean
  paymentComplianceConfirmed?: boolean
  paymentComplianceTermsVersion?: string
}

/** 预设额度 */
export interface PresetAmount {
  value: number
  discount?: number
}

/** 用户钱包数据（camelCase） */
export interface UserWalletData {
  id: number
  username: string
  quota: number
  usedQuota: number
  requestCount: number
  affQuota: number
  affHistoryQuota: number
  affCount: number
  group: string
}

/** 充值记录状态 */
export type TopupStatus = 'success' | 'pending' | 'expired'

/** 充值记录（camelCase） */
export interface TopupRecord {
  id: number
  userId: number
  amount: number
  money: number
  tradeNo: string
  paymentMethod: string
  createTime: number
  completeTime?: number
  status: TopupStatus
}

/** 支付响应 */
export interface PaymentResponse {
  success?: boolean
  message?: string
  data?: Record<string, unknown>
  url?: string
}

/** Stripe 支付响应 */
export interface StripePaymentResponse {
  success?: boolean
  message?: string
  data?: { payLink: string }
}

/** Creem 支付响应 */
export interface CreemPaymentResponse {
  success?: boolean
  message?: string
  data?: { checkoutUrl: string }
}

/** Waffo 支付响应 */
export interface WaffoPaymentResponse {
  success?: boolean
  message?: string
  data?: { paymentUrl?: string } | string
}

/** WaffoPancake 支付响应 */
export interface WaffoPancakePaymentResponse {
  success?: boolean
  message?: string
  data?: {
    checkoutUrl?: string
    sessionId?: string
    expiresAt?: number | string
    orderId?: string
    token?: string
    tokenExpiresAt?: number | string
  }
}

/** 兑换码请求 */
export interface RedemptionRequest {
  key: string
}

/** 支付请求 */
export interface PaymentRequest {
  amount: number
  paymentMethod: string
}

/** 金额计算请求 */
export interface AmountRequest {
  amount: number
}

/** 邀请转账请求 */
export interface AffiliateTransferRequest {
  quota: number
}

/** Waffo 支付请求 */
export interface WaffoPaymentRequest {
  amount: number
  payMethodIndex?: number
}

/** WaffoPancake 支付请求 */
export interface WaffoPancakePaymentRequest {
  amount: number
}

/** Creem 支付请求 */
export interface CreemPaymentRequest {
  productId: string
  paymentMethod: 'creem'
}

/** 完成订单请求 */
export interface CompleteOrderRequest {
  tradeNo: string
}
