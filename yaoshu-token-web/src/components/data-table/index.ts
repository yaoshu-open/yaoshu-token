// data-table 公共组件：vxe-table v4 + 项目级封装（矩阵 M1.5 选型变更：el-table-v2 → vxe-table）
export { default as DataTable } from './DataTable.vue'
export { default as DataTablePage } from './DataTablePage.vue'
export { default as DataTableToolbar } from './DataTableToolbar.vue'
export { default as DataTableBulkActions } from './DataTableBulkActions.vue'
export { default as DataTableFacetedFilter } from './DataTableFacetedFilter.vue'
export { default as DataTableViewOptions } from './DataTableViewOptions.vue'
export { default as DataTableColumnHeader } from './DataTableColumnHeader.vue'
export { default as DataTablePagination } from './DataTablePagination.vue'
export { default as DataTableMobileCardList } from './DataTableMobileCardList.vue'

// composables
export { useDataTable } from './composables/useDataTable'
export { useTableUrlState } from './composables/useTableUrlState'
export { useDebouncedColumnFilter } from './composables/useDebouncedColumnFilter'

// 类型重导出（消费方只从 @/components/data-table 引用，禁止跨层引用）
export type {
  ColumnFiltersState,
  DataTableCellRenderParams,
  DataTableColumn,
  DataTableFilterDef,
  DataTableFilterOption,
  DataTableHeaderRenderParams,
  DataTableInstance,
  ExpandedState,
  OnChangeFn,
  PaginationState,
  RowSelectionState,
  SortingState,
  UseDataTableOptions,
  UseTableUrlStateParams,
  UseTableUrlStateReturn,
  VisibilityState,
} from './types'
