/**
 * useRedemptionsData 单元测试。
 * 覆盖点：
 *   1. 初始状态（filters/pagination/redemptions）
 *   2. fetchRedemptions 走 list 分支（无 keyword/status）
 *   3. fetchRedemptions 走 search 分支（有 keyword）
 *   4. handleSearch 重置 page 到 1
 *   5. handleResetFilters 清空 filters
 *   6. handlePageChange / handlePageSizeChange 更新分页
 *   7. handleSelectionChange 更新 selectedIds
 */
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { ref } from 'vue'

// Mock API 模块
const getRedemptionsMock = vi.fn()
const searchRedemptionsMock = vi.fn()

vi.mock('@/api/redemption', () => ({
  getRedemptions: (...args: unknown[]) => getRedemptionsMock(...args),
  searchRedemptions: (...args: unknown[]) => searchRedemptionsMock(...args),
}))

// Mock useTableCompactMode（外部 composable 依赖，返回 ref 保持类型一致）
vi.mock('@/composables/useTableCompactMode', () => ({
  useTableCompactMode: () => [ref(false), vi.fn()] as const,
}))

// Mock onMounted（避免组件挂载副作用）
vi.mock('vue', async () => {
  const actual = await vi.importActual<typeof import('vue')>('vue')
  return {
    ...actual,
    onMounted: vi.fn((fn: () => void) => {
      // 不自动执行，由测试显式调用 fetchRedemptions
      void fn
    }),
  }
})

describe('useRedemptionsData', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('初始状态正确', async () => {
    const { useRedemptionsData } = await import('@/composables/redemption/useRedemptionsData')
    const {
      filters,
      pagination,
      redemptions,
      loading,
      error,
      selectedIds,
      isCompact,
      hasSelection,
    } = useRedemptionsData()

    expect(filters.keyword).toBe('')
    expect(filters.status).toBe('all')
    expect(pagination.page).toBe(1)
    expect(pagination.pageSize).toBe(20)
    expect(pagination.total).toBe(0)
    expect(redemptions.value).toEqual([])
    expect(loading.value).toBe(false)
    expect(error.value).toBeNull()
    expect(selectedIds.value).toEqual([])
    expect(isCompact.value).toBe(false)
    expect(hasSelection.value).toBe(false)
  })

  it('fetchRedemptions 无筛选走 list 分支', async () => {
    getRedemptionsMock.mockResolvedValueOnce({
      list: [{ id: 1, key: 'abc', name: 'n1', status: 1, quota: 100, expiredTime: 0 }],
      total: 1,
    })
    const { useRedemptionsData } = await import('@/composables/redemption/useRedemptionsData')
    const { fetchRedemptions, redemptions, pagination } = useRedemptionsData()

    await fetchRedemptions()

    expect(getRedemptionsMock).toHaveBeenCalledOnce()
    expect(getRedemptionsMock).toHaveBeenCalledWith({ pageNum: 1, pageSize: 20 })
    expect(searchRedemptionsMock).not.toHaveBeenCalled()
    expect(redemptions.value).toHaveLength(1)
    expect(pagination.total).toBe(1)
  })

  it('fetchRedemptions 有 keyword 走 search 分支', async () => {
    searchRedemptionsMock.mockResolvedValueOnce({ list: [], total: 0 })
    const { useRedemptionsData } = await import('@/composables/redemption/useRedemptionsData')
    const { fetchRedemptions, filters } = useRedemptionsData()

    filters.keyword = 'test'
    await fetchRedemptions()

    expect(searchRedemptionsMock).toHaveBeenCalledOnce()
    expect(searchRedemptionsMock).toHaveBeenCalledWith({
      keyword: 'test',
      status: '',
      pageNum: 1,
      pageSize: 20,
    })
    expect(getRedemptionsMock).not.toHaveBeenCalled()
  })

  it('fetchRedemptions 有 status 走 search 分支', async () => {
    searchRedemptionsMock.mockResolvedValueOnce({ list: [], total: 0 })
    const { useRedemptionsData } = await import('@/composables/redemption/useRedemptionsData')
    const { fetchRedemptions, filters } = useRedemptionsData()

    filters.status = '2'
    await fetchRedemptions()

    expect(searchRedemptionsMock).toHaveBeenCalledWith({
      keyword: '',
      status: '2',
      pageNum: 1,
      pageSize: 20,
    })
  })

  it('fetchRedemptions 失败设置 error', async () => {
    getRedemptionsMock.mockRejectedValueOnce(new Error('network error'))
    const { useRedemptionsData } = await import('@/composables/redemption/useRedemptionsData')
    const { fetchRedemptions, error, redemptions } = useRedemptionsData()

    await fetchRedemptions()

    expect(error.value).toBe('network error')
    expect(redemptions.value).toEqual([])
  })

  it('handleSearch 重置 page 到 1', async () => {
    const { useRedemptionsData } = await import('@/composables/redemption/useRedemptionsData')
    const { handleSearch, pagination, fetchRedemptions } = useRedemptionsData()

    pagination.page = 5
    // mock fetchRedemptions 避免真实请求
    const fetchSpy = vi.spyOn({ fetchRedemptions }, 'fetchRedemptions').mockResolvedValueOnce()
    // handleSearch 内部会调用 fetchRedemptions（来自闭包），无法直接 spy
    // 改为验证 page 被重置
    getRedemptionsMock.mockResolvedValueOnce({ list: [], total: 0 })
    handleSearch()

    expect(pagination.page).toBe(1)
    fetchSpy.mockRestore()
  })

  it('handleResetFilters 清空 filters 并重置 page', async () => {
    const { useRedemptionsData } = await import('@/composables/redemption/useRedemptionsData')
    const { handleResetFilters, filters, pagination } = useRedemptionsData()

    filters.keyword = 'abc'
    filters.status = '2'
    pagination.page = 5
    getRedemptionsMock.mockResolvedValueOnce({ list: [], total: 0 })

    handleResetFilters()

    expect(filters.keyword).toBe('')
    expect(filters.status).toBe('all')
    expect(pagination.page).toBe(1)
  })

  it('handlePageChange 更新 page', async () => {
    const { useRedemptionsData } = await import('@/composables/redemption/useRedemptionsData')
    const { handlePageChange, pagination } = useRedemptionsData()
    getRedemptionsMock.mockResolvedValueOnce({ list: [], total: 0 })

    handlePageChange(3)
    expect(pagination.page).toBe(3)
  })

  it('handlePageSizeChange 更新 pageSize 并重置 page', async () => {
    const { useRedemptionsData } = await import('@/composables/redemption/useRedemptionsData')
    const { handlePageSizeChange, pagination } = useRedemptionsData()
    pagination.page = 5
    getRedemptionsMock.mockResolvedValueOnce({ list: [], total: 0 })

    handlePageSizeChange(50)
    expect(pagination.pageSize).toBe(50)
    expect(pagination.page).toBe(1)
  })

  it('handleSelectionChange 更新 selectedIds', async () => {
    const { useRedemptionsData } = await import('@/composables/redemption/useRedemptionsData')
    const { handleSelectionChange, selectedIds, hasSelection, selectedCount } =
      useRedemptionsData()

    handleSelectionChange([1, 2, 3])
    expect(selectedIds.value).toEqual([1, 2, 3])
    expect(hasSelection.value).toBe(true)
    expect(selectedCount.value).toBe(3)
  })

  it('clearSelection 清空 selectedIds', async () => {
    const { useRedemptionsData } = await import('@/composables/redemption/useRedemptionsData')
    const { handleSelectionChange, clearSelection, selectedIds, hasSelection } =
      useRedemptionsData()

    handleSelectionChange([1, 2])
    expect(hasSelection.value).toBe(true)
    clearSelection()
    expect(selectedIds.value).toEqual([])
    expect(hasSelection.value).toBe(false)
  })
})
