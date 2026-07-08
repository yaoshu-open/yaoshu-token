/**
 * 用户列表数据管理 composable。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { getUsers, searchUsers } from '@/api/user'
import { DEFAULT_PAGE_SIZE } from '@/api/user/constants'
import type { GetUsersParams, SearchUsersParams, User } from '@/api/user/types'
import { useTableCompactMode } from '@/composables/useTableCompactMode'

export interface UserFilters {
  keyword: string
  group: string
  role: string
  status: string
}

export interface UserPagination {
  page: number
  pageSize: number
  total: number
}

const DEFAULT_FILTERS: UserFilters = { keyword: '', group: '', role: 'all', status: 'all' }
const DEFAULT_PAGINATION: UserPagination = { page: 1, pageSize: DEFAULT_PAGE_SIZE, total: 0 }

export function useUsersData() {
  const users = ref<User[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const filters = reactive<UserFilters>({ ...DEFAULT_FILTERS })
  const pagination = reactive<UserPagination>({ ...DEFAULT_PAGINATION })
  const selectedIds = ref<number[]>([])

  const [isCompact, setCompact] = useTableCompactMode('users')

  const hasSelection = computed(() => selectedIds.value.length > 0)
  const selectedCount = computed(() => selectedIds.value.length)

  async function fetchUsers(): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const hasKeyword = filters.keyword.trim().length > 0 || filters.group.trim().length > 0
      if (hasKeyword || filters.role !== 'all' || filters.status !== 'all') {
        const params: SearchUsersParams = {
          keyword: filters.keyword,
          group: filters.group,
          role: filters.role !== 'all' ? filters.role : '',
          status: filters.status !== 'all' ? filters.status : '',
          pageNum: pagination.page,
          pageSize: pagination.pageSize,
        }
        const data = await searchUsers(params)
        users.value = data.list
        pagination.total = data.total
      } else {
        const params: GetUsersParams = { pageNum: pagination.page, pageSize: pagination.pageSize }
        const data = await getUsers(params)
        users.value = data.list
        pagination.total = data.total
      }
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load users'
      users.value = []
      pagination.total = 0
    } finally {
      loading.value = false
    }
  }

  function handleSearch(): void { pagination.page = 1; fetchUsers() }
  function handleResetFilters(): void { Object.assign(filters, DEFAULT_FILTERS); pagination.page = 1; fetchUsers() }
  function handlePageChange(page: number): void { pagination.page = page; fetchUsers() }
  function handlePageSizeChange(size: number): void { pagination.pageSize = size; pagination.page = 1; fetchUsers() }
  function handleSelectionChange(ids: number[]): void { selectedIds.value = ids }
  function clearSelection(): void { selectedIds.value = [] }
  function toggleCompact(): void { setCompact(!isCompact.value) }

  onMounted(fetchUsers)

  return {
    users, loading, error, filters, pagination, selectedIds, isCompact,
    hasSelection, selectedCount,
    fetchUsers, handleSearch, handleResetFilters,
    handlePageChange, handlePageSizeChange, handleSelectionChange, clearSelection, toggleCompact,
  }
}
