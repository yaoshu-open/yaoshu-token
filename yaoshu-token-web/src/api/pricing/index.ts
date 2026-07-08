/**
 * Pricing API Service。
 * 后端契约：GET /api/pricing 返回 {success, message, data, vendors, group_ratio, ...}。
 *
 * Mock 闭环：DEV + VITE_PRICING_MOCK=true 时切换 mock 实现（动态 import 避免污染
 * production build），与 rankings/perf-metrics 模式一致。
 */
import { request } from '@/utils/request'
import { USE_MOCK } from './constants'
import type { PricingData } from './types'

/** 获取模型定价数据 */
export function getPricing(): Promise<PricingData> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGetPricing())
  }
  // /api/* 前缀由 request 拦截器自动解包 {success, message, data} → 返回完整响应体
  // pricing 响应的 data 字段是模型数组，vendors/group_ratio 等是顶层字段
  return request.get<PricingData>('/api/pricing')
}
