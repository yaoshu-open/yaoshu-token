/**
 * 兑换码 API Service。
 * 后端契约：ai-docs/后端设计/API_Contract/契约_数据统计与日志.md §五。
 * 后端已就绪，无 mock 闭环。
 */
import { request } from '@/utils/request'
import { REDEMPTION_ENDPOINTS } from './constants'
import type {
  CreateRedemptionPayload,
  GetRedemptionsParams,
  Redemption,
  RedemptionsListData,
  SearchRedemptionsParams,
  UpdateRedemptionPayload,
} from './types'

/** 获取兑换码列表（分页） */
export function getRedemptions(
  params: GetRedemptionsParams = {}
): Promise<RedemptionsListData> {
  const { pageNum = 1, pageSize = 20 } = params
  return request.get<RedemptionsListData>(REDEMPTION_ENDPOINTS.LIST, {
    params: { pageNum, pageSize },
  })
}

/** 搜索兑换码 */
export function searchRedemptions(
  params: SearchRedemptionsParams
): Promise<RedemptionsListData> {
  return request.get<RedemptionsListData>(REDEMPTION_ENDPOINTS.SEARCH, { params })
}

/** 获取兑换码详情 */
export function getRedemption(id: number): Promise<Redemption> {
  return request.get<Redemption>(REDEMPTION_ENDPOINTS.DETAIL(id))
}

/** 批量创建兑换码 */
export function createRedemption(
  payload: CreateRedemptionPayload
): Promise<void> {
  return request.post<void>(REDEMPTION_ENDPOINTS.CREATE, payload)
}

/** 更新兑换码 */
export function updateRedemption(payload: UpdateRedemptionPayload): Promise<void> {
  return request.put<void>(REDEMPTION_ENDPOINTS.UPDATE, payload)
}

/** 删除单条兑换码 */
export function deleteRedemption(id: number): Promise<void> {
  return request.delete<void>(REDEMPTION_ENDPOINTS.DELETE(id))
}

/** 清理失效兑换码 */
export function clearInvalidRedemptions(): Promise<void> {
  return request.delete<void>(REDEMPTION_ENDPOINTS.DELETE_INVALID)
}
