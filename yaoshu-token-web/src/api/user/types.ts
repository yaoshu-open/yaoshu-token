// /api/user/self 契约：M1-A 最小切片（仅 getCurrentUser，供 guards 链路消费）
// M1-B 扩展 login/register/logout/resetPassword 等

import type { Announcement } from '@/api/system/types'
import type { PageInfo, PageParams } from '@/api/types'

export interface UserInfo {
  id: string | number
  username: string
  displayName?: string
  email?: string
  role: number
  status: number
  group?: string
  quota?: number
  usedQuota?: number
  requestCount?: number
  // OAuth 平台 ID
  githubId?: string
  oidcId?: string
  wechatId?: string
  telegramId?: string
  linuxDoId?: string
  // 侧边栏个性化（与 useSidebarConfig 联动，JSON 字符串或对象）
  sidebarModules?: string | Record<string, unknown> | null
  permissions?: {
    sidebarSettings?: boolean
    [key: string]: unknown
  }
  // 其他后端追加字段（兼容）
  [key: string]: unknown
}

// /api/user/self 响应：直接 UserInfo（request 拦截器已对 /api/* 解包 data 部分）
export type CurrentUserResponse = UserInfo

// 复用 Announcement 类型（与 /api/status 共享）
export type { Announcement }

// ============================================================================
// 用户管理类型（M2 P2 第四批，扩展自认证类型）
// ============================================================================

/** 用户实体（管理用，含完整字段） */
export interface User {
  id: number
  username: string
  displayName: string
  password?: string
  githubId?: string
  oidcId?: string
  wechatId?: string
  telegramId?: string
  email?: string
  quota: number
  usedQuota: number
  requestCount: number
  group: string
  affCode?: string
  affCount?: number
  affQuota?: number
  affHistoryQuota?: number
  inviterId?: number
  linuxDoId?: string
  status: number
  role: number
  createdAt?: number
  updatedAt?: number
  lastLoginAt?: number
  remark?: string
}

export type GetUsersParams = PageParams

/** 搜索用户参数（keyword/group/role/status 为 URL query param） */
export interface SearchUsersParams extends PageParams {
  keyword?: string
  group?: string
  role?: string
  status?: string
}

/** 用户列表响应（经拦截器解包后的业务数据，PageInfo 契约） */
export type UsersListData = PageInfo<User>

/** 用户表单数据 */
export interface UserFormData {
  id?: number
  username: string
  displayName: string
  password?: string
  group: string
  role: number
  remark?: string
}

/** 用户管理操作类型 */
export type ManageUserAction =
  | 'promote'
  | 'demote'
  | 'enable'
  | 'disable'
  | 'delete'

/** 额度调整参数 */
export interface ManageUserQuotaPayload {
  userId: number
  quota: number
  action: 'add' | 'subtract' | 'override'
}

/** 用户可用模型列表项（GET /api/user/models） */
export interface UserAvailableModel {
  /** 模型 ID（API 调用时使用） */
  id: string
  /** 模型显示名（若有映射，否则同 id） */
  name?: string
}
