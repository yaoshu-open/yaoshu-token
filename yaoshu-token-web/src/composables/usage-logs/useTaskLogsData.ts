/**
 * 任务日志数据管理 composable。
 *
 * 职责：分页/筛选/三态/紧凑模式/音频预览与失败原因对话框状态。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { getAllTaskLogs, getUserTaskLogs } from '@/api/task'
import { DEFAULT_PAGE_SIZE } from '@/api/task/constants'
import type { GetTaskLogsParams, TaskLog } from '@/api/task/types'
import { useTableCompactMode } from '@/composables/useTableCompactMode'
import { useUserPermissions } from '@/composables/useUserPermissions'

export interface TaskLogsFilters {
  dateRange: [Date, Date] | null
  taskId: string
  channelId: string
}

export interface TaskLogsPagination {
  page: number
  pageSize: number
  total: number
}

const DEFAULT_FILTERS: TaskLogsFilters = {
  dateRange: getDefaultDateRange(),
  taskId: '',
  channelId: '',
}

const DEFAULT_PAGINATION: TaskLogsPagination = {
  page: 1,
  pageSize: DEFAULT_PAGE_SIZE,
  total: 0,
}

function getDefaultDateRange(): [Date, Date] {
  const now = new Date()
  const sevenDaysAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000)
  return [sevenDaysAgo, now]
}

export function useTaskLogsData() {
  const { isAdmin } = useUserPermissions()

  const logs = ref<TaskLog[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const filters = reactive<TaskLogsFilters>({ ...DEFAULT_FILTERS, dateRange: getDefaultDateRange() })
  const pagination = reactive<TaskLogsPagination>({ ...DEFAULT_PAGINATION })

  const [isCompact, setCompact] = useTableCompactMode('usageLogsTask')

  const audioDialog = reactive<{ visible: boolean; log: TaskLog | null }>({
    visible: false,
    log: null,
  })
  const failReasonDialog = reactive<{ visible: boolean; content: string }>({
    visible: false,
    content: '',
  })

  const hasData = computed(() => logs.value.length > 0)

  function buildParams(): GetTaskLogsParams {
    const params: GetTaskLogsParams = {
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
    }
    if (filters.taskId.trim()) params.task_id = filters.taskId.trim()
    if (isAdmin.value && filters.channelId.trim()) params.channel_id = filters.channelId.trim()
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
      const data = isAdmin.value ? await getAllTaskLogs(params) : await getUserTaskLogs(params)
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

  function handleSearch() {
    pagination.page = 1
    fetchLogs()
  }

  function handleResetFilters() {
    Object.assign(filters, { ...DEFAULT_FILTERS, dateRange: getDefaultDateRange() })
    pagination.page = 1
    fetchLogs()
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

  function openAudioDialog(log: TaskLog) {
    audioDialog.log = log
    audioDialog.visible = true
  }

  function openFailReasonDialog(reason: string) {
    failReasonDialog.content = reason
    failReasonDialog.visible = true
  }

  onMounted(fetchLogs)

  return {
    logs, loading, error, hasData,
    filters, pagination,
    isAdmin,
    isCompact, toggleCompact,
    audioDialog, failReasonDialog,
    openAudioDialog, openFailReasonDialog,
    fetchLogs,
    handleSearch, handleResetFilters,
    handlePageChange, handlePageSizeChange,
  }
}
