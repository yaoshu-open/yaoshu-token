/**
 * 模型列表数据管理 composable。
 *
 * 职责：列表加载 / 搜索 / 筛选 / 排序 / 分页 / 选择 / 紧凑模式消费。
 * 不负责：表单编辑（useModelMutateForm）/ 操作确认（useModelActions）。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { getModels, getVendors, searchModels } from '@/api/model'
import { DEFAULT_PAGE_SIZE } from '@/api/model/constants'
import type {
  GetModelsParams,
  Model,
  SearchModelsParams,
  Vendor,
} from '@/api/model/types'
import { useTableCompactMode } from '@/composables/useTableCompactMode'

/** 模型列表筛选状态 */
export interface ModelFilters {
  keyword: string
  vendor: string
  status: string
  syncOfficial: string
}

/** 模型列表分页状态 */
export interface ModelPagination {
  page: number
  pageSize: number
  total: number
}

const DEFAULT_FILTERS: ModelFilters = {
  keyword: '',
  vendor: 'all',
  status: 'all',
  syncOfficial: 'all',
}

const DEFAULT_PAGINATION: ModelPagination = {
  page: 1,
  pageSize: DEFAULT_PAGE_SIZE,
  total: 0,
}

export function useModelsData() {
  const { t } = useI18n()

  // ============================================================================
  // 状态
  // ============================================================================

  const models = ref<Model[]>([])
  const vendors = ref<Vendor[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const vendorCounts = ref<Record<string, number>>({})

  const filters = reactive<ModelFilters>({ ...DEFAULT_FILTERS })
  const pagination = reactive<ModelPagination>({ ...DEFAULT_PAGINATION })
  const selectedIds = ref<number[]>([])

  // 紧凑模式（T-MO-01）
  const [isCompact, setCompact] = useTableCompactMode('models')

  // ============================================================================
  // 计算属性
  // ============================================================================

  const hasSelection = computed(() => selectedIds.value.length > 0)
  const selectedCount = computed(() => selectedIds.value.length)

  /** 供应商选项（含计数） */
  const vendorOptions = computed(() => {
    const options = vendors.value.map((v) => ({
      label: `${v.name}${vendorCounts.value[String(v.id)] ? ` (${vendorCounts.value[String(v.id)]})` : ''}`,
      value: String(v.id),
    }))
    return [
      { label: `${t('model.vendor.all')}${vendorCounts.value.all ? ` (${vendorCounts.value.all})` : ''}`, value: 'all' },
      ...options,
    ]
  })

  // ============================================================================
  // 数据加载
  // ============================================================================

  async function fetchVendors(): Promise<void> {
    try {
      const data = await getVendors({ pageSize: 1000 })
      vendors.value = data.list
    } catch {
      // 供应商加载失败不阻塞主流程
    }
  }

  async function fetchModels(): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const hasKeyword = filters.keyword.trim().length > 0
      const hasVendorFilter = filters.vendor !== 'all'

      if (hasKeyword || hasVendorFilter) {
        const params: SearchModelsParams = {
          keyword: filters.keyword || undefined,
          vendor: filters.vendor !== 'all' ? filters.vendor : undefined,
          status: filters.status !== 'all' ? filters.status : undefined,
          sync_official: filters.syncOfficial !== 'all' ? filters.syncOfficial : undefined,
          pageNum: pagination.page,
          pageSize: pagination.pageSize,
        }
        const data = await searchModels(params)
        models.value = data.list
        pagination.total = data.total
        vendorCounts.value = data.vendorCounts ?? {}
      } else {
        const params: GetModelsParams = {
          status: filters.status !== 'all' ? filters.status : undefined,
          sync_official: filters.syncOfficial !== 'all' ? filters.syncOfficial : undefined,
          pageNum: pagination.page,
          pageSize: pagination.pageSize,
        }
        const data = await getModels(params)
        models.value = data.list
        pagination.total = data.total
        vendorCounts.value = data.vendorCounts ?? {}
      }
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load models'
      models.value = []
      pagination.total = 0
    } finally {
      loading.value = false
    }
  }

  // ============================================================================
  // 筛选与搜索
  // ============================================================================

  function handleSearch(): void {
    pagination.page = 1
    fetchModels()
  }

  function handleResetFilters(): void {
    Object.assign(filters, DEFAULT_FILTERS)
    pagination.page = 1
    fetchModels()
  }

  function handleFilterChange(): void {
    pagination.page = 1
    fetchModels()
  }

  // ============================================================================
  // 分页
  // ============================================================================

  function handlePageChange(page: number): void {
    pagination.page = page
    fetchModels()
  }

  function handlePageSizeChange(size: number): void {
    pagination.pageSize = size
    pagination.page = 1
    fetchModels()
  }

  // ============================================================================
  // 选择
  // ============================================================================

  function handleSelectionChange(ids: number[]): void {
    selectedIds.value = ids
  }

  function clearSelection(): void {
    selectedIds.value = []
  }

  // ============================================================================
  // 紧凑模式
  // ============================================================================

  function toggleCompact(): void {
    setCompact(!isCompact.value)
  }

  // ============================================================================
  // 监听
  // ============================================================================

  watch(() => filters.vendor, () => handleFilterChange())
  watch(() => filters.status, () => handleFilterChange())
  watch(() => filters.syncOfficial, () => handleFilterChange())

  // ============================================================================
  // 初始化
  // ============================================================================

  onMounted(() => {
    fetchVendors()
    fetchModels()
  })

  return {
    // 状态
    models,
    vendors,
    loading,
    error,
    vendorCounts,
    filters,
    pagination,
    selectedIds,
    isCompact,
    // 计算属性
    hasSelection,
    selectedCount,
    vendorOptions,
    // 方法
    fetchModels,
    fetchVendors,
    handleSearch,
    handleResetFilters,
    handleFilterChange,
    handlePageChange,
    handlePageSizeChange,
    handleSelectionChange,
    clearSelection,
    toggleCompact,
  }
}
