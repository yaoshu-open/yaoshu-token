/**
 * useAccessToken composable 单元测试。
 * 覆盖点：
 *   1. 初始状态（token 空字符串，loading false）
 *   2. generate 成功填充 token 并返回
 *   3. generate 失败返回空字符串，token 保持
 */
import { describe, expect, it, vi, beforeEach } from 'vitest'

const generateAccessTokenMock = vi.fn()

vi.mock('@/api/profile', () => ({
  generateAccessToken: (...args: unknown[]) => generateAccessTokenMock(...args),
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

describe('useAccessToken', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('初始状态正确', async () => {
    const { useAccessToken } = await import('@/composables/profile/useAccessToken')
    const { token, loading } = useAccessToken()

    expect(token.value).toBe('')
    expect(loading.value).toBe(false)
  })

  it('generate 成功填充 token 并返回', async () => {
    const mockToken = 'new-token-abc-123'
    generateAccessTokenMock.mockResolvedValueOnce(mockToken)
    const { useAccessToken } = await import('@/composables/profile/useAccessToken')
    const { token, loading, generate } = useAccessToken()

    const result = await generate()

    expect(generateAccessTokenMock).toHaveBeenCalledOnce()
    expect(token.value).toBe(mockToken)
    expect(result).toBe(mockToken)
    expect(loading.value).toBe(false)
  })

  it('generate 失败返回空字符串，token 保持原值', async () => {
    generateAccessTokenMock.mockRejectedValueOnce(new Error('fail'))
    const { useAccessToken } = await import('@/composables/profile/useAccessToken')
    const { token, loading, generate } = useAccessToken()

    const result = await generate()

    expect(result).toBe('')
    expect(token.value).toBe('') // 未被覆盖
    expect(loading.value).toBe(false)
  })
})
