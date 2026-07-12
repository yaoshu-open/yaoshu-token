/**
 * 调用日志数据管理 composable。
 *
 * 职责：分页/筛选/三态/紧凑模式/统计/详情对话框状态。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { getAllLogs, getUserLogs, getLogStats, getUserLogStats } from '@/api/usage-log'
import { DEFAULT_PAGE_SIZE } from '@/api/usage-log/constants'
import type { GetLogsParams, LogStatistics, UsageLog } from '@/api/usage-log/types'
import { useTableCompactMode } from '@/composables/useTableCompactMode'
import { useUserPermissions } from '@/composables/useUserPermissions'

/** 筛选条件 */
export interface CommonLogsFilters {
  dateRange: [Date, Date] | null
  logType: number
  modelName: string
  group: string
  tokenName: string
  username: string
  channel: string
  requestId: string
  upstreamRequestId: string
}

/** 分页 */
export interface CommonLogsPagination {
  page: number
  pageSize: number
  total: number
}

const DEFAULT_FILTERS: CommonLogsFilters = {
  dateRange: getDefaultDateRange(),
  logType: 0,
  modelName: '',
  group: '',
  tokenName: '',
  username: '',
  channel: '',
  requestId: '',
  upstreamRequestId: '',
}

const DEFAULT_PAGINATION: CommonLogsPagination = {
  page: 1,
  pageSize: DEFAULT_PAGE_SIZE,
  total: 0,
}

function getDefaultDateRange(): [Date, Date] {
  const now = new Date()
  const sevenDaysAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000)
  return [sevenDaysAgo, now]
}

export function useCommonLogsData() {
  const { isAdmin } = useUserPermissions()

  const logs = ref<UsageLog[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const filters = reactive<CommonLogsFilters>({ ...DEFAULT_FILTERS, dateRange: getDefaultDateRange() })
  const pagination = reactive<CommonLogsPagination>({ ...DEFAULT_PAGINATION })

  const stats = ref<LogStatistics>({ quota: 0, rpm: 0, tpm: 0 })
  const statsLoading = ref(false)

  const [isCompact, setCompact] = useTableCompactMode('usageLogsCommon')

  const detailsDialog = reactive<{ visible: boolean; log: UsageLog | null }>({
    visible: false,
    log: null,
  })

  const hasData = computed(() => logs.value.length > 0)

  function buildParams(): GetLogsParams {
    const params: GetLogsParams = {
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
    }
    if (filters.logType !== 0) params.type = filters.logType
    if (filters.modelName.trim()) params.model_name = filters.modelName.trim()
    if (filters.group.trim()) params.group = filters.group.trim()
    if (filters.tokenName.trim()) params.token_name = filters.tokenName.trim()
    if (isAdmin.value && filters.username.trim()) params.username = filters.username.trim()
    if (isAdmin.value && filters.channel.trim()) params.channel = Number(filters.channel.trim()) || undefined
    if (filters.requestId.trim()) params.request_id = filters.requestId.trim()
    if (filters.upstreamRequestId.trim()) params.upstream_request_id = filters.upstreamRequestId.trim()
    if (filters.dateRange) {
      params.start_timestamp = Math.floor(filters.dateRange[0].getTime() / 1000)
      params.end_timestamp = Math.floor(filters.dateRange[1].getTime() / 1000)
    }
    return params
  }

  async function fetchLogs() {
    loading.value = true
    error.value = null
    try {
      const params = buildParams()
      const data = isAdmin.value ? await getAllLogs(params) : await getUserLogs(params)
      logs.value = data.list ?? []
      pagination.total = data.total ?? 0
    } catch (e) {
      error.value = e instanceof Error ? e.message : '加载失败'
      logs.value = []
      pagination.total = 0
    } finally {
      loading.value = false
    }
  }

  async function fetchStats() {
    statsLoading.value = true
    try {
      const params = buildParams()
      const data = isAdmin.value ? await getLogStats(params) : await getUserLogStats(params)
      stats.value = data ?? { quota: 0, rpm: 0, tpm: 0 }
    } catch {
      stats.value = { quota: 0, rpm: 0, tpm: 0 }
    } finally {
      statsLoading.value = false
    }
  }

  function handleSearch() {
    pagination.page = 1
    fetchLogs()
    fetchStats()
  }

  function handleResetFilters() {
    Object.assign(filters, { ...DEFAULT_FILTERS, dateRange: getDefaultDateRange() })
    pagination.page = 1
    fetchLogs()
    fetchStats()
  }

  function handlePageChange(page: number) {
    pagination.page = page
    fetchLogs()
  }

  function handlePageSizeChange(size: number) {
    pagination.pageSize = size
    pagination.page = 1
    fetchLogs()
  }

  function toggleCompact() {
    setCompact(!isCompact.value)
  }

  function openDetailsDialog(log: UsageLog) {
    detailsDialog.log = log
    detailsDialog.visible = true
  }

  onMounted(() => {
    fetchLogs()
    fetchStats()
  })

  return {
    logs, loading, error, hasData,
    filters, pagination,
    stats, statsLoading,
    isAdmin,
    isCompact, toggleCompact,
    detailsDialog, openDetailsDialog,
    fetchLogs, fetchStats,
    handleSearch, handleResetFilters,
    handlePageChange, handlePageSizeChange,
  }
}
