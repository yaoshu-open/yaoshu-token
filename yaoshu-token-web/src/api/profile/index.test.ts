/**
 * Profile API Service 单元测试。
 * 覆盖点：
 *   1. 用户资料 5 个接口的真实请求路径（URL + method + params/body）
 *   2. 账号绑定 5 个接口的路径与参数透传
 *   3. 2FA 5 个接口的路径与 body
 *   4. 签到 2 个接口的 params 透传
 *
 * Mock 策略：mock @/utils/request 模块（核心依赖 request 层属外部边界，单测可 mock）。
 * Mock 开关 USE_MOCK 来自 constants.ts，测试环境 DEV=true 但 VITE_PROFILE_MOCK 默认未设置，
 * USE_MOCK=false，因此走真实 request 分支（被 mock）。
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = {
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
}

vi.mock('@/utils/request', () => ({
  request: requestMock,
}))

describe('profile API service', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('用户资料', () => {
    it('getUserProfile 走 GET /api/user/self', async () => {
      requestMock.get.mockResolvedValueOnce({ id: 1, username: 'u' })
      const { getUserProfile } = await import('@/api/profile')
      await getUserProfile()
      expect(requestMock.get).toHaveBeenCalledWith('/api/user/self')
    })

    it('updateUserProfile 走 PUT /api/user/self 透传 body', async () => {
      requestMock.put.mockResolvedValueOnce(undefined)
      const { updateUserProfile } = await import('@/api/profile')
      const payload = { displayName: 'newName', language: 'zh-CN' }
      await updateUserProfile(payload)
      expect(requestMock.put).toHaveBeenCalledWith('/api/user/self', payload)
    })

    it('updateUserSettings 走 PUT /api/user/setting 透传 body', async () => {
      requestMock.put.mockResolvedValueOnce(undefined)
      const { updateUserSettings } = await import('@/api/profile')
      const payload = { notifyType: 'email', quotaWarningThreshold: 500000 }
      await updateUserSettings(payload)
      expect(requestMock.put).toHaveBeenCalledWith('/api/user/setting', payload)
    })

    it('deleteUserAccount 走 DELETE /api/user/self 透传 body', async () => {
      requestMock.delete.mockResolvedValueOnce(undefined)
      const { deleteUserAccount } = await import('@/api/profile')
      const payload = { password: 'pwd123' }
      await deleteUserAccount(payload)
      expect(requestMock.delete).toHaveBeenCalledWith('/api/user/self', { data: payload })
    })

    it('deleteUserAccount 无参数时 data 为 undefined', async () => {
      requestMock.delete.mockResolvedValueOnce(undefined)
      const { deleteUserAccount } = await import('@/api/profile')
      await deleteUserAccount()
      expect(requestMock.delete).toHaveBeenCalledWith('/api/user/self', { data: undefined })
    })

    it('generateAccessToken 走 GET /api/user/token', async () => {
      requestMock.get.mockResolvedValueOnce('token-abc')
      const { generateAccessToken } = await import('@/api/profile')
      const token = await generateAccessToken()
      expect(requestMock.get).toHaveBeenCalledWith('/api/user/token')
      expect(token).toBe('token-abc')
    })
  })

  describe('账号绑定', () => {
    it('sendEmailVerification 透传 email + turnstile', async () => {
      requestMock.get.mockResolvedValueOnce(undefined)
      const { sendEmailVerification } = await import('@/api/profile')
      await sendEmailVerification('a@b.com', 'ts-token')
      expect(requestMock.get).toHaveBeenCalledWith('/api/verification', {
        params: { email: 'a@b.com', turnstile: 'ts-token' },
      })
    })

    it('sendEmailVerification 无 turnstile 时只传 email', async () => {
      requestMock.get.mockResolvedValueOnce(undefined)
      const { sendEmailVerification } = await import('@/api/profile')
      await sendEmailVerification('a@b.com')
      expect(requestMock.get).toHaveBeenCalledWith('/api/verification', {
        params: { email: 'a@b.com' },
      })
    })

    it('bindEmail 走 POST /api/oauth/email/bind 透传 body', async () => {
      requestMock.post.mockResolvedValueOnce(undefined)
      const { bindEmail } = await import('@/api/profile')
      const payload = { email: 'a@b.com', code: '123456' }
      await bindEmail(payload)
      expect(requestMock.post).toHaveBeenCalledWith('/api/oauth/email/bind', payload)
    })

    it('bindWeChat 走 GET /api/oauth/wechat/bind 透传 code', async () => {
      requestMock.get.mockResolvedValueOnce(undefined)
      const { bindWeChat } = await import('@/api/profile')
      await bindWeChat('wx-code-123')
      expect(requestMock.get).toHaveBeenCalledWith('/api/oauth/wechat/bind', {
        params: { code: 'wx-code-123' },
      })
    })

    it('getSelfOAuthBindings 走 GET /api/user/oauth/bindings', async () => {
      requestMock.get.mockResolvedValueOnce([])
      const { getSelfOAuthBindings } = await import('@/api/profile')
      await getSelfOAuthBindings()
      expect(requestMock.get).toHaveBeenCalledWith('/api/user/oauth/bindings')
    })

    it('unbindCustomOAuth 走 DELETE /api/user/oauth/bindings/:providerId', async () => {
      requestMock.delete.mockResolvedValueOnce(undefined)
      const { unbindCustomOAuth } = await import('@/api/profile')
      await unbindCustomOAuth('github')
      expect(requestMock.delete).toHaveBeenCalledWith('/api/user/oauth/bindings/github')
    })
  })

  describe('2FA', () => {
    it('get2FAStatus 走 GET /api/user/2fa/status', async () => {
      requestMock.get.mockResolvedValueOnce({ enabled: false, locked: false, backupCodesRemaining: 0 })
      const { get2FAStatus } = await import('@/api/profile')
      await get2FAStatus()
      expect(requestMock.get).toHaveBeenCalledWith('/api/user/2fa/status')
    })

    it('get2FASecret 走 GET /api/user/2fa/secret', async () => {
      requestMock.get.mockResolvedValueOnce({ secret: 's', qrCodeData: 'qr', backupCodes: [] })
      const { get2FASecret } = await import('@/api/profile')
      await get2FASecret()
      expect(requestMock.get).toHaveBeenCalledWith('/api/user/2fa/secret')
    })

    it('verify2FA 走 POST /api/user/2fa/verify 透传 body', async () => {
      requestMock.post.mockResolvedValueOnce(undefined)
      const { verify2FA } = await import('@/api/profile')
      const payload = { code: '123456', secret: 'abc' }
      await verify2FA(payload)
      expect(requestMock.post).toHaveBeenCalledWith('/api/user/2fa/verify', payload)
    })

    it('disable2FA 走 DELETE /api/user/2fa 透传 body', async () => {
      requestMock.delete.mockResolvedValueOnce(undefined)
      const { disable2FA } = await import('@/api/profile')
      const payload = { password: 'pwd' }
      await disable2FA(payload)
      expect(requestMock.delete).toHaveBeenCalledWith('/api/user/2fa', { data: payload })
    })

    it('get2FABackupCodes 走 GET /api/user/2fa/backup', async () => {
      requestMock.get.mockResolvedValueOnce(['code1', 'code2'])
      const { get2FABackupCodes } = await import('@/api/profile')
      const codes = await get2FABackupCodes()
      expect(requestMock.get).toHaveBeenCalledWith('/api/user/2fa/backup')
      expect(codes).toEqual(['code1', 'code2'])
    })
  })

  describe('签到', () => {
    it('getCheckinStatus 透传 month 参数', async () => {
      requestMock.get.mockResolvedValueOnce({ enabled: true, stats: { checkedInToday: false } })
      const { getCheckinStatus } = await import('@/api/profile')
      await getCheckinStatus('2026-06')
      expect(requestMock.get).toHaveBeenCalledWith('/api/user/checkin', {
        params: { month: '2026-06' },
        _silent: true,
      })
    })

    it('performCheckin 无 turnstile 时 params 为空对象', async () => {
      requestMock.post.mockResolvedValueOnce({ quotaAwarded: 1000 })
      const { performCheckin } = await import('@/api/profile')
      await performCheckin()
      expect(requestMock.post).toHaveBeenCalledWith(
        '/api/user/checkin',
        undefined,
        { params: {}, _silent: true }
      )
    })

    it('performCheckin 有 turnstile 时透传', async () => {
      requestMock.post.mockResolvedValueOnce({ quotaAwarded: 1000 })
      const { performCheckin } = await import('@/api/profile')
      await performCheckin('ts-token')
      expect(requestMock.post).toHaveBeenCalledWith(
        '/api/user/checkin',
        undefined,
        { params: { turnstile: 'ts-token' }, _silent: true }
      )
    })
  })
})
