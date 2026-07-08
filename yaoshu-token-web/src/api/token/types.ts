/**
 * 令牌管理 API 类型声明。
 * 后端契约：ai-docs/后端设计/API_Contract/契约_令牌管理.md。
 */
import type { PageInfo, PageParams } from '@/api/types'

// ============================================================================
// 实体类型
// ============================================================================

/** 令牌实体 */
export interface Token {
  id: number
  name: string
  key: string
  status: number
  remainQuota: number
  usedQuota: number
  unlimitedQuota: boolean
  expiredTime: number
  createdTime: number
  accessedTime: number
  group: string
  crossGroupRetry: boolean
  modelLimitsEnabled: boolean
  modelLimits: string
  allowIps: string
}

// ============================================================================
// 请求/响应类型
// ============================================================================

/** 获取令牌列表参数 */
export type GetTokensParams = PageParams

/** 搜索令牌参数 */
export interface SearchTokensParams extends PageParams {
  keyword?: string
  token?: string
}

/** 令牌列表响应（经拦截器解包后的业务数据，PageInfo 契约） */
export type TokensListData = PageInfo<Token>

// ============================================================================
// 表单数据类型
// ============================================================================

/** 令牌表单数据 */
export interface TokenFormData {
  id?: number
  name: string
  remainQuota: number
  expiredTime: number
  unlimitedQuota: boolean
  modelLimitsEnabled: boolean
  modelLimits: string
  allowIps: string
  group: string
  crossGroupRetry: boolean
}
