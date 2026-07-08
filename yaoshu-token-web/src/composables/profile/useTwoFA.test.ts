/**
 * useTwoFA composable 单元测试。
 * 覆盖点：
 *   1. 初始状态
 *   2. fetchStatus enabled=true 成功填充 status
 *   3. fetchStatus enabled=false 直接返回不请求
 *   4. fetchStatus 失败 status=null
 *   5. startSetup 成功填充 setupData
 *   6. startSetup 失败返回 false
 *   7. verify 无 setupData 直接返回 false
 *   8. verify 成功调用 verify2FA + fetchStatus
 *   9. verify 失败返回 false
 *  10. disable 成功调用 disable2FA + fetchStatus
 *  11. disable 失败返回 false
 *  12. fetchBackupCodes 成功填充 backupCodes
 *  13. fetchBackupCodes 失败清空 backupCodes
 */
import { describe, expect, it, vi, beforeEach } from 'vitest'

const get2FAStatusMock = vi.fn()
const get2FASecretMock = vi.fn()
const verify2FAMock = vi.fn()
const disable2FAMock = vi.fn()
const get2FABackupCodesMock = vi.fn()

vi.mock('@/api/profile', () => ({
  get2FAStatus: (...args: unknown[]) => get2FAStatusMock(...args),
  get2FASecret: (...args: unknown[]) => get2FASecretMock(...args),
  verify2FA: (...args: unknown[]) => verify2FAMock(...args),
  disable2FA: (...args: unknown[]) => disable2FAMock(...args),
  get2FABackupCodes: (...args: unknown[]) => get2FABackupCodesMock(...args),
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

describe('useTwoFA', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('初始状态正确', async () => {
    const { useTwoFA } = await import('@/composables/profile/useTwoFA')
    const {
      status,
      loading,
      setupData,
      setupLoading,
      verifying,
      disabling,
      backupCodes,
      backupLoading,
    } = useTwoFA()

    expect(status.value).toBeNull()
    expect(loading.value).toBe(false)
    expect(setupData.value).toBeNull()
    expect(setupLoading.value).toBe(false)
    expect(verifying.value).toBe(false)
    expect(disabling.value).toBe(false)
    expect(backupCodes.value).toEqual([])
    expect(backupLoading.value).toBe(false)
  })

  it('fetchStatus enabled=true 成功填充 status', async () => {
    const mockStatus = { enabled: true, locked: false, backupCodesRemaining: 5 }
    get2FAStatusMock.mockResolvedValueOnce(mockStatus)
    const { useTwoFA } = await import('@/composables/profile/useTwoFA')
    const { status, loading, fetchStatus } = useTwoFA(true)

    await fetchStatus()

    expect(get2FAStatusMock).toHaveBeenCalledOnce()
    expect(status.value).toEqual(mockStatus)
    expect(loading.value).toBe(false)
  })

  it('fetchStatus enabled=false 直接返回不请求', async () => {
    const { useTwoFA } = await import('@/composables/profile/useTwoFA')
    const { fetchStatus } = useTwoFA(false)

    await fetchStatus()

    expect(get2FAStatusMock).not.toHaveBeenCalled()
  })

  it('fetchStatus 失败 status=null', async () => {
    get2FAStatusMock.mockRejectedValueOnce(new Error('fail'))
    const { useTwoFA } = await import('@/composables/profile/useTwoFA')
    const { status, fetchStatus } = useTwoFA(true)

    await fetchStatus()

    expect(status.value).toBeNull()
  })

  it('startSetup 成功填充 setupData', async () => {
    const mockSetup = { secret: 'sec', qrCodeData: 'qr', backupCodes: ['c1'] }
    get2FASecretMock.mockResolvedValueOnce(mockSetup)
    const { useTwoFA } = await import('@/composables/profile/useTwoFA')
    const { setupData, setupLoading, startSetup } = useTwoFA()

    const result = await startSetup()

    expect(result).toBe(true)
    expect(setupData.value).toEqual(mockSetup)
    expect(setupLoading.value).toBe(false)
  })

  it('startSetup 失败返回 false', async () => {
    get2FASecretMock.mockRejectedValueOnce(new Error('fail'))
    const { useTwoFA } = await import('@/composables/profile/useTwoFA')
    const { startSetup, setupData } = useTwoFA()

    const result = await startSetup()

    expect(result).toBe(false)
    expect(setupData.value).toBeNull()
  })

  it('verify 无 setupData 直接返回 false', async () => {
    const { useTwoFA } = await import('@/composables/profile/useTwoFA')
    const { verify } = useTwoFA()

    const result = await verify('123456')

    expect(result).toBe(false)
    expect(verify2FAMock).not.toHaveBeenCalled()
  })

  it('verify 成功调用 verify2FA + fetchStatus', async () => {
    const mockSetup = { secret: 'sec', qrCodeData: 'qr', backupCodes: [] }
    get2FASecretMock.mockResolvedValueOnce(mockSetup)
    verify2FAMock.mockResolvedValueOnce(undefined)
    get2FAStatusMock.mockResolvedValueOnce({ enabled: true, locked: false, backupCodesRemaining: 5 })
    const { useTwoFA } = await import('@/composables/profile/useTwoFA')
    const { startSetup, verify } = useTwoFA(true)

    await startSetup()
    const result = await verify('123456')

    expect(verify2FAMock).toHaveBeenCalledWith({ code: '123456', secret: 'sec' })
    expect(get2FAStatusMock).toHaveBeenCalledOnce() // fetchStatus 触发
    expect(result).toBe(true)
  })

  it('verify 失败返回 false', async () => {
    const mockSetup = { secret: 'sec', qrCodeData: 'qr', backupCodes: [] }
    get2FASecretMock.mockResolvedValueOnce(mockSetup)
    verify2FAMock.mockRejectedValueOnce(new Error('invalid code'))
    const { useTwoFA } = await import('@/composables/profile/useTwoFA')
    const { startSetup, verify, verifying } = useTwoFA()

    await startSetup()
    const result = await verify('wrong')

    expect(result).toBe(false)
    expect(verifying.value).toBe(false)
  })

  it('disable 成功调用 disable2FA + fetchStatus', async () => {
    disable2FAMock.mockResolvedValueOnce(undefined)
    get2FAStatusMock.mockResolvedValueOnce({ enabled: false, locked: false, backupCodesRemaining: 0 })
    const { useTwoFA } = await import('@/composables/profile/useTwoFA')
    const { disable, disabling } = useTwoFA(true)

    const result = await disable('pwd123')

    expect(disable2FAMock).toHaveBeenCalledWith({ password: 'pwd123' })
    expect(get2FAStatusMock).toHaveBeenCalledOnce()
    expect(result).toBe(true)
    expect(disabling.value).toBe(false)
  })

  it('disable 失败返回 false', async () => {
    disable2FAMock.mockRejectedValueOnce(new Error('wrong pwd'))
    const { useTwoFA } = await import('@/composables/profile/useTwoFA')
    const { disable, disabling } = useTwoFA()

    const result = await disable('wrong')

    expect(result).toBe(false)
    expect(disabling.value).toBe(false)
  })

  it('fetchBackupCodes 成功填充 backupCodes', async () => {
    const mockCodes = ['c1', 'c2', 'c3']
    get2FABackupCodesMock.mockResolvedValueOnce(mockCodes)
    const { useTwoFA } = await import('@/composables/profile/useTwoFA')
    const { backupCodes, backupLoading, fetchBackupCodes } = useTwoFA()

    await fetchBackupCodes()

    expect(get2FABackupCodesMock).toHaveBeenCalledOnce()
    expect(backupCodes.value).toEqual(mockCodes)
    expect(backupLoading.value).toBe(false)
  })

  it('fetchBackupCodes 失败清空 backupCodes', async () => {
    get2FABackupCodesMock.mockRejectedValueOnce(new Error('fail'))
    const { useTwoFA } = await import('@/composables/profile/useTwoFA')
    const { backupCodes, fetchBackupCodes } = useTwoFA()

    await fetchBackupCodes()

    expect(backupCodes.value).toEqual([])
  })
})
