/**
 * useProfile composable 单元测试。
 * 覆盖点：
 *   1. 初始状态（profile/loading/updating）
 *   2. fetchProfile 成功填充 profile，loading 切换
 *   3. fetchProfile 失败保持 profile=null，loading 归位
 *   4. refreshProfile 静默刷新（不触发 loading）
 *   5. updateProfile 成功调用 API + refreshProfile + 返回 true
 *   6. updateProfile 失败返回 false
 *   7. updateSettings 成功/失败分支
 *   8. onMounted 不自动执行（mock onMounted）
 */
import { describe, expect, it, vi, beforeEach } from 'vitest'

// Mock API 模块
const getUserProfileMock = vi.fn()
const updateUserProfileMock = vi.fn()
const updateUserSettingsMock = vi.fn()

vi.mock('@/api/profile', () => ({
  getUserProfile: (...args: unknown[]) => getUserProfileMock(...args),
  updateUserProfile: (...args: unknown[]) => updateUserProfileMock(...args),
  updateUserSettings: (...args: unknown[]) => updateUserSettingsMock(...args),
}))

// Mock element-plus 的 ElMessage（避免 jsdom 环境副作用）
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

// Mock vue-i18n（避免运行时 t() 调用）
vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

// Mock onMounted（避免组件挂载副作用）
vi.mock('vue', async () => {
  const actual = await vi.importActual<typeof import('vue')>('vue')
  return {
    ...actual,
    onMounted: vi.fn((fn: () => void) => {
      // 不自动执行，由测试显式调用 fetchProfile
      void fn
    }),
  }
})

describe('useProfile', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('初始状态正确', async () => {
    const { useProfile } = await import('@/composables/profile/useProfile')
    const { profile, loading, updating } = useProfile()

    expect(profile.value).toBeNull()
    // loading 初始为 true（onMounted 被 mock，fetchProfile 未执行，但初始值是 true）
    expect(loading.value).toBe(true)
    expect(updating.value).toBe(false)
  })

  it('fetchProfile 成功填充 profile 并切换 loading', async () => {
    const mockData = { id: 1, username: 'u1', displayName: 'U1' }
    getUserProfileMock.mockResolvedValueOnce(mockData)
    const { useProfile } = await import('@/composables/profile/useProfile')
    const { profile, loading, fetchProfile } = useProfile()

    await fetchProfile()

    expect(getUserProfileMock).toHaveBeenCalledOnce()
    expect(profile.value).toEqual(mockData)
    expect(loading.value).toBe(false)
  })

  it('fetchProfile 失败保持 profile=null，loading 归位', async () => {
    getUserProfileMock.mockRejectedValueOnce(new Error('network'))
    const { useProfile } = await import('@/composables/profile/useProfile')
    const { profile, loading, fetchProfile } = useProfile()

    await fetchProfile()

    expect(profile.value).toBeNull()
    expect(loading.value).toBe(false)
  })

  it('refreshProfile 静默刷新不触发 loading', async () => {
    const mockData = { id: 1, username: 'u1' }
    getUserProfileMock.mockResolvedValueOnce(mockData)
    const { useProfile } = await import('@/composables/profile/useProfile')
    const { loading, refreshProfile, profile } = useProfile()

    // loading 初始 true，静默刷新不应改变 loading 状态
    loading.value = false
    await refreshProfile()

    expect(getUserProfileMock).toHaveBeenCalledOnce()
    expect(profile.value).toEqual(mockData)
    expect(loading.value).toBe(false)
  })

  it('updateProfile 成功调用 API + refreshProfile + 返回 true', async () => {
    updateUserProfileMock.mockResolvedValueOnce(undefined)
    getUserProfileMock.mockResolvedValueOnce({ id: 1, displayName: 'new' })
    const { useProfile } = await import('@/composables/profile/useProfile')
    const { updateProfile, updating } = useProfile()

    const payload = { displayName: 'new' }
    const result = await updateProfile(payload)

    expect(updateUserProfileMock).toHaveBeenCalledWith(payload)
    expect(getUserProfileMock).toHaveBeenCalledOnce() // refreshProfile 触发
    expect(result).toBe(true)
    expect(updating.value).toBe(false)
  })

  it('updateProfile 失败返回 false', async () => {
    updateUserProfileMock.mockRejectedValueOnce(new Error('fail'))
    const { useProfile } = await import('@/composables/profile/useProfile')
    const { updateProfile, updating } = useProfile()

    const result = await updateProfile({ displayName: 'x' })

    expect(result).toBe(false)
    expect(updating.value).toBe(false)
  })

  it('updateSettings 成功调用 API + refreshProfile + 返回 true', async () => {
    updateUserSettingsMock.mockResolvedValueOnce(undefined)
    getUserProfileMock.mockResolvedValueOnce({ id: 1 })
    const { useProfile } = await import('@/composables/profile/useProfile')
    const { updateSettings, updating } = useProfile()

    const payload = { notifyType: 'email' }
    const result = await updateSettings(payload)

    expect(updateUserSettingsMock).toHaveBeenCalledWith(payload)
    expect(getUserProfileMock).toHaveBeenCalledOnce()
    expect(result).toBe(true)
    expect(updating.value).toBe(false)
  })

  it('updateSettings 失败返回 false', async () => {
    updateUserSettingsMock.mockRejectedValueOnce(new Error('fail'))
    const { useProfile } = await import('@/composables/profile/useProfile')
    const { updateSettings, updating } = useProfile()

    const result = await updateSettings({ notifyType: 'webhook' })

    expect(result).toBe(false)
    expect(updating.value).toBe(false)
  })
})
