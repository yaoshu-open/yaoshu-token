import { request } from '@/utils/request'
import type {
  PlanRecord,
  PlanPayload,
  UserSubscriptionRecord,
  CreateUserSubscriptionRequest,
  ComplianceConfirmResponse,
  SelfSubscriptionData,
  SubscriptionPayRequest,
  SubscriptionPayResponse,
} from './types'

// ============ Admin Plan Management ============

export function getAdminPlans() {
  return request.get<PlanRecord[]>('/api/subscription/admin/plans')
}

export function createPlan(data: PlanPayload) {
  // Bug-FE-06: 后端 API 期望 {plan: {...}} 包装（对齐 React 原版 PlanPayload 契约）
  return request.post<PlanRecord>('/api/subscription/admin/plans', { plan: data })
}

export function updatePlan(id: number, data: PlanPayload) {
  return request.put<PlanRecord>(`/api/subscription/admin/plans/${id}`, { plan: data })
}

export function patchPlanStatus(id: number, enabled: boolean) {
  return request.patch(`/api/subscription/admin/plans/${id}`, { enabled })
}

// ============ Admin User Subscription Management ============

export function getUserSubscriptions(userId: number) {
  return request.get<UserSubscriptionRecord[]>(
    `/api/subscription/admin/users/${userId}/subscriptions`
  )
}

export function createUserSubscription(
  userId: number,
  data: CreateUserSubscriptionRequest
) {
  return request.post<UserSubscriptionRecord>(
    `/api/subscription/admin/users/${userId}/subscriptions`,
    data
  )
}

export function invalidateUserSubscription(id: number) {
  return request.post(`/api/subscription/admin/user_subscriptions/${id}/invalidate`)
}

export function deleteUserSubscription(id: number) {
  return request.delete(`/api/subscription/admin/user_subscriptions/${id}`)
}

// ============ User Self Subscriptions ============

export function getPublicPlans() {
  return request.get<PlanRecord[]>('/api/subscription/plans')
}

export function getSelfSubscriptionFull() {
  return request.get<SelfSubscriptionData>('/api/subscription/self', {
    _silent: true,
  })
}

export function updateBillingPreference(preference: string) {
  return request.put<{ billingPreference: string }>(
    '/api/subscription/self/preference',
    { billingPreference: preference }
  )
}

// ============ User Subscription Payment ============

export function paySubscriptionBalance(data: SubscriptionPayRequest) {
  return request.post<null>('/api/subscription/balance/pay', data)
}

export function paySubscriptionEpay(data: SubscriptionPayRequest) {
  return request.post<SubscriptionPayResponse>('/api/subscription/epay/pay', data)
}

export function paySubscriptionStripe(data: SubscriptionPayRequest) {
  return request.post<SubscriptionPayResponse>('/api/subscription/stripe/pay', data)
}

export function paySubscriptionCreem(data: SubscriptionPayRequest) {
  return request.post<SubscriptionPayResponse>('/api/subscription/creem/pay', data)
}

export function paySubscriptionWaffoPancake(data: SubscriptionPayRequest) {
  return request.post<SubscriptionPayResponse>(
    '/api/subscription/waffo-pancake/pay',
    data
  )
}

// ============ Compliance ============

export function getPaymentCompliance() {
  return request.get<ComplianceConfirmResponse>('/api/option/')
}

// ============ User Subscription Auto-Renew ============

/** 关闭自动续期（当前周期仍可用到到期，到期不再扣费） */
export function cancelSelfSubscription(): Promise<UserSubscriptionRecord[]> {
  return request.post<UserSubscriptionRecord[]>('/api/subscription/self/cancel')
}

/** 重新开启自动续期（关闭后可恢复，到期自动扣费续期） */
export function enableSelfAutoRenew(): Promise<UserSubscriptionRecord[]> {
  return request.post<UserSubscriptionRecord[]>('/api/subscription/self/renew/enable')
}
