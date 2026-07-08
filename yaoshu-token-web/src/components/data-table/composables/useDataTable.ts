import { computed, ref, toValue, watch } from 'vue'
import type { ComputedRef, Ref } from 'vue'
import type {
  ColumnFiltersState,
  DataTableColumn,
  DataTableInstance,
  ExpandedState,
  OnChangeFn,
  PaginationState,
  RowSelectionState,
  SortingState,
  UseDataTableOptions,
  VisibilityState,
} from '../types'

/** 解析 updater：函数式更新 or 直接值 */
function resolveUpdater<T>(updater: T | ((prev: T) => T), previous: T): T {
  return typeof updater === 'function'
    ? (updater as (old: T) => T)(previous)
    : updater
}

/** 将 MaybeRef/MaybeComputedRef 归一为 ComputedRef */
function normalizeRef<T>(
  source: T | Ref<T> | ComputedRef<T> | undefined,
  fallback: T,
): ComputedRef<T> {
  if (source === undefined) return computed(() => fallback)
  return computed(() => toValue(source as T | Ref<T> | ComputedRef<T>))
}

export function useDataTable<TData = Record<string, unknown>>(
  options: UseDataTableOptions<TData>,
): DataTableInstance<TData> {
  const {
    rowKey,
    enableRowSelection = false,
    ensurePageInRange,
    initialSorting = [],
    initialColumnVisibility = {},
    initialRowSelection = {},
    initialExpanded = {},
    initialPagination = { pageIndex: 0, pageSize: 20 },
  } = options

  // ===== 响应式输入归一 =====
  const columnsRef = normalizeRef(options.columns, [])
  const dataRef = normalizeRef(options.data, [])
  const totalCountRef = normalizeRef(options.totalCount, undefined)
  const explicitPageCountRef = normalizeRef(options.pageCount, undefined)

  // ===== 行 ID 解析 =====
  const getRowId = (row: TData, rowIndex: number): string => {
    if (rowKey) return rowKey(row)
    const maybeId = (row as Record<string, unknown>)?.id
    return maybeId !== undefined ? String(maybeId) : String(rowIndex)
  }

  // ===== 受控/非受控状态 =====
  const sortingControlled = options.sorting ? toValue(options.sorting as Ref<SortingState>) : undefined
  const sortingRef = ref<SortingState>(sortingControlled ?? initialSorting)
  const sorting = computed<SortingState>({
    get: () => (options.sorting ? toValue(options.sorting as Ref<SortingState>) : sortingRef.value),
    set: (v) => { sortingRef.value = v },
  })

  const visibilityControlled = options.columnVisibility
    ? toValue(options.columnVisibility as Ref<VisibilityState>)
    : undefined
  const columnVisibilityRef = ref<VisibilityState>(visibilityControlled ?? initialColumnVisibility)
  const columnVisibility = computed<VisibilityState>({
    get: () => options.columnVisibility ? toValue(options.columnVisibility as Ref<VisibilityState>) : columnVisibilityRef.value,
    set: (v) => { columnVisibilityRef.value = v },
  })

  const rowSelectionControlled = options.rowSelection
    ? toValue(options.rowSelection as Ref<RowSelectionState>)
    : undefined
  const rowSelectionRef = ref<RowSelectionState>(rowSelectionControlled ?? initialRowSelection)
  const rowSelection = computed<RowSelectionState>({
    get: () => options.rowSelection ? toValue(options.rowSelection as Ref<RowSelectionState>) : rowSelectionRef.value,
    set: (v) => { rowSelectionRef.value = v },
  })

  const expandedControlled = options.expanded
    ? toValue(options.expanded as Ref<ExpandedState>)
    : undefined
  const expandedRef = ref<ExpandedState>(expandedControlled ?? initialExpanded)
  const expanded = computed<ExpandedState>({
    get: () => options.expanded ? toValue(options.expanded as Ref<ExpandedState>) : expandedRef.value,
    set: (v) => { expandedRef.value = v },
  })

  const paginationControlled = options.pagination
    ? toValue(options.pagination as Ref<PaginationState>)
    : undefined
  const paginationRef = ref<PaginationState>(paginationControlled ?? initialPagination)
  const pagination = computed<PaginationState>({
    get: () => options.pagination ? toValue(options.pagination as Ref<PaginationState>) : paginationRef.value,
    set: (v) => { paginationRef.value = v },
  })

  // columnFilters / globalFilter 始终外部控制（useTableUrlState 或组件直接管理）
  const columnFiltersRef = ref<ColumnFiltersState>(
    options.columnFilters ? toValue(options.columnFilters as Ref<ColumnFiltersState>) : [],
  )
  const columnFilters = computed<ColumnFiltersState>({
    get: () => options.columnFilters ? toValue(options.columnFilters as Ref<ColumnFiltersState>) : columnFiltersRef.value,
    set: (v) => { columnFiltersRef.value = v },
  })

  const globalFilterRef = ref<string>(
    options.globalFilter ? toValue(options.globalFilter as Ref<string>) : '',
  )
  const globalFilter = computed<string>({
    get: () => options.globalFilter ? toValue(options.globalFilter as Ref<string>) : globalFilterRef.value,
    set: (v) => { globalFilterRef.value = v },
  })

  // 外部受控状态变化时同步内部 ref（双向）
  watch(() => (options.columnFilters ? toValue(options.columnFilters as Ref<ColumnFiltersState>) : undefined), (v) => {
    if (v !== undefined) columnFiltersRef.value = v
  })
  watch(() => (options.globalFilter ? toValue(options.globalFilter as Ref<string>) : undefined), (v) => {
    if (v !== undefined) globalFilterRef.value = v
  })

  // ===== 派生计算 =====
  const pageCount = computed<number | undefined>(() => {
    if (explicitPageCountRef.value !== undefined) return explicitPageCountRef.value
    if (totalCountRef.value !== undefined) {
      return Math.ceil(totalCountRef.value / Math.max(1, pagination.value.pageSize))
    }
    return undefined
  })

  const allRowKeys = computed<string[]>(() =>
    dataRef.value.map((row, index) => getRowId(row, index)),
  )

  const selectedRowKeys = computed<string[]>(() =>
    Object.keys(rowSelection.value).filter((key) => rowSelection.value[key]),
  )

  const selectedCount = computed(() => selectedRowKeys.value.length)

  const getIsAllRowsSelected = computed(() => {
    if (!enableRowSelection || allRowKeys.value.length === 0) return false
    return allRowKeys.value.every((key) => rowSelection.value[key])
  })

  const getIsSomeRowsSelected = computed(() => {
    if (!enableRowSelection) return false
    return selectedCount.value > 0 && !getIsAllRowsSelected.value
  })

  const visibleColumns = computed<DataTableColumn<TData>[]>(() =>
    columnsRef.value.filter(
      (col) => columnVisibility.value[col.key] !== false,
    ),
  )

  // ===== 状态操作方法 =====
  const setSorting: OnChangeFn<SortingState> = (updater) => {
    const next = resolveUpdater(updater, sorting.value)
    if (options.sorting === undefined) sortingRef.value = next
    options.onSortingChange?.(next)
  }

  const toggleColumnVisibility = (columnKey: string, visible?: boolean) => {
    const next = { ...columnVisibility.value }
    next[columnKey] = visible ?? next[columnKey] === false
    if (options.columnVisibility === undefined) columnVisibilityRef.value = next
    options.onColumnVisibilityChange?.(next)
  }

  const getColumnVisibility = (columnKey: string): boolean =>
    columnVisibility.value[columnKey] !== false

  const setRowSelection: OnChangeFn<RowSelectionState> = (updater) => {
    const next = resolveUpdater(updater, rowSelection.value)
    if (options.rowSelection === undefined) rowSelectionRef.value = next
    options.onRowSelectionChange?.(next)
  }

  const resetRowSelection = () => {
    if (options.rowSelection === undefined) rowSelectionRef.value = {}
    options.onRowSelectionChange?.({})
  }

  const toggleRowSelection = (key: string, selected?: boolean) => {
    const next = { ...rowSelection.value }
    next[key] = selected ?? !next[key]
    setRowSelection(next)
  }

  const toggleAllRowsSelected = (selected?: boolean) => {
    const next: RowSelectionState = {}
    const target = selected ?? !getIsAllRowsSelected.value
    if (target) {
      for (const key of allRowKeys.value) next[key] = true
    }
    setRowSelection(next)
  }

  const setColumnFilter = (
    columnId: string,
    value: string | string[] | undefined,
  ) => {
    const prev = columnFilters.value.filter((f) => f.id !== columnId)
    const next =
      value === undefined || (Array.isArray(value) && value.length === 0) || value === ''
        ? prev
        : [...prev, { id: columnId, value }]
    if (options.columnFilters === undefined) columnFiltersRef.value = next
    options.onColumnFiltersChange?.(next)
  }

  const getColumnFilterValue = (
    columnId: string,
  ): string | string[] | undefined => {
    return columnFilters.value.find((f) => f.id === columnId)?.value
  }

  const resetColumnFilters = () => {
    if (options.columnFilters === undefined) columnFiltersRef.value = []
    options.onColumnFiltersChange?.([])
  }

  const setGlobalFilter: OnChangeFn<string> = (updater) => {
    const next = resolveUpdater(updater, globalFilter.value)
    if (options.globalFilter === undefined) globalFilterRef.value = next
    options.onGlobalFilterChange?.(next)
  }

  const resetGlobalFilter = () => {
    if (options.globalFilter === undefined) globalFilterRef.value = ''
    options.onGlobalFilterChange?.('')
  }

  const setPagination: OnChangeFn<PaginationState> = (updater) => {
    const next = resolveUpdater(updater, pagination.value)
    if (options.pagination === undefined) paginationRef.value = next
    options.onPaginationChange?.(next)
  }

  const setPageIndex = (pageIndex: number) => {
    setPagination({ ...pagination.value, pageIndex })
  }

  const setPageSize = (pageSize: number) => {
    setPagination({ pageIndex: 0, pageSize })
  }

  // ===== 页码越界保护 =====
  watch(pageCount, (actualPageCount) => {
    if (actualPageCount !== undefined && ensurePageInRange) {
      ensurePageInRange(actualPageCount)
    }
  })

  return {
    columns: columnsRef,
    data: dataRef,

    sorting: sorting as Ref<SortingState>,
    columnVisibility: columnVisibility as Ref<VisibilityState>,
    rowSelection: rowSelection as Ref<RowSelectionState>,
    expanded: expanded as Ref<ExpandedState>,
    columnFilters: columnFilters as Ref<ColumnFiltersState>,
    globalFilter: globalFilter as Ref<string>,
    pagination: pagination as Ref<PaginationState>,

    totalCount: totalCountRef,
    pageCount,
    selectedRowKeys,
    selectedCount,
    visibleColumns,

    setSorting,
    toggleColumnVisibility,
    getColumnVisibility,
    setRowSelection,
    resetRowSelection,
    toggleRowSelection,
    toggleAllRowsSelected,
    getIsAllRowsSelected,
    getIsSomeRowsSelected,
    setColumnFilter,
    getColumnFilterValue,
    resetColumnFilters,
    setGlobalFilter,
    resetGlobalFilter,
    setPagination,
    setPageIndex,
    setPageSize,

    getRowId,
  }
}
