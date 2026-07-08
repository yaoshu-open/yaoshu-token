import type { ComputedRef, Ref, VNode } from 'vue'

/** 排序状态 */
export type SortingState = { key: string; order: 'asc' | 'desc' }[]

/** 列筛选状态（faceted filter / column filter 用） */
export type ColumnFiltersState = { id: string; value: string | string[] }[]

/** 列可见性 */
export type VisibilityState = Record<string, boolean>

/** 行选择：key 为行唯一标识（rowKey） */
export type RowSelectionState = Record<string, boolean>

/** 分页：pageIndex 从 0 开始（API 层转换成 page 从 1 开始） */
export interface PaginationState {
  pageIndex: number
  pageSize: number
}

/** 展开状态（多 Key Channel 子行展开） */
export type ExpandedState = Record<string, boolean>

/** 受控状态变更回调（支持 updater 函数式写法） */
export type OnChangeFn<T> = (updater: T | ((prev: T) => T)) => void

// ===== 列定义 =====

/** 单元格渲染参数 */
export interface DataTableCellRenderParams<TData> {
  cellValue: unknown
  rowData: TData
  rowIndex: number
  column: DataTableColumn<TData>
}

/** 表头渲染参数 */
export interface DataTableHeaderRenderParams<TData> {
  column: DataTableColumn<TData>
}

/** 列定义（框架无关，内部转换为 vxe-table Column 配置） */
export interface DataTableColumn<TData = Record<string, unknown>> {
  /** 列唯一标识（同时作为状态索引 key） */
  key: string
  /** 数据字段名（vxe-table field）；cellRenderer 不覆盖时用此字段自动渲染单元格值 */
  field?: string
  /** 列标题 */
  title?: string
  /** 列宽 */
  width?: number | string
  /** 最小列宽 */
  minWidth?: number | string
  /** 对齐 */
  align?: 'left' | 'center' | 'right'
  /** 列固定 */
  fixed?: 'left' | 'right'
  /** 是否可排序（须同时开启 enableSorting 或组件层约束） */
  sortable?: boolean
  /** 单元格渲染器（内部转为 vxe-table formatter） */
  cellRenderer?: (
    params: DataTableCellRenderParams<TData>,
  ) => VNode | string | number | null
  /** 表头渲染器（内部转为 vxe-table header slot） */
  headerCellRenderer?: (
    params: DataTableHeaderRenderParams<TData>,
  ) => VNode | string | null

  // ===== 业务元数据扩展 =====
  /** ViewOptions 下拉中显示的列名（默认用 title） */
  meta?: {
    label?: string
    [key: string]: unknown
  }
  /** 是否允许在 ViewOptions 中隐藏，默认 true（操作列设 false） */
  enableHiding?: boolean
  /** 是否允许排序，默认跟随 sortable */
  enableSorting?: boolean
}

// ===== Faceted Filter 定义 =====

export interface DataTableFilterOption {
  label: string
  value: string
  /** Iconify 图标名，如 'ep:user' */
  icon?: string
  count?: number
}

export interface DataTableFilterDef {
  columnId: string
  title: string
  options: DataTableFilterOption[]
  /** 单选模式 */
  singleSelect?: boolean
}

// ===== useDataTable 返回类型 =====

export interface DataTableInstance<TData = Record<string, unknown>> {
  // 原始输入
  columns: ComputedRef<DataTableColumn<TData>[]>
  data: ComputedRef<TData[]>

  // 当前状态（reactive）
  sorting: Ref<SortingState>
  columnVisibility: Ref<VisibilityState>
  rowSelection: Ref<RowSelectionState>
  expanded: Ref<ExpandedState>
  columnFilters: Ref<ColumnFiltersState>
  globalFilter: Ref<string>
  pagination: Ref<PaginationState>

  // 派生数据
  totalCount: ComputedRef<number | undefined>
  pageCount: ComputedRef<number | undefined>
  selectedRowKeys: ComputedRef<string[]>
  selectedCount: ComputedRef<number>
  visibleColumns: ComputedRef<DataTableColumn<TData>[]>

  // 状态操作方法
  setSorting: OnChangeFn<SortingState>
  toggleColumnVisibility: (columnKey: string, visible?: boolean) => void
  getColumnVisibility: (columnKey: string) => boolean
  setRowSelection: OnChangeFn<RowSelectionState>
  resetRowSelection: () => void
  toggleRowSelection: (rowKey: string, selected?: boolean) => void
  toggleAllRowsSelected: (selected?: boolean) => void
  getIsAllRowsSelected: ComputedRef<boolean>
  getIsSomeRowsSelected: ComputedRef<boolean>
  setColumnFilter: (
    columnId: string,
    value: string | string[] | undefined,
  ) => void
  getColumnFilterValue: (
    columnId: string,
  ) => string | string[] | undefined
  resetColumnFilters: () => void
  setGlobalFilter: OnChangeFn<string>
  resetGlobalFilter: () => void
  setPagination: OnChangeFn<PaginationState>
  setPageIndex: (pageIndex: number) => void
  setPageSize: (pageSize: number) => void

  // 辅助
  getRowId: (row: TData, rowIndex: number) => string
}

// ===== useDataTable 输入类型 =====

export interface UseDataTableOptions<TData = Record<string, unknown>> {
  columns: DataTableColumn<TData>[] | ComputedRef<DataTableColumn<TData>[]>
  data: TData[] | Ref<TData[]> | ComputedRef<TData[]>
  /** 行唯一标识，默认取 row.id */
  rowKey?: (row: TData) => string

  // 服务端模式配置
  totalCount?: number | Ref<number | undefined> | ComputedRef<number | undefined>
  pageCount?: number | Ref<number | undefined> | ComputedRef<number | undefined>

  // 受控状态（外部传入则受控，否则内部非受控）
  sorting?: SortingState | Ref<SortingState>
  onSortingChange?: OnChangeFn<SortingState>
  columnVisibility?: VisibilityState | Ref<VisibilityState>
  onColumnVisibilityChange?: OnChangeFn<VisibilityState>
  rowSelection?: RowSelectionState | Ref<RowSelectionState>
  onRowSelectionChange?: OnChangeFn<RowSelectionState>
  expanded?: ExpandedState | Ref<ExpandedState>
  onExpandedChange?: OnChangeFn<ExpandedState>
  columnFilters?: ColumnFiltersState | Ref<ColumnFiltersState>
  onColumnFiltersChange?: OnChangeFn<ColumnFiltersState>
  globalFilter?: string | Ref<string>
  onGlobalFilterChange?: OnChangeFn<string>
  pagination?: PaginationState | Ref<PaginationState>
  onPaginationChange?: OnChangeFn<PaginationState>

  // 初始值（非受控模式）
  initialSorting?: SortingState
  initialColumnVisibility?: VisibilityState
  initialRowSelection?: RowSelectionState
  initialExpanded?: ExpandedState
  initialPagination?: PaginationState

  // 功能开关
  enableRowSelection?: boolean
  ensurePageInRange?: (pageCount: number) => void
}

// ===== useTableUrlState 类型 =====

export interface UseTableUrlStateParams {
  pagination?: {
    pageKey?: string
    pageSizeKey?: string
    defaultPage?: number
    defaultPageSize?: number
  }
  globalFilter?: {
    enabled?: boolean
    key?: string
    trim?: boolean
  }
  sorting?: {
    enabled?: boolean
    key?: string
  }
  columnFilters?: Array<{
    columnId: string
    searchKey: string
    type?: 'string' | 'array'
    serialize?: (value: unknown) => unknown
    deserialize?: (value: unknown) => unknown
  }>
}

export interface UseTableUrlStateReturn {
  sorting: ComputedRef<SortingState>
  onSortingChange: OnChangeFn<SortingState>
  globalFilter: ComputedRef<string>
  onGlobalFilterChange: OnChangeFn<string> | undefined
  columnFilters: ComputedRef<ColumnFiltersState>
  onColumnFiltersChange: OnChangeFn<ColumnFiltersState>
  pagination: ComputedRef<PaginationState>
  onPaginationChange: OnChangeFn<PaginationState>
  ensurePageInRange: (
    pageCount: number,
    opts?: { resetTo?: 'first' | 'last' },
  ) => void
}

// ===== Props 辅助类型 =====

export type MaybeRef<T> = T | Ref<T>
export type MaybeComputedRef<T> = T | Ref<T> | ComputedRef<T>
