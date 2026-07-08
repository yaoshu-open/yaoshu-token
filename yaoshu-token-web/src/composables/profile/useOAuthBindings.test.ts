/**
 * useOAuthBindings composable 单元测试。
 * 覆盖点：
 *   1. 初始状态（bindings 空数组，loading false，unbinding false）
 *   2. fetchBindings 成功填充 bindings
 *   3. fetchBindings 失败清空 bindings
 *   4. unbind 成功调用 unbindCustomOAuth + fetchBindings + 返回 true
 *   5. unbind 失败返回 false
 */
import { describe, expect, it, vi, beforeEach } from 'vitest'

const getSelfOAuthBindingsMock = vi.fn()
const unbindCustomOAuthMock = vi.fn()

vi.mock('@/api/profile', () => ({
  getSelfOAuthBindings: (...args: unknown[]) => getSelfOAuthBindingsMock(...args),
  unbindCustomOAuth: (...args: unknown[]) => unbindCustomOAuthMock(...args),
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

describe('useOAuthBindings', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('初始状态正确', async () => {
    const { useOAuthBindings } = await import('@/composables/profile/useOAuthBindings')
    const { bindings, loading, unbinding } = useOAuthBindings()

    expect(bindings.value).toEqual([])
    expect(loading.value).toBe(false)
    expect(unbinding.value).toBe(false)
  })

  it('fetchBindings 成功填充 bindings', async () => {
    const mockBindings = [
      { providerId: 'github', providerName: 'GitHub', externalId: 'gh-123' },
      { providerId: 'oidc-1', providerName: 'Custom OIDC' },
    ]
    getSelfOAuthBindingsMock.mockResolvedValueOnce(mockBindings)
    const { useOAuthBindings } = await import('@/composables/profile/useOAuthBindings')
    const { bindings, loading, fetchBindings } = useOAuthBindings()

    await fetchBindings()

    expect(getSelfOAuthBindingsMock).toHaveBeenCalledOnce()
    expect(bindings.value).toEqual(mockBindings)
    expect(loading.value).toBe(false)
  })

  it('fetchBindings 失败清空 bindings', async () => {
    getSelfOAuthBindingsMock.mockRejectedValueOnce(new Error('fail'))
    const { useOAuthBindings } = await import('@/composables/profile/useOAuthBindings')
    const { bindings, fetchBindings } = useOAuthBindings()

    await fetchBindings()

    expect(bindings.value).toEqual([])
  })

  it('unbind 成功调用 unbindCustomOAuth + fetchBindings + 返回 true', async () => {
    unbindCustomOAuthMock.mockResolvedValueOnce(undefined)
    getSelfOAuthBindingsMock.mockResolvedValueOnce([])
    const { useOAuthBindings } = await import('@/composables/profile/useOAuthBindings')
    const { unbind, unbinding } = useOAuthBindings()

    const target = { providerId: 'github', providerName: 'GitHub' }
    const result = await unbind(target)

    expect(unbindCustomOAuthMock).toHaveBeenCalledWith('github')
    expect(getSelfOAuthBindingsMock).toHaveBeenCalledOnce() // fetchBindings 触发
    expect(result).toBe(true)
    expect(unbinding.value).toBe(false)
  })

  it('unbind 失败返回 false', async () => {
    unbindCustomOAuthMock.mockRejectedValueOnce(new Error('fail'))
    const { useOAuthBindings } = await import('@/composables/profile/useOAuthBindings')
    const { unbind, unbinding } = useOAuthBindings()

    const target = { providerId: 'discord', providerName: 'Discord' }
    const result = await unbind(target)

    expect(result).toBe(false)
    expect(unbinding.value).toBe(false)
    // 失败时不触发 fetchBindings
    expect(getSelfOAuthBindingsMock).not.toHaveBeenCalled()
  })
})
