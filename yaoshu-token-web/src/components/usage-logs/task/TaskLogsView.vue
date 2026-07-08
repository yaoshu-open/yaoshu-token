<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useTaskLogsData } from '@/composables/usage-logs/useTaskLogsData'
import type { TaskLogsFilters } from '@/composables/usage-logs/useTaskLogsData'
import TaskLogsToolbar from './TaskLogsToolbar.vue'
import TaskLogsTable from './TaskLogsTable.vue'
import TaskAudioPreviewDialog from './TaskAudioPreviewDialog.vue'
import TaskFailReasonDialog from './TaskFailReasonDialog.vue'
import CompactModeToggle from '@/components/CompactModeToggle.vue'
import ErrorState from '@/components/ErrorState.vue'

const {
  logs, loading, error, hasData,
  filters, pagination,
  isAdmin, isCompact,
  audioDialog, failReasonDialog,
  openAudioDialog, openFailReasonDialog,
  handleSearch, handleResetFilters,
  handlePageChange, handlePageSizeChange,
} = useTaskLogsData()

const { t } = useI18n()

function updateFilters(partial: Partial<TaskLogsFilters>) {
  Object.assign(filters, partial)
}
</script>

<template>
  <div class="task-logs-view">
    <div class="task-logs-view__header">
      <CompactModeToggle table-key="usageLogsTask" />
    </div>

    <TaskLogsToolbar
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
      <TaskLogsTable
        :logs="logs"
        :loading="loading"
        :is-compact="isCompact"
        :is-admin="isAdmin"
        @preview-audio="openAudioDialog"
        @view-fail-reason="openFailReasonDialog"
      />

      <div class="task-logs-view__pagination">
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

    <TaskAudioPreviewDialog
      v-model:open="audioDialog.visible"
      :log="audioDialog.log"
    />

    <TaskFailReasonDialog
      v-model:open="failReasonDialog.visible"
      :content="failReasonDialog.content"
    />
  </div>
</template>

<style scoped lang="scss">
.task-logs-view {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-4);

  &__header {
    display: flex;
    justify-content: flex-end;
  }

  &__pagination {
    display: flex;
    justify-content: flex-end;
  }
}
</style>
