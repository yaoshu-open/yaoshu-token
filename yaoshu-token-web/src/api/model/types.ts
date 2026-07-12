/**
 * 模型管理 API 类型声明。
 * 后端契约：ai-docs/后端设计/API_Contract/契约_模型与部署.md。
 *
 * 不含 Deployment 相关类型（已由独立 deployment 模块覆盖）。
 */
import type { PageInfo, PageParams } from '@/api/types'

// ============================================================================
// 实体类型
// ============================================================================

/** 绑定渠道信息 */
export interface BoundChannel {
  name: string
  type: number
}

/** 模型实体 */
export interface Model {
  id: number
  modelName: string
  description?: string
  icon?: string
  tags?: string
  vendorId?: number
  endpoints?: string
  status: number
  syncOfficial: number
  createdTime: number
  updatedTime: number
  nameRule: number
  // 运行时字段
  boundChannels?: BoundChannel[]
  enableGroups?: string[]
  quotaTypes?: number[]
  matchedModels?: string[]
  matchedCount?: number
}

/** 供应商实体 */
export interface Vendor {
  id: number
  name: string
  description?: string
  icon?: string
  status: number
  createdTime: number
  updatedTime: number
}

/** 预填组实体 */
export interface PrefillGroup {
  id: number
  name: string
  type: 'model' | 'tag' | 'endpoint'
  items: string | string[]
  description?: string
}

// ============================================================================
// 请求/响应类型
// ============================================================================

/** 获取模型列表参数 */
export interface GetModelsParams extends PageParams {
  vendor?: string
  status?: string
  sync_official?: string
}

/** 搜索模型参数 */
export interface SearchModelsParams extends PageParams {
  keyword?: string
  vendor?: string
  status?: string
  sync_official?: string
}

/** 模型列表响应（经拦截器解包后的业务数据，PageInfo 契约 + 供应商计数） */
export type ModelsListData = PageInfo<Model> & {
  vendorCounts?: Record<string, number>
}

/** 供应商列表响应（经拦截器解包后的业务数据，PageInfo 契约） */
export type VendorsListData = PageInfo<Vendor>

/** 同步差异数据 */
export interface SyncDiffData {
  missing?: Array<{
    modelName: string
    vendor?: string
    [key: string]: unknown
  }>
  conflicts?: Array<{
    modelName: string
    local?: Partial<Model>
    upstream?: Partial<Model>
    fields?: Array<{
      field: string
      local?: unknown
      upstream?: unknown
    }>
    [key: string]: unknown
  }>
}

/** 同步覆盖载荷 */
export interface SyncOverwritePayload {
  modelName: string
  fields: string[]
}

/** 同步上游响应（经拦截器解包后的业务数据） */
export interface SyncUpstreamData {
  createdModels?: number
  updatedModels?: number
  createdVendors?: number
  skippedModels?: string[]
}

// ============================================================================
// 表单数据类型
// ============================================================================

/** 模型表单数据 */
export interface ModelFormData {
  id?: number
  modelName: string
  description: string
  icon: string
  tags: string[]
  vendorId?: number
  endpoints: string
  nameRule: number
  status: number
  syncOfficial: boolean
}

/** 供应商表单数据 */
export interface VendorFormData {
  id?: number
  name: string
  description: string
  icon: string
  status: number
}

/** 预填组表单数据 */
export interface PrefillGroupFormData {
  id?: number
  name: string
  description?: string
  type: 'model' | 'tag' | 'endpoint'
  items: string | string[]
}

// ============================================================================
// 工具类型
// ============================================================================

/** 名称匹配规则 */
export type NameRule = 0 | 1 | 2 | 3

/** 模型状态 */
export type ModelStatus = 0 | 1

/** 配额类型 */
export type QuotaType = 0 | 1

/** 同步语言 */
export type SyncLocale = 'zh' | 'en' | 'ja'

/** 同步来源 */
export type SyncSource = 'official' | 'config'
