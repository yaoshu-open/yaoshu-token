<script setup lang="ts" generic="TData extends Record<string, unknown>">
import { computed } from 'vue'
import { ElEmpty } from 'element-plus'
import { useI18n } from 'vue-i18n'
import type { DataTableColumn, DataTableInstance } from './types'

const props = withDefaults(
  defineProps<{
    dataTable: DataTableInstance<TData>
    columns?: DataTableColumn<TData>[]
    isLoading?: boolean
    emptyTitle?: string
    emptyDescription?: string
    rowClassName?: (rowData: TData, rowIndex: number) => string | undefined
    fixedHeight?: boolean
  }>(),
  {
    columns: undefined,
    isLoading: false,
    emptyTitle: undefined,
    emptyDescription: undefined,
    rowClassName: undefined,
    fixedHeight: true,
  },
)

const { t } = useI18n()

const tableData = computed(() => props.dataTable.data.value)

// DataTableColumn[] → vxe-table Column 配置
const vxeColumns = computed(() => {
  const source = props.columns ?? props.dataTable.visibleColumns.value
  return source.map((col) => {
    const vxeCol: Record<string, unknown> = {
      field: col.field || col.key,
      title: col.title || col.key,
      width: col.width,
      minWidth: col.minWidth,
      align: col.align,
      fixed: col.fixed,
      sortable: (col.enableSorting ?? col.sortable) ?? false,
      showOverflow: true,
    }
    if (col.cellRenderer) {
      // cellRenderer → vxe-table formatter
      vxeCol.formatter = ({ cellValue, row, rowIndex }: { cellValue: unknown; row: Record<string, unknown>; rowIndex: number }) => {
        const result = col.cellRenderer!({
          cellValue,
          rowData: row as TData,
          rowIndex,
          column: col,
        })
        if (typeof result === 'string' || typeof result === 'number' || result === null) {
          return String(result ?? '')
        }
        // VNode 渲染：vxe-table formatter 支持 { children: VNode[] }
        return { children: [result] }
      }
    }
    return vxeCol
  })
})

// 排序状态同步：vxe-table 内部维护排序显示，sort-change 事件桥接至 useDataTable
// 初始排序通过 vxe-table defaultSort 注入

// vxe-table sort-change → useDataTable.setSorting 桥接
function handleSortChange(params: { property?: string; order?: string | null }) {
  const property = params.property
  const order = params.order
  if (!property) return
  const others = props.dataTable.sorting.value.filter((s: { key: string }) => s.key !== property)
  // order 为 null/undefined/'' 时表示取消排序
  const next = order ? [...others, { key: property, order: order as 'asc' | 'desc' }] : others
  props.dataTable.setSorting(next)
}

// vxe-table row-config（含 rowClassName 适配）
const rowConfig = computed(() => {
  const config: Record<string, unknown> = { isHover: true }
  if (props.rowClassName) {
    config.className = ({ row, rowIndex }: { row: Record<string, unknown>; rowIndex: number }) =>
      props.rowClassName!(row as TData, rowIndex) ?? ''
  }
  return config
})
</script>

<template>
  <div
    class="data-table-container"
    :class="{ 'data-table-container--fixed': fixedHeight }"
  >
    <vxe-table
      :data="tableData"
      :columns="vxeColumns"
      :loading="isLoading"
      :sort-config="{ remote: true, multiple: true }"
      :height="fixedHeight ? '100%' : undefined"
      :max-height="fixedHeight ? '100%' : undefined"
      :row-config="rowConfig"
      border
      stripe
      @sort-change="(e) => handleSortChange(e as { property?: string; order?: string | null })"
    >
      <template #empty>
        <div class="data-table-empty">
          <ElEmpty
            :description="emptyTitle ?? t('common.empty.title')"
            :image-size="120"
          >
            <p
              v-if="emptyDescription"
              class="data-table-empty-desc"
            >
              {{ emptyDescription }}
            </p>
            <slot name="empty-action" />
          </ElEmpty>
        </div>
      </template>
    </vxe-table>
  </div>
</template>

<style scoped lang="scss">
.data-table-container {
  display: flex;
  min-height: 0;

  &--fixed {
    flex: 1;
    width: 100%;
  }
}

.data-table-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;

  &-desc {
    margin: 0;
    font-size: var(--el-font-size-base);
    color: var(--el-text-color-secondary);
  }
}
</style>
