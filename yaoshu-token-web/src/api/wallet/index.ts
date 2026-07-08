/**
 * 钱包与支付 API Service。
 * 后端契约：ai-docs/后端设计/API_Contract/契约_用户与Token.md（第八章 支付与充值）。
 * 字段命名：/api/* 管理端统一 camelCase（前端架构 §5.1 契约铁律），不引入隐式 snake↔camel 转换。
 * 分页响应统一 PageInfo<T>（见 @/api/types），分页请求参数 pageNum/pageSize。
 */
import { request } from '@/utils/request'
import { WALLET_ENDPOINTS } from './constants'
import type { PageInfo } from '@/api/types'
import type {
  AffiliateTransferRequest,
  AmountRequest,
  CompleteOrderRequest,
  CreemPaymentRequest,
  CreemPaymentResponse,
  PaymentRequest,
  PaymentResponse,
  RedemptionRequest,
  StripePaymentResponse,
  TopupInfo,
  TopupRecord,
  UserWalletData,
  WaffoPaymentRequest,
  WaffoPaymentResponse,
  WaffoPancakePaymentRequest,
  WaffoPancakePaymentResponse,
} from './types'

// ============================================================================
// 用户数据
// ============================================================================

/** 获取当前用户钱包数据 */
export async function getSelfUser(): Promise<UserWalletData> {
  return request.get<UserWalletData>(WALLET_ENDPOINTS.USER_SELF)
}

// ============================================================================
// 充值配置
// ============================================================================

/** 获取充值配置信息 */
export async function getTopupInfo(): Promise<TopupInfo> {
  return request.get<TopupInfo>(WALLET_ENDPOINTS.TOPUP_INFO)
}

/** 兑换码充值 */
export async function redeemTopupCode(req: RedemptionRequest): Promise<number> {
  return request.post<number>(WALLET_ENDPOINTS.TOPUP, req)
}

// ============================================================================
// 金额计算
// ============================================================================

/** 普通支付金额计算 */
export async function calculateAmount(req: AmountRequest): Promise<string> {
  return request.post<string>(WALLET_ENDPOINTS.AMOUNT, req)
}

/** Stripe 金额计算 */
export async function calculateStripeAmount(req: AmountRequest): Promise<string> {
  return request.post<string>(WALLET_ENDPOINTS.STRIPE_AMOUNT, req)
}

/** WaffoPancake 金额计算 */
export async function calculateWaffoPancakeAmount(req: AmountRequest): Promise<string> {
  return request.post<string>(WALLET_ENDPOINTS.WAFFO_PANCAKE_AMOUNT, req)
}

// ============================================================================
// 支付请求
// ============================================================================

/** 普通支付 */
export async function requestPayment(req: PaymentRequest): Promise<PaymentResponse> {
  return request.post<PaymentResponse>(WALLET_ENDPOINTS.PAY, req)
}

/** Stripe 支付 */
export async function requestStripePayment(req: PaymentRequest): Promise<StripePaymentResponse> {
  return request.post<StripePaymentResponse>(WALLET_ENDPOINTS.STRIPE_PAY, req)
}

/** Creem 支付 */
export async function requestCreemPayment(req: CreemPaymentRequest): Promise<CreemPaymentResponse> {
  return request.post<CreemPaymentResponse>(WALLET_ENDPOINTS.CREEM_PAY, req)
}

/** Waffo 支付 */
export async function requestWaffoPayment(req: WaffoPaymentRequest): Promise<WaffoPaymentResponse> {
  return request.post<WaffoPaymentResponse>(WALLET_ENDPOINTS.WAFFO_PAY, req)
}

/** WaffoPancake 支付 */
export async function requestWaffoPancakePayment(
  req: WaffoPancakePaymentRequest
): Promise<WaffoPancakePaymentResponse> {
  return request.post<WaffoPancakePaymentResponse>(WALLET_ENDPOINTS.WAFFO_PANCAKE_PAY, req)
}

// ============================================================================
// 邀请返利
// ============================================================================

/** 获取邀请码/链接 */
export async function getAffiliateCode(): Promise<string> {
  return request.get<string>(WALLET_ENDPOINTS.AFF)
}

/** 邀请额度转账 */
export async function transferAffiliateQuota(req: AffiliateTransferRequest): Promise<void> {
  return request.post<void>(WALLET_ENDPOINTS.AFF_TRANSFER, req)
}

// ============================================================================
// 账单历史
// ============================================================================

/** 获取当前用户账单历史 */
export async function getUserBillingHistory(
  pageNum: number,
  pageSize: number,
  keyword?: string
): Promise<PageInfo<TopupRecord>> {
  const params: Record<string, unknown> = { pageNum, pageSize }
  if (keyword) params.keyword = keyword
  return request.get<PageInfo<TopupRecord>>(WALLET_ENDPOINTS.BILLING_SELF, { params })
}

/** 获取所有用户账单历史（管理员） */
export async function getAllBillingHistory(
  pageNum: number,
  pageSize: number,
  keyword?: string
): Promise<PageInfo<TopupRecord>> {
  const params: Record<string, unknown> = { pageNum, pageSize }
  if (keyword) params.keyword = keyword
  return request.get<PageInfo<TopupRecord>>(WALLET_ENDPOINTS.BILLING_ALL, { params })
}

/** 完成订单（管理员） */
export async function completeOrder(req: CompleteOrderRequest): Promise<void> {
  return request.post<void>(WALLET_ENDPOINTS.COMPLETE_ORDER, req)
}
