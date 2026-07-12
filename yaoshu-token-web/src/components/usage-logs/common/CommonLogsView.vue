<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useCommonLogsData } from '@/composables/usage-logs/useCommonLogsData'
import type { CommonLogsFilters } from '@/composables/usage-logs/useCommonLogsData'
import CommonLogsToolbar from './CommonLogsToolbar.vue'
import CommonLogsStats from './CommonLogsStats.vue'
import CommonLogsTable from './CommonLogsTable.vue'
import CommonLogsDetailsDialog from './CommonLogsDetailsDialog.vue'
import CompactModeToggle from '@/components/CompactModeToggle.vue'
import ErrorState from '@/components/ErrorState.vue'

const {
  logs, loading, error, hasData,
  filters, pagination,
  stats, statsLoading,
  isAdmin,
  isCompact,
  detailsDialog, openDetailsDialog,
  handleSearch, handleResetFilters,
  handlePageChange, handlePageSizeChange,
} = useCommonLogsData()

const { t } = useI18n()
const sensitiveVisible = ref(false)

function updateFilters(partial: Partial<CommonLogsFilters>) {
  Object.assign(filters, partial)
}
</script>

<template>
  <div class="common-logs-view">
    <div class="common-logs-view__header">
      <div class="common-logs-view__header-left">
        <CommonLogsStats
          :stats="stats"
          :loading="statsLoading"
          :sensitive-visible="sensitiveVisible"
          @toggle-sensitive="sensitiveVisible = !sensitiveVisible"
        />
      </div>
      <div class="common-logs-view__header-right">
        <CompactModeToggle table-key="usageLogsCommon" />
      </div>
    </div>

    <CommonLogsToolbar
      :filters="filters"
      :loading="loading"
      :is-admin="isAdmin"
      @search="handleSearch"
      @reset="handleResetFilters"
      @update:filters="updateFilters"
    />

    <ErrorState
      v-if="error"
      :description="error"
      @retry="handleSearch"
    />

    <ElEmpty
      v-else-if="!hasData && !loading"
      :description="t('usageLogs.empty')"
    />

    <template v-else>
      <CommonLogsTable
        :logs="logs"
        :loading="loading"
        :is-compact="isCompact"
        :is-admin="isAdmin"
        :sensitive-visible="sensitiveVisible"
        @view-details="openDetailsDialog"
      />

      <div class="common-logs-view__pagination">
        <ElPagination
          :current-page="pagination.page"
          :page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="handlePageChange"
          @size-change="handlePageSizeChange"
        />
      </div>
    </template>

    <CommonLogsDetailsDialog
      v-model:open="detailsDialog.visible"
      :log="detailsDialog.log"
    />
  </div>
</template>

<style scoped lang="scss">
.common-logs-view {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-4);

  &__header {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-3);
    align-items: center;
    justify-content: space-between;
  }

  &__header-right {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
  }

  &__pagination {
    display: flex;
    justify-content: flex-end;
  }
}
</style>
