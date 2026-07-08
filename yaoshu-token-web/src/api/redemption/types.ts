/**
 * 兑换码管理类型。
 * 后端契约：ai-docs/后端设计/API_Contract/契约_数据统计与日志.md §五。
 * 字段全部 camelCase（CC-2 约束）。
 */
import type { PageInfo, PageParams } from '@/api/types'

/** 兑换码状态：1=未使用，2=已使用 */
export type RedemptionStatus = 1 | 2

/** 兑换码实体 */
export interface Redemption {
  id: number
  /** UUID 唯一码 */
  key: string
  /** 名称（≤20字符） */
  name: string
  status: RedemptionStatus
  /** 兑换配额增量 */
  quota: number
  /** Unix 秒，0=永不过期 */
  expiredTime: number
  /** 赎回者 ID */
  usedUserId?: number
  /** 赎回时间（Unix 秒） */
  redeemedTime?: number
  createdAt?: number
  updatedAt?: number
}

export type GetRedemptionsParams = PageParams

/** 搜索兑换码参数（keyword/status 为 URL query param） */
export interface SearchRedemptionsParams extends PageParams {
  keyword?: string
  status?: string
}

export type RedemptionsListData = PageInfo<Redemption>

/** 批量创建 Payload */
export interface CreateRedemptionPayload {
  name: string
  quota: number
  /** 1-100 */
  count: number
  /** Unix 秒，0=永久 */
  expiredTime: number
}

/** 更新 Payload */
export interface UpdateRedemptionPayload {
  id: number
  name?: string
  quota?: number
  expiredTime?: number
  /** true=仅状态，false=全量 */
  statusOnly?: boolean
  status?: RedemptionStatus
}

/** 表单数据（抽屉内编辑态） */
export interface RedemptionFormData {
  id?: number
  name: string
  quota: number
  /** 创建时用，编辑时隐藏 */
  count: number
  /** 0=永久，否则 Unix 秒 */
  expiredTime: number
}

/** 行操作类型 */
export type RedemptionRowAction = 'edit' | 'delete' | 'toggleStatus' | 'copyKey'
