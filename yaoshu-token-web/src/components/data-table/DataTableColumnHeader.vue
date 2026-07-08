<script setup lang="ts">
import { computed } from 'vue'
import type { DataTableColumn, DataTableInstance } from './types'

const props = defineProps<{
  dataTable: DataTableInstance
  column: DataTableColumn
}>()

const sortState = computed(() => props.dataTable.sorting.value)
const currentSort = computed(
  () => sortState.value.find((s) => s.key === props.column.key)?.order,
)
const isSortable = computed(
  () => props.column.enableSorting ?? props.column.sortable ?? false,
)

function toggleSort() {
  if (!isSortable.value) return
  const order: 'asc' | 'desc' | undefined =
    currentSort.value === 'asc' ? 'desc' : currentSort.value === 'desc' ? undefined : 'asc'
  const others = sortState.value.filter((s) => s.key !== props.column.key)
  const next = order ? [...others, { key: props.column.key, order }] : others
  props.dataTable.setSorting(next)
}
</script>

<template>
  <div
    class="data-table-column-header"
    :class="{ 'is-sortable': isSortable }"
    @click="toggleSort"
  >
    <span class="data-table-column-header__label">{{ column.title }}</span>
    <span
      v-if="isSortable"
      class="data-table-column-header__sort"
    >
      <i
        class="data-table-column-header__sort-icon"
        :class="{
          'is-active': currentSort === 'asc',
          'is-visible': currentSort === 'asc' || currentSort === undefined,
        }"
      >▲</i>
      <i
        class="data-table-column-header__sort-icon"
        :class="{
          'is-active': currentSort === 'desc',
          'is-visible': currentSort === 'desc' || currentSort === undefined,
        }"
      >▼</i>
    </span>
  </div>
</template>

<style scoped lang="scss">
.data-table-column-header {
  display: inline-flex;
  gap: var(--ys-spacing-1);
  align-items: center;
  cursor: default;

  &.is-sortable {
    cursor: pointer;
  }

  &__label {
    font-weight: 600;
  }

  &__sort {
    display: inline-flex;
    flex-direction: column;
    font-size: 9px;
    line-height: 0.7;
  }

  &__sort-icon {
    color: var(--el-text-color-placeholder);

    &.is-active {
      color: var(--el-color-primary);
    }

    &:not(.is-visible) {
      visibility: hidden;
    }
  }
}
</style>
