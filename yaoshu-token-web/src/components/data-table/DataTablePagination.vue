<script setup lang="ts" generic="TData extends Record<string, unknown>">
import { computed } from 'vue'
import { ElPagination } from 'element-plus'
import { useI18n } from 'vue-i18n'
import type { DataTableInstance } from './types'

const props = withDefaults(
  defineProps<{
    dataTable: DataTableInstance<TData>
    pageSizes?: number[]
    layout?: string
  }>(),
  {
    pageSizes: () => [10, 20, 50, 100],
    layout: 'total, sizes, prev, pager, next, jumper',
  },
)

const { t } = useI18n()

const currentPage = computed(() => props.dataTable.pagination.value.pageIndex + 1)
const pageSize = computed(() => props.dataTable.pagination.value.pageSize)
const total = computed(() => props.dataTable.totalCount.value ?? 0)

function handleCurrentChange(page: number) {
  props.dataTable.setPageIndex(page - 1)
}

function handleSizeChange(size: number) {
  props.dataTable.setPageSize(size)
}
</script>

<template>
  <div class="data-table-pagination">
    <ElPagination
      :current-page="currentPage"
      :page-size="pageSize"
      :page-sizes="pageSizes"
      :total="total"
      :layout="layout"
      background
      @current-change="handleCurrentChange"
      @size-change="handleSizeChange"
    >
      <span class="data-table-pagination__total">
        {{ t('common.paginationTotal', { total }) }}
      </span>
    </ElPagination>
  </div>
</template>

<style scoped lang="scss">
.data-table-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding-top: 8px;
}
</style>
