/**
 * 令牌管理 API Service。
 * 后端契约：ai-docs/后端设计/API_Contract/契约_令牌管理.md。
 *
 * Mock 闭环：DEV + VITE_TOKEN_MOCK=true 时切换 mock 实现。
 */
import { request } from '@/utils/request'
import { TOKEN_ENDPOINTS, USE_MOCK } from './constants'
import type {
  GetTokensParams,
  SearchTokensParams,
  Token,
  TokenFormData,
  TokensListData,
} from './types'

// ============================================================================
// CRUD
// ============================================================================

/** 获取令牌列表（分页） */
export function getTokens(params: GetTokensParams = {}): Promise<TokensListData> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGetTokens(params))
  }
  const { pageNum = 1, pageSize = 10 } = params
  return request.get<TokensListData>(`${TOKEN_ENDPOINTS.LIST}?pageNum=${pageNum}&pageSize=${pageSize}`)
}

/** 搜索令牌 */
export function searchTokens(params: SearchTokensParams): Promise<TokensListData> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockSearchTokens(params))
  }
  return request.get<TokensListData>(TOKEN_ENDPOINTS.SEARCH, { params })
}

/** 获取令牌详情 */
export function getToken(id: number): Promise<Token> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGetToken(id))
  }
  return request.get<Token>(`${TOKEN_ENDPOINTS.DETAIL}/${id}`)
}

/** 创建令牌 */
export function createToken(data: TokenFormData): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockCreateToken())
  }
  return request.post<void>(TOKEN_ENDPOINTS.CREATE, data)
}

/** 更新令牌 */
export function updateToken(data: Partial<TokenFormData> & { id: number }): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockUpdateToken())
  }
  return request.put<void>(TOKEN_ENDPOINTS.UPDATE, data)
}

/** 删除令牌 */
export function deleteToken(id: number): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockDeleteToken())
  }
  return request.delete<void>(`${TOKEN_ENDPOINTS.DELETE}/${id}/`)
}

/** 批量删除令牌 */
export function batchDeleteTokens(ids: number[]): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockBatchDeleteTokens())
  }
  return request.post<void>(TOKEN_ENDPOINTS.BATCH_DELETE, { ids })
}

/** 更新令牌状态（仅状态） */
export function updateTokenStatus(id: number, status: number): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockUpdateToken())
  }
  return request.put<void>(`${TOKEN_ENDPOINTS.UPDATE}?status_only=true`, { id, status })
}

/** 获取令牌完整 Key（后端 CriticalRateLimit 限流，列表 API 返回脱敏 key） */
export function getTokenKey(id: number): Promise<string> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGetTokenKey(id))
  }
  // 后端返回 { key: "sk-xxx" }，拦截器解包后 data 即该对象，取 key 字段
  return request.post<{ key: string }>(`/api/token/${id}/key`).then((res) => res.key)
}
