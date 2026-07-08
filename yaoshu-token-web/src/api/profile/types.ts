// Vue3 侧遵循 camelCase 契约铁律（前端架构 §5.1），响应类型只标注 data 部分（拦截器已解包）
// UserProfile 复用 @/api/user/types 的 UserInfo，扩展 profile 专属字段

import type { UserInfo } from '@/api/user/types'

// ============================================================================
// 用户资料（扩展 UserInfo，补充 profile 专属字段）
// ============================================================================

/** GET /api/user/self 响应：完整用户资料 */
export interface UserProfile extends UserInfo {
  /** 系统访问令牌（生成后返回） */
  accessToken?: string
  /** 邀请码 */
  affCode?: string
  /** 成功邀请人数 */
  affCount?: number
  /** 待入账邀请收益 */
  affQuota?: number
  /** 累计邀请收益（历史） */
  affHistoryQuota?: number
  /** 邀请人 ID */
  inviterId?: number
  /** 账号创建时间戳 */
  createdAt?: number
  /** 用户设置（JSON string，解析为 UserSettings） */
  setting?: string
  /** Discord OAuth ID */
  discordId?: string
}

// ============================================================================
// 用户设置（JSON string 解析后的结构）
// ============================================================================

export type NotifyType = 'email' | 'webhook' | 'bark' | 'gotify'

export interface SidebarModuleConfig {
  showWallet?: boolean
  showTokens?: boolean
  showDashboard?: boolean
  showProfile?: boolean
  showCheckin?: boolean
}

export interface UserSettings {
  notifyType?: NotifyType
  quotaWarningThreshold?: number
  webhookUrl?: string
  webhookSecret?: string
  notificationEmail?: string
  barkUrl?: string
  gotifyUrl?: string
  gotifyToken?: string
  gotifyPriority?: number
  acceptUnsetModelRatioModel?: boolean
  recordIpLog?: boolean
  upstreamModelUpdateNotifyEnabled?: boolean
  language?: string
  sidebarModules?: SidebarModuleConfig
}

// ============================================================================
// 请求载荷
// ============================================================================

export interface UpdateUserRequest {
  displayName?: string
  password?: string
  originalPassword?: string
  language?: string
  email?: string
}

export interface UpdateUserSettingsRequest {
  notifyType?: string
  quotaWarningThreshold?: number
  webhookUrl?: string
  webhookSecret?: string
  notificationEmail?: string
  barkUrl?: string
  gotifyUrl?: string
  gotifyToken?: string
  gotifyPriority?: number
  acceptUnsetModelRatioModel?: boolean
  recordIpLog?: boolean
  upstreamModelUpdateNotifyEnabled?: boolean
  sidebarModules?: SidebarModuleConfig
}

export interface DeleteAccountRequest {
  password?: string
}

export interface BindEmailRequest {
  email: string
  code: string
}

// ============================================================================
// 2FA 类型
// ============================================================================

export interface TwoFAStatus {
  enabled: boolean
  locked: boolean
  backupCodesRemaining: number
}

export interface TwoFASetupData {
  secret: string
  qrCodeData: string
  backupCodes: string[]
}

export interface TwoFAVerifyRequest {
  code: string
  secret: string
}

export interface TwoFADisableRequest {
  password: string
}

// ============================================================================
// OAuth 绑定类型
// ============================================================================

export interface CustomOAuthBinding {
  providerId: string
  providerName: string
  externalId?: string
}

// ============================================================================
// 签到类型
// ============================================================================

export interface CheckinRecord {
  checkinDate: string
  quotaAwarded: number
}

export interface CheckinStats {
  checkedInToday: boolean
  totalCheckins: number
  totalQuota: number
  checkinCount: number
  records: CheckinRecord[]
}

export interface CheckinStatusResponse {
  enabled: boolean
  stats: CheckinStats
}

export interface CheckinResponse {
  quotaAwarded: number
}
