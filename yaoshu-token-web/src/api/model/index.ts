/**
 * 模型管理 API Service。
 * 后端契约：ai-docs/后端设计/API_Contract/契约_模型与部署.md。
 *
 * Mock 闭环：DEV + VITE_MODEL_MOCK=true 时切换 mock 实现（动态 import 避免污染
 * production build），与 channel/deployment 模式一致。
 *
 * request 工具经拦截器解包后直接返回 data，类型由调用方泛型标注。
 */
import { request } from '@/utils/request'
import type { PageParams } from '@/api/types'
import { MODEL_ENDPOINTS, USE_MOCK, VENDOR_ENDPOINTS, PREFILL_GROUP_ENDPOINTS } from './constants'
import type {
  GetModelsParams,
  Model,
  ModelFormData,
  ModelsListData,
  PrefillGroup,
  PrefillGroupFormData,
  SearchModelsParams,
  SyncDiffData,
  SyncLocale,
  SyncOverwritePayload,
  SyncSource,
  SyncUpstreamData,
  VendorFormData,
  VendorsListData,
} from './types'

// ============================================================================
// 模型 CRUD
// ============================================================================

/** 获取模型列表（分页） */
export function getModels(params: GetModelsParams = {}): Promise<ModelsListData> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGetModels(params))
  }
  return request.get<ModelsListData>(MODEL_ENDPOINTS.LIST, { params })
}

/** 搜索模型 */
export function searchModels(params: SearchModelsParams): Promise<ModelsListData> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockSearchModels(params))
  }
  return request.get<ModelsListData>(MODEL_ENDPOINTS.SEARCH, { params })
}

/** 获取模型详情 */
export function getModel(id: number): Promise<Model> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGetModel(id))
  }
  return request.get<Model>(`${MODEL_ENDPOINTS.DETAIL}/${id}`)
}

/** 创建模型 */
export function createModel(data: ModelFormData): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockCreateModel())
  }
  return request.post<void>(MODEL_ENDPOINTS.CREATE, data)
}

/** 更新模型 */
export function updateModel(data: Partial<ModelFormData> & { id: number }): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockUpdateModel())
  }
  return request.put<void>(MODEL_ENDPOINTS.UPDATE, data)
}

/** 删除模型 */
export function deleteModel(id: number): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockDeleteModel())
  }
  return request.delete<void>(`${MODEL_ENDPOINTS.DELETE}/${id}`)
}

// ============================================================================
// 缺失模型扫描
// ============================================================================

/** 获取缺失模型列表（已使用但未配置） */
export function getMissingModels(): Promise<string[]> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGetMissingModels())
  }
  return request.get<string[]>(MODEL_ENDPOINTS.MISSING)
}

// ============================================================================
// 供应商 CRUD
// ============================================================================

/** 获取供应商列表 */
export function getVendors(params?: PageParams): Promise<VendorsListData> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGetVendors())
  }
  return request.get<VendorsListData>(VENDOR_ENDPOINTS.LIST, {
    // pageNum 必传：后端 PageHelper 缺 pageNum 抛 433
    params: { pageNum: 1, pageSize: 1000, ...params },
  })
}

/** 搜索供应商 */
export function searchVendors(keyword: string): Promise<VendorsListData> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockSearchVendors(keyword))
  }
  return request.get<VendorsListData>(VENDOR_ENDPOINTS.SEARCH, { params: { keyword } })
}

/** 创建供应商 */
export function createVendor(data: VendorFormData): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockCreateVendor())
  }
  return request.post<void>(VENDOR_ENDPOINTS.CREATE, data)
}

/** 更新供应商 */
export function updateVendor(data: VendorFormData & { id: number }): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockUpdateVendor())
  }
  return request.put<void>(VENDOR_ENDPOINTS.UPDATE, data)
}

/** 删除供应商 */
export function deleteVendor(id: number): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockDeleteVendor())
  }
  return request.delete<void>(`${VENDOR_ENDPOINTS.DELETE}/${id}`)
}

// ============================================================================
// 上游同步
// ============================================================================

/** 预览上游同步差异 */
export function previewUpstreamDiff(params?: {
  locale?: SyncLocale
  source?: SyncSource
}): Promise<SyncDiffData> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockPreviewUpstreamDiff())
  }
  return request.get<SyncDiffData>(MODEL_ENDPOINTS.SYNC_PREVIEW, { params })
}

/** 正式同步上游模型 */
export function syncUpstream(params?: {
  locale?: SyncLocale
  source?: SyncSource
  overwrite?: SyncOverwritePayload[]
}): Promise<SyncUpstreamData> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockSyncUpstream())
  }
  return request.post<SyncUpstreamData>(MODEL_ENDPOINTS.SYNC, params)
}

// ============================================================================
// 预填组管理
// ============================================================================

/** 获取预填组列表 */
export function getPrefillGroups(type?: 'model' | 'tag' | 'endpoint'): Promise<PrefillGroup[]> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGetPrefillGroups())
  }
  return request.get<PrefillGroup[]>(PREFILL_GROUP_ENDPOINTS.LIST, {
    params: type ? { type } : undefined,
  })
}

/** 创建预填组 */
export function createPrefillGroup(data: PrefillGroupFormData): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockCreatePrefillGroup(data))
  }
  return request.post<void>(PREFILL_GROUP_ENDPOINTS.CREATE, data)
}

/** 更新预填组 */
export function updatePrefillGroup(data: PrefillGroupFormData & { id: number }): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockUpdatePrefillGroup(data))
  }
  return request.put<void>(PREFILL_GROUP_ENDPOINTS.UPDATE, data)
}

/** 删除预填组 */
export function deletePrefillGroup(id: number): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockDeletePrefillGroup(id))
  }
  return request.delete<void>(`${PREFILL_GROUP_ENDPOINTS.DELETE}/${id}`)
}
