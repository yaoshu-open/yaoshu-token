/**
 * Profile API Mock 实现。
 * 后端契约未就绪时闭环，与 model/channel mock 模式一致。
 */
import { DEFAULT_QUOTA_WARNING_THRESHOLD } from './constants'
import type {
  BindEmailRequest,
  CheckinResponse,
  CheckinStatusResponse,
  CustomOAuthBinding,
  DeleteAccountRequest,
  TwoFADisableRequest,
  TwoFASetupData,
  TwoFAStatus,
  TwoFAVerifyRequest,
  UpdateUserRequest,
  UpdateUserSettingsRequest,
  UserProfile,
  UserSettings,
} from './types'

// ============================================================================
// Mock 数据
// ============================================================================

const mockSettings: UserSettings = {
  notifyType: 'email',
  quotaWarningThreshold: DEFAULT_QUOTA_WARNING_THRESHOLD,
  notificationEmail: '',
  webhookUrl: '',
  webhookSecret: '',
  barkUrl: '',
  gotifyUrl: '',
  gotifyToken: '',
  gotifyPriority: 5,
  acceptUnsetModelRatioModel: false,
  recordIpLog: false,
  upstreamModelUpdateNotifyEnabled: false,
  language: 'zh-CN',
}

const mockProfile: UserProfile = {
  id: 1,
  username: 'demo_user',
  displayName: 'Demo User',
  email: 'demo@example.com',
  role: 1,
  status: 1,
  group: 'default',
  quota: 5000000,
  usedQuota: 1200000,
  requestCount: 328,
  accessToken: 'sk-mock-token-xxxxxxxx',
  affCode: 'ABCDEF',
  affCount: 5,
  affQuota: 200000,
  affHistoryQuota: 800000,
  inviterId: 0,
  createdAt: 1710000000000,
  setting: JSON.stringify(mockSettings),
  githubId: undefined,
  discordId: undefined,
  oidcId: undefined,
  wechatId: undefined,
  telegramId: undefined,
  linuxDoId: undefined,
}

const mockTwoFAStatus: TwoFAStatus = {
  enabled: false,
  locked: false,
  backupCodesRemaining: 0,
}

const mockTwoFASetup: TwoFASetupData = {
  secret: 'JBSWY3DPEHPK3PXP',
  qrCodeData: 'otpauth://totp/yaoshu:demo_user?secret=JBSWY3DPEHPK3PXP&issuer=yaoshu',
  backupCodes: ['12345678', '87654321', '11223344', '44332211', '55667788', '88776655'],
}

const mockOAuthBindings: CustomOAuthBinding[] = []

// ============================================================================
// Mock 函数（带延迟模拟网络）
// ============================================================================

function delay(ms = 300): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export async function mockGetUserProfile(): Promise<UserProfile> {
  await delay()
  return { ...mockProfile }
}

export async function mockUpdateUserProfile(
  _data: UpdateUserRequest
): Promise<void> {
  await delay()
}

export async function mockUpdateUserSettings(
  _data: UpdateUserSettingsRequest
): Promise<void> {
  await delay()
}

export async function mockDeleteUserAccount(
  _data?: DeleteAccountRequest
): Promise<void> {
  await delay()
}

export async function mockGenerateAccessToken(): Promise<string> {
  await delay()
  return `sk-mock-token-${Date.now()}`
}

export async function mockSendEmailVerification(_email: string): Promise<void> {
  await delay(500)
}

export async function mockBindEmail(_data: BindEmailRequest): Promise<void> {
  await delay()
}

export async function mockBindWeChat(_code: string): Promise<void> {
  await delay()
}

export async function mockGetSelfOAuthBindings(): Promise<CustomOAuthBinding[]> {
  await delay()
  return [...mockOAuthBindings]
}

export async function mockUnbindCustomOAuth(_providerId: string): Promise<void> {
  await delay()
}

export async function mockGet2FAStatus(): Promise<TwoFAStatus> {
  await delay()
  return { ...mockTwoFAStatus }
}

export async function mockGet2FASecret(): Promise<TwoFASetupData> {
  await delay()
  return { ...mockTwoFASetup, backupCodes: [...mockTwoFASetup.backupCodes] }
}

export async function mockVerify2FA(_data: TwoFAVerifyRequest): Promise<void> {
  await delay()
}

export async function mockDisable2FA(_data: TwoFADisableRequest): Promise<void> {
  await delay()
}

export async function mockGet2FABackupCodes(): Promise<string[]> {
  await delay()
  return [...mockTwoFASetup.backupCodes]
}

export async function mockGetCheckinStatus(
  _month: string
): Promise<CheckinStatusResponse> {
  await delay()
  return {
    enabled: true,
    stats: {
      checkedInToday: false,
      totalCheckins: 12,
      totalQuota: 60000,
      checkinCount: 12,
      records: [
        { checkinDate: '2026-06-28', quotaAwarded: 5000 },
        { checkinDate: '2026-06-27', quotaAwarded: 5000 },
      ],
    },
  }
}

export async function mockPerformCheckin(): Promise<CheckinResponse> {
  await delay()
  return { quotaAwarded: 5000 }
}
