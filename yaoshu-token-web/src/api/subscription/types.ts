/** 订阅套餐 */
export interface SubscriptionPlan {
  id: number
  title: string
  subtitle?: string
  priceAmount: number
  currency: string
  durationUnit: 'year' | 'month' | 'day' | 'hour' | 'custom'
  durationValue: number
  customSeconds?: number
  quotaResetPeriod: 'never' | 'daily' | 'weekly' | 'monthly' | 'custom'
  quotaResetCustomSeconds?: number
  enabled: boolean
  sortOrder: number
  allowBalancePay: boolean
  maxPurchasePerUser: number
  totalAmount: number
  upgradeGroup?: string
  stripePriceId?: string
  creemProductId?: string
  waffoPancakeProductId?: string
}

/** 套餐列表项（后端包裹在 record 内） */
export interface PlanRecord {
  plan: SubscriptionPlan
}

/** 创建/更新套餐 payload */
export interface PlanPayload {
  title: string
  subtitle?: string
  priceAmount: number
  currency: string
  durationUnit: string
  durationValue: number
  customSeconds?: number
  quotaResetPeriod: string
  quotaResetCustomSeconds?: number
  enabled: boolean
  sortOrder: number
  allowBalancePay: boolean
  maxPurchasePerUser: number
  totalAmount: number
  upgradeGroup?: string
  stripePriceId?: string
  creemProductId?: string
  waffoPancakeProductId?: string
}

/** 用户订阅 */
export interface UserSubscription {
  id: number
  userId: number
  planId: number
  status: string
  source?: string
  startTime: number
  endTime: number
  amountTotal: number
  amountUsed: number
  nextResetTime?: number
  /** 是否自动续期（true=到期自动扣费续期，false=已关闭续期，到期自然失效） */
  autoRenew?: boolean
}

export interface UserSubscriptionRecord {
  subscription: UserSubscription
}

/** 创建用户订阅请求 */
export interface CreateUserSubscriptionRequest {
  planId: number
  startTime?: number
  endTime?: number
}

/** 通用 API 响应 */
export interface ApiResponse<T = unknown> {
  success?: boolean
  message?: string
  data?: T
}

/** 支付合规确认 */
export interface ComplianceConfirmResponse {
  complianceConfirmed: boolean
}

/** 用户自助订阅数据（GET /api/subscription/self 返回） */
export interface SelfSubscriptionData {
  billingPreference: string
  subscriptions: UserSubscriptionRecord[]
  allSubscriptions: UserSubscriptionRecord[]
}

/** 订阅支付请求 */
export interface SubscriptionPayRequest {
  planId: number
  paymentMethod?: string
}

/** 订阅支付响应 */
export interface SubscriptionPayResponse {
  payLink?: string
  checkoutUrl?: string
  sessionId?: string
  orderId?: string
  token?: string
  url?: string
}
