/**
 * useCheckin composable 单元测试。
 * 覆盖点：
 *   1. 初始状态（status/loading/checking/currentMonth 格式 YYYY-MM）
 *   2. fetchStatus 成功填充 status
 *   3. fetchStatus 失败 status=null
 *   4. fetchStatus 不传 month 时使用 currentMonth
 *   5. checkin 成功调用 performCheckin + fetchStatus + 返回 true
 *   6. checkin 失败返回 false
 *   7. changeMonth 更新 currentMonth 并触发 fetchStatus
 */
import { describe, expect, it, vi, beforeEach } from 'vitest'

const getCheckinStatusMock = vi.fn()
const performCheckinMock = vi.fn()

vi.mock('@/api/profile', () => ({
  getCheckinStatus: (...args: unknown[]) => getCheckinStatusMock(...args),
  performCheckin: (...args: unknown[]) => performCheckinMock(...args),
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

describe('useCheckin', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('初始状态正确，currentMonth 格式为 YYYY-MM', async () => {
    const { useCheckin } = await import('@/composables/profile/useCheckin')
    const { status, loading, checking, currentMonth } = useCheckin()

    expect(status.value).toBeNull()
    expect(loading.value).toBe(false)
    expect(checking.value).toBe(false)
    // 格式 YYYY-MM
    expect(currentMonth.value).toMatch(/^\d{4}-\d{2}$/)
  })

  it('fetchStatus 成功填充 status', async () => {
    const mockStatus = {
      enabled: true,
      stats: {
        checkedInToday: false,
        totalCheckins: 5,
        totalQuota: 1000,
        checkinCount: 5,
        records: [],
      },
    }
    getCheckinStatusMock.mockResolvedValueOnce(mockStatus)
    const { useCheckin } = await import('@/composables/profile/useCheckin')
    const { status, loading, fetchStatus } = useCheckin()

    await fetchStatus('2026-06')

    expect(getCheckinStatusMock).toHaveBeenCalledWith('2026-06')
    expect(status.value).toEqual(mockStatus)
    expect(loading.value).toBe(false)
  })

  it('fetchStatus 失败 status=null', async () => {
    getCheckinStatusMock.mockRejectedValueOnce(new Error('fail'))
    const { useCheckin } = await import('@/composables/profile/useCheckin')
    const { status, fetchStatus } = useCheckin()

    await fetchStatus('2026-06')

    expect(status.value).toBeNull()
  })

  it('fetchStatus 不传 month 时使用 currentMonth', async () => {
    getCheckinStatusMock.mockResolvedValueOnce({ enabled: true, stats: { checkedInToday: false } })
    const { useCheckin } = await import('@/composables/profile/useCheckin')
    const { currentMonth, fetchStatus } = useCheckin()

    await fetchStatus()

    expect(getCheckinStatusMock).toHaveBeenCalledWith(currentMonth.value)
  })

  it('checkin 成功调用 performCheckin + fetchStatus + 返回 true', async () => {
    performCheckinMock.mockResolvedValueOnce({ quotaAwarded: 500 })
    getCheckinStatusMock.mockResolvedValueOnce({ enabled: true, stats: { checkedInToday: true } })
    const { useCheckin } = await import('@/composables/profile/useCheckin')
    const { checkin, checking } = useCheckin()

    const result = await checkin('ts-token')

    expect(performCheckinMock).toHaveBeenCalledWith('ts-token')
    expect(getCheckinStatusMock).toHaveBeenCalledOnce() // fetchStatus 触发
    expect(result).toBe(true)
    expect(checking.value).toBe(false)
  })

  it('checkin 无 turnstile 时透传 undefined', async () => {
    performCheckinMock.mockResolvedValueOnce({ quotaAwarded: 500 })
    getCheckinStatusMock.mockResolvedValueOnce({ enabled: true, stats: { checkedInToday: true } })
    const { useCheckin } = await import('@/composables/profile/useCheckin')
    const { checkin } = useCheckin()

    await checkin()

    expect(performCheckinMock).toHaveBeenCalledWith(undefined)
  })

  it('checkin 失败返回 false', async () => {
    performCheckinMock.mockRejectedValueOnce(new Error('already checked in'))
    const { useCheckin } = await import('@/composables/profile/useCheckin')
    const { checkin, checking } = useCheckin()

    const result = await checkin()

    expect(result).toBe(false)
    expect(checking.value).toBe(false)
  })

  it('changeMonth 更新 currentMonth 并触发 fetchStatus', async () => {
    getCheckinStatusMock.mockResolvedValueOnce({ enabled: true, stats: { checkedInToday: false } })
    const { useCheckin } = await import('@/composables/profile/useCheckin')
    const { currentMonth, changeMonth } = useCheckin()

    await changeMonth('2026-05')

    expect(currentMonth.value).toBe('2026-05')
    expect(getCheckinStatusMock).toHaveBeenCalledWith('2026-05')
  })
})
