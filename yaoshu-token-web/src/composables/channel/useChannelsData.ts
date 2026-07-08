/**
 * 渠道列表数据管理 composable。
 *
 * 职责：列表加载 / 搜索 / 筛选 / 排序 / 分页 / 选择 / 紧凑模式消费。
 * 不负责：表单编辑（useChannelMutateForm）/ 操作确认（useChannelOperations）。
 */

import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getChannels, searchChannels } from '@/api/channel'
import {
  CHANNEL_STATUS,
  DEFAULT_PAGE_SIZE
} from '@/api/channel/constants'
import type {
  Channel,
  ChannelSortBy,
  ChannelSortOrder,
  GetChannelsParams
} from '@/api/channel/types'
import { useTableCompactMode } from '@/composables/useTableCompactMode'
import { getSortFieldByProp } from '@/components/channel/ChannelsColumns'

/** 渠道列表筛选状态 */
export interface ChannelFilters {
  status: string
  type: number | undefined
  group: string
  tag: string
  keyword: string
}

/** 渠道列表排序状态 */
export interface ChannelSortState {
  sortBy: ChannelSortBy
  sortOrder: ChannelSortOrder
}

/** 渠道列表分页状态 */
export interface ChannelPagination {
  page: number
  pageSize: number
  total: number
}

const DEFAULT_FILTERS: ChannelFilters = {
  status: 'all',
  type: undefined,
  group: '',
  tag: '',
  keyword: ''
}

const DEFAULT_SORT: ChannelSortState = {
  sortBy: 'id',
  sortOrder: 'desc'
}

const DEFAULT_PAGINATION: ChannelPagination = {
  page: 1,
  pageSize: DEFAULT_PAGE_SIZE,
  total: 0
}

export function useChannelsData() {
  // ============================================================================
  // 状态
  // ============================================================================

  const channels = ref<Channel[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  const filters = reactive<ChannelFilters>({ ...DEFAULT_FILTERS })
  const sort = reactive<ChannelSortState>({ ...DEFAULT_SORT })
  const pagination = reactive<ChannelPagination>({ ...DEFAULT_PAGINATION })

  const selectedIds = ref<number[]>([])

  // 紧凑模式（T-CH-01，复用通用 composable）
  const [isCompact, setCompact] = useTableCompactMode('channels')

  function toggleCompact(): void {
    setCompact(!isCompact.value)
  }

  // ============================================================================
  // 计算属性
  // ============================================================================

  const isEmpty = computed(() => !loading.value && channels.value.length === 0)
  const hasSelection = computed(() => selectedIds.value.length > 0)
  const selectedCount = computed(() => selectedIds.value.length)
  const isAllSelected = computed(
    () =>
      channels.value.length > 0 &&
      selectedIds.value.length === channels.value.length
  )

  const enabledCount = computed(
    () => channels.value.filter((c) => c.status === CHANNEL_STATUS.ENABLED).length
  )
  const disabledCount = computed(
    () => channels.value.length - enabledCount.value
  )

  // ============================================================================
  // 数据加载
  // ============================================================================

  function buildParams(): GetChannelsParams {
    const params: GetChannelsParams = {
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
      id_sort: false,
      tag_mode: false
    }

    if (filters.status !== 'all') params.status = filters.status
    if (filters.type !== undefined) params.type = filters.type
    if (filters.group) params.group = filters.group
    if (filters.tag) params.tag = filters.tag

    if (sort.sortBy !== 'id') {
      params.sort_by = sort.sortBy
      params.sort_order = sort.sortOrder
    }

    return params
  }

  async function fetchChannels() {
    loading.value = true
    error.value = null

    try {
      // 关键词搜索走独立接口（与列表分页状态一致，SEARCH 端点同样要求 pageNum/pageSize）
      if (filters.keyword.trim()) {
        const res = await searchChannels({
          pageNum: pagination.page,
          pageSize: pagination.pageSize,
          keyword: filters.keyword.trim(),
          tag: filters.tag || undefined,
          group: filters.group || undefined,
          status: filters.status === 'all' ? undefined : filters.status
        })
        channels.value = res.list ?? []
        pagination.total = res.total ?? 0
      } else {
        const res = await getChannels(buildParams())
        channels.value = res.list ?? []
        pagination.total = res.total ?? 0
      }
      // 重置选择（数据变化后旧选择失效）
      selectedIds.value = []
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load channels'
      channels.value = []
      pagination.total = 0
      ElMessage.error(error.value)
    } finally {
      loading.value = false
    }
  }

  // ============================================================================
  // 操作方法
  // ============================================================================

  function handlePageChange(page: number) {
    pagination.page = page
    fetchChannels()
  }

  function handlePageSizeChange(size: number) {
    pagination.pageSize = size
    pagination.page = 1
    fetchChannels()
  }

  function handleSortChange({
    prop,
    order
  }: {
    prop: string
    order: 'ascending' | 'descending' | null
  }) {
    if (!order) {
      sort.sortBy = 'id'
      sort.sortOrder = 'desc'
    } else {
      const fieldKey = getSortFieldByProp(prop)
      if (fieldKey) {
        sort.sortBy = fieldKey
        sort.sortOrder = order === 'ascending' ? 'asc' : 'desc'
      }
    }
    pagination.page = 1
    fetchChannels()
  }

  function handleFilterChange() {
    pagination.page = 1
    fetchChannels()
  }

  function handleSearch() {
    pagination.page = 1
    fetchChannels()
  }

  function handleResetFilters() {
    Object.assign(filters, DEFAULT_FILTERS)
    Object.assign(sort, DEFAULT_SORT)
    pagination.page = 1
    fetchChannels()
  }

  function handleSelectionChange(ids: number[]) {
    selectedIds.value = ids
  }

  function handleSelectAll() {
    if (isAllSelected.value) {
      selectedIds.value = []
    } else {
      selectedIds.value = channels.value.map((c) => c.id)
    }
  }

  function refresh() {
    fetchChannels()
  }

  // ============================================================================
  // 监听筛选变化（防抖由调用方在 Toolbar 处理）
  // ============================================================================

  watch(
    () => filters.status,
    () => handleFilterChange()
  )
  watch(
    () => filters.type,
    () => handleFilterChange()
  )
  watch(
    () => filters.group,
    () => handleFilterChange()
  )

  onMounted(() => {
    fetchChannels()
  })

  return {
    // 状态
    channels,
    loading,
    error,
    filters,
    sort,
    pagination,
    selectedIds,
    isCompact,

    // 计算属性
    isEmpty,
    hasSelection,
    selectedCount,
    isAllSelected,
    enabledCount,
    disabledCount,

    // 操作
    fetchChannels,
    refresh,
    handlePageChange,
    handlePageSizeChange,
    handleSortChange,
    handleFilterChange,
    handleSearch,
    handleResetFilters,
    handleSelectionChange,
    handleSelectAll,
    toggleCompact
  }
}
