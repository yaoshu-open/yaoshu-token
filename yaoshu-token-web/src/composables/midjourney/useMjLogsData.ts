/**
 * Midjourney 任务日志数据管理 composable。
 *
 * 职责：分页/筛选/三态/紧凑模式/T-MJ-01 banner/图片与 Prompt 对话框状态。
 * 后端 PageHelper 分页，pageNum 1-based。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { getAllMjLogs, getUserMjLogs } from '@/api/midjourney'
import { DEFAULT_PAGE_SIZE } from '@/api/midjourney/constants'
import type { GetMjLogsParams, MidjourneyLog, MjLogsListData } from '@/api/midjourney/types'
import { useTableCompactMode } from '@/composables/useTableCompactMode'
import { useUserPermissions } from '@/composables/useUserPermissions'

/** 筛选条件 */
export interface MjLogsFilters {
  /** 日期范围 [start, end]，null 表示已清除 */
  dateRange: [Date, Date] | null
  mjId: string
  channelId: string
}

/** 分页（前端 1-based） */
export interface MjLogsPagination {
  page: number
  pageSize: number
  total: number
}

const DEFAULT_FILTERS: MjLogsFilters = {
  dateRange: getDefaultDateRange(),
  mjId: '',
  channelId: '',
}

const DEFAULT_PAGINATION: MjLogsPagination = {
  page: 1,
  pageSize: DEFAULT_PAGE_SIZE,
  total: 0,
}

/** 默认最近 30 天 */
function getDefaultDateRange(): [Date, Date] {
  const now = new Date()
  const thirtyDaysAgo = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000)
  return [thirtyDaysAgo, now]
}

const MJ_NOTIFY_KEY = 'mj_notify_enabled'

export function useMjLogsData() {
  const { isAdmin } = useUserPermissions()

  const logs = ref<MidjourneyLog[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const filters = reactive<MjLogsFilters>({ ...DEFAULT_FILTERS, dateRange: getDefaultDateRange() })
  const pagination = reactive<MjLogsPagination>({ ...DEFAULT_PAGINATION })

  const [isCompact, setCompact] = useTableCompactMode('mjLogs')

  // T-MJ-01 banner：管理员可见，localStorage 持久化关闭状态
  const showBanner = ref(false)

  // 对话框状态
  const imageDialog = reactive<{ visible: boolean; url: string }>({
    visible: false,
    url: '',
  })
  const promptDialog = reactive<{ visible: boolean; content: string; title: string }>({
    visible: false,
    content: '',
    title: '',
  })

  const hasData = computed(() => logs.value.length > 0)

  /** 构建 API 参数（后端 PageHelper 分页，pageNum 1-based） */
  function buildParams(): GetMjLogsParams {
    const params: GetMjLogsParams = {
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
    }
    if (filters.mjId.trim()) params.mj_id = filters.mjId.trim()
    if (isAdmin.value && filters.channelId.trim()) params.channel_id = filters.channelId.trim()
    if (filters.dateRange) {
      // 后端契约：秒级时间戳（与 MidjourneyLog.submitTime 单位一致）
      params.start_timestamp = Math.floor(filters.dateRange[0].getTime() / 1000)
      params.end_timestamp = Math.floor(filters.dateRange[1].getTime() / 1000)
    }
    return params
  }

  async function fetchMjLogs(): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const params = buildParams()
      const data: MjLogsListData = isAdmin.value
        ? await getAllMjLogs(params)
        : await getUserMjLogs(params)
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

  function handleSearch(): void {
    pagination.page = 1
    fetchMjLogs()
  }

  function handleResetFilters(): void {
    Object.assign(filters, { ...DEFAULT_FILTERS, dateRange: getDefaultDateRange() })
    pagination.page = 1
    fetchMjLogs()
  }

  function handlePageChange(page: number): void {
    pagination.page = page
    fetchMjLogs()
  }

  function handlePageSizeChange(size: number): void {
    pagination.pageSize = size
    pagination.page = 1
    fetchMjLogs()
  }

  function toggleCompact(): void {
    setCompact(!isCompact.value)
  }

  function openImageDialog(url: string): void {
    imageDialog.url = url
    imageDialog.visible = true
  }

  function openPromptDialog(content: string, title: string): void {
    promptDialog.content = content
    promptDialog.title = title
    promptDialog.visible = true
  }

  /** T-MJ-01：关闭 banner 并持久化 */
  function dismissBanner(): void {
    try {
      localStorage.setItem(MJ_NOTIFY_KEY, 'true')
    } catch {
      /* 隐私模式忽略 */
    }
    showBanner.value = false
  }

  /** 初始化 banner 可见性（管理员 + 未手动关闭） */
  function initBanner(): void {
    if (!isAdmin.value) {
      showBanner.value = false
      return
    }
    try {
      showBanner.value = localStorage.getItem(MJ_NOTIFY_KEY) !== 'true'
    } catch {
      showBanner.value = true
    }
  }

  onMounted(() => {
    initBanner()
    fetchMjLogs()
  })

  return {
    // 数据三态
    logs,
    loading,
    error,
    hasData,
    // 分页/筛选
    filters,
    pagination,
    // 权限
    isAdmin,
    // 紧凑模式
    isCompact,
    toggleCompact,
    // T-MJ-01 banner
    showBanner,
    dismissBanner,
    // 对话框
    imageDialog,
    promptDialog,
    openImageDialog,
    openPromptDialog,
    // 操作
    fetchMjLogs,
    handleSearch,
    handleResetFilters,
    handlePageChange,
    handlePageSizeChange,
  }
}
