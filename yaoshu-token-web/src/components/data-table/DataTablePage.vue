<script setup lang="ts" generic="TData extends Record<string, unknown>">
import { computed } from 'vue'
import { useMobile } from '@/composables/useMobile'
import DataTable from './DataTable.vue'
import DataTableToolbar from './DataTableToolbar.vue'
import DataTablePagination from './DataTablePagination.vue'
import DataTableMobileCardList from './DataTableMobileCardList.vue'
import type {
  DataTableColumn,
  DataTableFilterDef,
  DataTableInstance,
} from './types'

const props = withDefaults(
  defineProps<{
    dataTable: DataTableInstance<TData>
    columns?: DataTableColumn<TData>[]
    isLoading?: boolean
    isFetching?: boolean
    emptyTitle?: string
    emptyDescription?: string
    hideMobile?: boolean
    showPagination?: boolean
    fixedHeight?: boolean
    /** 工具栏 props 透传 */
    searchPlaceholder?: string
    searchDebounceMs?: number
    searchKey?: string
    filters?: DataTableFilterDef[]
    hideViewOptions?: boolean
    hasAdditionalFilters?: boolean
    hasExpandedActiveFilters?: boolean
  }>(),
  {
    columns: undefined,
    isLoading: false,
    isFetching: false,
    emptyTitle: undefined,
    emptyDescription: undefined,
    hideMobile: false,
    showPagination: true,
    fixedHeight: true,
    searchPlaceholder: undefined,
    searchDebounceMs: 0,
    searchKey: undefined,
    filters: () => [],
    hideViewOptions: false,
    hasAdditionalFilters: false,
    hasExpandedActiveFilters: false,
  },
)

const emit = defineEmits<{
  (e: 'reset'): void
  (e: 'search'): void
}>()

const isMobile = useMobile()
const showMobileLayout = computed(() => isMobile.value && !props.hideMobile)
const isFetchingOnly = computed(() => props.isFetching && !props.isLoading)
</script>

<template>
  <div
    class="data-table-page"
    :class="{ 'data-table-page--fixed': fixedHeight }"
  >
    <!-- 工具栏（桌面端始终显示，移动端仅当无 hideMobile 时显示） -->
    <slot
      name="toolbar"
      :data-table="dataTable"
    >
      <DataTableToolbar
        :data-table="dataTable"
        :search-placeholder="searchPlaceholder"
        :search-debounce-ms="searchDebounceMs"
        :search-key="searchKey"
        :filters="filters"
        :hide-view-options="hideViewOptions"
        :has-additional-filters="hasAdditionalFilters"
        :has-expanded-active-filters="hasExpandedActiveFilters"
        @reset="emit('reset')"
        @search="emit('search')"
      >
        <template
          v-if="$slots.customSearch"
          #customSearch
        >
          <slot name="customSearch" />
        </template>
        <template
          v-if="$slots.additionalSearch"
          #additionalSearch
        >
          <slot name="additionalSearch" />
        </template>
        <template
          v-if="$slots.expandable"
          #expandable
        >
          <slot name="expandable" />
        </template>
        <template
          v-if="$slots.preActions"
          #preActions
        >
          <slot name="preActions" />
        </template>
        <template
          v-if="$slots.leftActions"
          #leftActions
        >
          <slot name="leftActions" />
        </template>
      </DataTableToolbar>
    </slot>

    <!-- 移动端卡片化 -->
    <div
      v-if="showMobileLayout"
      class="data-table-page__mobile"
    >
      <slot
        name="mobile"
        :data-table="dataTable"
      >
        <DataTableMobileCardList
          :data-table="dataTable"
          :is-loading="isLoading"
          :empty-title="emptyTitle"
          :empty-description="emptyDescription"
        >
          <template
            v-if="$slots.mobileCard"
            #card="slotProps"
          >
            <slot
              name="mobileCard"
              v-bind="slotProps"
            />
          </template>
        </DataTableMobileCardList>
      </slot>
    </div>

    <!-- 桌面端表格 -->
    <div
      v-else
      class="data-table-page__desktop"
      :class="{ 'is-fetching': isFetchingOnly }"
    >
      <slot
        name="table"
        :data-table="dataTable"
      >
        <DataTable
          :data-table="dataTable"
          :columns="columns"
          :is-loading="isLoading"
          :empty-title="emptyTitle"
          :empty-description="emptyDescription"
          :fixed-height="fixedHeight"
        />
      </slot>
    </div>

    <!-- 表格下方附加内容 -->
    <slot name="afterTable" />

    <!-- 批量操作栏（仅桌面端） -->
    <slot
      v-if="!showMobileLayout"
      name="bulkActions"
      :data-table="dataTable"
    />

    <!-- 分页 -->
    <template v-if="showPagination">
      <slot
        name="pagination"
        :data-table="dataTable"
      >
        <DataTablePagination :data-table="dataTable" />
      </slot>
    </template>
  </div>
</template>

<style scoped lang="scss">
.data-table-page {
  display: flex;
  flex-direction: column;
  gap: 10px;

  &--fixed {
    height: 100%;
    min-height: 0;
  }

  &__mobile {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
  }

  &__desktop {
    flex: 1;
    min-height: 0;

    &.is-fetching {
      pointer-events: none;
      opacity: 0.6;
      transition: opacity 0.15s;
    }
  }
}
</style>
