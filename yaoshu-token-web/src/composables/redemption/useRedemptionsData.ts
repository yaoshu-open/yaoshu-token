/**
 * 兑换码列表数据管理 composable。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { getRedemptions, searchRedemptions } from '@/api/redemption'
import { DEFAULT_PAGE_SIZE } from '@/api/redemption/constants'
import type {
  GetRedemptionsParams,
  Redemption,
  SearchRedemptionsParams,
} from '@/api/redemption/types'
import { useTableCompactMode } from '@/composables/useTableCompactMode'

export interface RedemptionFilters {
  keyword: string
  status: string
}

export interface RedemptionPagination {
  page: number
  pageSize: number
  total: number
}

const DEFAULT_FILTERS: RedemptionFilters = { keyword: '', status: 'all' }
const DEFAULT_PAGINATION: RedemptionPagination = {
  page: 1,
  pageSize: DEFAULT_PAGE_SIZE,
  total: 0,
}

export function useRedemptionsData() {
  const redemptions = ref<Redemption[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const filters = reactive<RedemptionFilters>({ ...DEFAULT_FILTERS })
  const pagination = reactive<RedemptionPagination>({ ...DEFAULT_PAGINATION })
  const selectedIds = ref<number[]>([])

  const [isCompact, setCompact] = useTableCompactMode('redemptions')

  const hasSelection = computed(() => selectedIds.value.length > 0)
  const selectedCount = computed(() => selectedIds.value.length)

  async function fetchRedemptions(): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const hasKeyword = filters.keyword.trim().length > 0
      const hasStatus = filters.status !== 'all'
      if (hasKeyword || hasStatus) {
        const params: SearchRedemptionsParams = {
          keyword: filters.keyword,
          status: hasStatus ? filters.status : '',
          pageNum: pagination.page,
          pageSize: pagination.pageSize,
        }
        const data = await searchRedemptions(params)
        redemptions.value = data.list
        pagination.total = data.total
      } else {
        const params: GetRedemptionsParams = {
          pageNum: pagination.page,
          pageSize: pagination.pageSize,
        }
        const data = await getRedemptions(params)
        redemptions.value = data.list
        pagination.total = data.total
      }
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load redemptions'
      redemptions.value = []
      pagination.total = 0
    } finally {
      loading.value = false
    }
  }

  function handleSearch(): void {
    pagination.page = 1
    fetchRedemptions()
  }

  function handleResetFilters(): void {
    Object.assign(filters, DEFAULT_FILTERS)
    pagination.page = 1
    fetchRedemptions()
  }

  function handlePageChange(page: number): void {
    pagination.page = page
    fetchRedemptions()
  }

  function handlePageSizeChange(size: number): void {
    pagination.pageSize = size
    pagination.page = 1
    fetchRedemptions()
  }

  function handleSelectionChange(ids: number[]): void {
    selectedIds.value = ids
  }

  function clearSelection(): void {
    selectedIds.value = []
  }

  function toggleCompact(): void {
    setCompact(!isCompact.value)
  }

  onMounted(fetchRedemptions)

  return {
    redemptions,
    loading,
    error,
    filters,
    pagination,
    selectedIds,
    isCompact,
    hasSelection,
    selectedCount,
    fetchRedemptions,
    handleSearch,
    handleResetFilters,
    handlePageChange,
    handlePageSizeChange,
    handleSelectionChange,
    clearSelection,
    toggleCompact,
  }
}
