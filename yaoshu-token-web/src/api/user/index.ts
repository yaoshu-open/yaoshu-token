import { request } from '@/utils/request'
import { USER_ENDPOINTS, USE_MOCK } from './constants'
import type { CurrentUserResponse, UserAvailableModel, UserInfo } from './types'
import type {
  GetUsersParams,
  ManageUserAction,
  ManageUserQuotaPayload,
  SearchUsersParams,
  User,
  UserFormData,
  UsersListData,
} from './types'

// 获取当前登录用户信息：GET /api/user/self
export function getCurrentUser() {
  return request.get<CurrentUserResponse>('/api/user/self')
}

export type { UserInfo }

// ============================================================================
// 用户管理 API（M2 P2 第四批）
// ============================================================================

/** 获取用户列表（分页） */
export function getUsers(params: GetUsersParams = {}): Promise<UsersListData> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGetUsers(params))
  }
  const { pageNum = 1, pageSize = 20 } = params
  return request.get<UsersListData>(USER_ENDPOINTS.LIST, { params: { pageNum, pageSize } })
}

/** 搜索用户 */
export function searchUsers(params: SearchUsersParams): Promise<UsersListData> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockSearchUsers(params))
  }
  return request.get<UsersListData>(USER_ENDPOINTS.SEARCH, { params })
}

/** 获取单个用户详情 */
export function getUser(id: number): Promise<User> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockGetUser(id))
  }
  return request.get<User>(`${USER_ENDPOINTS.DETAIL}/${id}`)
}

/** 创建用户 */
export function createUser(data: UserFormData): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockCreateUser(data))
  }
  return request.post<void>(USER_ENDPOINTS.CREATE, data)
}

/** 更新用户 */
export function updateUser(data: UserFormData & { id: number }): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockUpdateUser(data))
  }
  return request.put<void>(USER_ENDPOINTS.UPDATE, data)
}

/** 删除用户 */
export function deleteUser(id: number): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockDeleteUser())
  }
  return request.delete<void>(`${USER_ENDPOINTS.DELETE}/${id}/`)
}

/** 管理用户（提升/降级/启用/禁用） */
export function manageUser(id: number, action: ManageUserAction): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockManageUser())
  }
  return request.post<void>(USER_ENDPOINTS.MANAGE, { id, action })
}

/** 调整用户额度 */
export function manageUserQuota(payload: ManageUserQuotaPayload): Promise<void> {
  if (USE_MOCK) {
    return import('./mock').then((m) => m.mockManageUser())
  }
  return request.post<void>(USER_ENDPOINTS.QUOTA, payload)
}

/** 获取当前用户可用模型列表（Dashboard SetupGuide curl 预览消费）。
 *  后端返回 OpenAI 兼容格式 {object: "list", data: [{id, ...}]}。 */
export async function getUserModels(): Promise<UserAvailableModel[]> {
  const data = await request.get<unknown>(USER_ENDPOINTS.USER_MODELS)
  if (data && typeof data === 'object' && 'data' in data) {
    const list = (data as { data: unknown }).data
    if (Array.isArray(list)) {
      return list
        .map((item) => {
          if (typeof item === 'string') return { id: item }
          const obj = item as { id?: string; name?: string }
          return { id: obj.id || '', name: obj.name }
        })
        .filter((item) => item.id)
    }
  }
  if (Array.isArray(data)) return data as UserAvailableModel[]
  return []
}
