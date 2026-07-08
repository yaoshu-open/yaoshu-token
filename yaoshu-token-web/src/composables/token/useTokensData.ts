/**
 * 令牌列表数据管理 composable。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { getTokens, searchTokens } from '@/api/token'
import { DEFAULT_PAGE_SIZE } from '@/api/token/constants'
import type { GetTokensParams, SearchTokensParams, Token } from '@/api/token/types'
import { useTableCompactMode } from '@/composables/useTableCompactMode'

export interface TokenFilters {
  keyword: string
  status: string
}

export interface TokenPagination {
  page: number
  pageSize: number
  total: number
}

const DEFAULT_FILTERS: TokenFilters = { keyword: '', status: 'all' }
const DEFAULT_PAGINATION: TokenPagination = { page: 1, pageSize: DEFAULT_PAGE_SIZE, total: 0 }

export function useTokensData() {
  const tokens = ref<Token[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const filters = reactive<TokenFilters>({ ...DEFAULT_FILTERS })
  const pagination = reactive<TokenPagination>({ ...DEFAULT_PAGINATION })
  const selectedIds = ref<number[]>([])

  const [isCompact, setCompact] = useTableCompactMode('tokens')

  const hasSelection = computed(() => selectedIds.value.length > 0)
  const selectedCount = computed(() => selectedIds.value.length)

  async function fetchTokens(): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const hasKeyword = filters.keyword.trim().length > 0
      if (hasKeyword) {
        const params: SearchTokensParams = {
          keyword: filters.keyword,
          pageNum: pagination.page,
          pageSize: pagination.pageSize,
        }
        const data = await searchTokens(params)
        tokens.value = data.list
        pagination.total = data.total
      } else {
        const params: GetTokensParams = { pageNum: pagination.page, pageSize: pagination.pageSize }
        const data = await getTokens(params)
        tokens.value = data.list
        pagination.total = data.total
      }
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load tokens'
      tokens.value = []
      pagination.total = 0
    } finally {
      loading.value = false
    }
  }

  function handleSearch(): void { pagination.page = 1; fetchTokens() }
  function handleResetFilters(): void { Object.assign(filters, DEFAULT_FILTERS); pagination.page = 1; fetchTokens() }
  function handlePageChange(page: number): void { pagination.page = page; fetchTokens() }
  function handlePageSizeChange(size: number): void { pagination.pageSize = size; pagination.page = 1; fetchTokens() }
  function handleSelectionChange(ids: number[]): void { selectedIds.value = ids }
  function clearSelection(): void { selectedIds.value = [] }
  function toggleCompact(): void { setCompact(!isCompact.value) }

  onMounted(fetchTokens)

  return {
    tokens, loading, error, filters, pagination, selectedIds, isCompact,
    hasSelection, selectedCount,
    fetchTokens, handleSearch, handleResetFilters,
    handlePageChange, handlePageSizeChange, handleSelectionChange, clearSelection, toggleCompact,
  }
}
