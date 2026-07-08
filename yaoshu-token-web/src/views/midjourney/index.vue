<script setup lang="ts">
/**
 * Midjourney 任务日志页（容器）。
 *
 * 职责：消费 useMjLogsData → 三态分发 → props 传入木偶组件。
 */
import { ElEmpty, ElIcon, ElPagination, ElSkeleton } from 'element-plus'
import { View } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { useMjLogsData } from '@/composables/midjourney/useMjLogsData'
import type { MjLogsFilters } from '@/composables/midjourney/useMjLogsData'
import MjBanner from '@/components/midjourney/MjBanner.vue'
import MjLogsToolbar from '@/components/midjourney/MjLogsToolbar.vue'
import MjLogsTable from '@/components/midjourney/MjLogsTable.vue'
import MjImageDialog from '@/components/midjourney/MjImageDialog.vue'
import MjPromptDialog from '@/components/midjourney/MjPromptDialog.vue'
import CompactModeToggle from '@/components/CompactModeToggle.vue'
import ErrorState from '@/components/ErrorState.vue'

const {
  logs,
  loading,
  error,
  hasData,
  filters,
  pagination,
  isAdmin,
  isCompact,
  showBanner,
  dismissBanner,
  imageDialog,
  promptDialog,
  openImageDialog,
  openPromptDialog,
  handleSearch,
  handleResetFilters,
  handlePageChange,
  handlePageSizeChange,
} = useMjLogsData()

const { t } = useI18n()

function updateFilters(partial: Partial<MjLogsFilters>): void {
  Object.assign(filters, partial)
}
</script>

<template>
  <div class="mj-page">
    <!-- T-MJ-01 回调提醒 banner -->
    <MjBanner
      :visible="showBanner"
      @dismiss="dismissBanner"
    />

    <!-- 筛选区 -->
    <MjLogsToolbar
      :filters="filters"
      :loading="loading"
      :is-admin="isAdmin"
      @search="handleSearch"
      @reset="handleResetFilters"
      @update:filters="updateFilters"
    />

    <!-- 操作行：标题 + 紧凑模式 -->
    <div class="mj-page__header">
      <div class="mj-page__title">
        <el-icon class="mj-page__title-icon">
          <View />
        </el-icon>
        <span>{{ t('midjourney.title') }}</span>
      </div>
      <CompactModeToggle table-key="mjLogs" />
    </div>

    <!-- 加载态 -->
    <div
      v-if="loading && !hasData"
      class="mj-page__skeleton"
    >
      <ElSkeleton
        :rows="8"
        animated
      />
    </div>

    <!-- 错误态 -->
    <ErrorState
      v-else-if="error"
      :description="error"
      @retry="handleSearch"
    />

    <!-- 空数据态 -->
    <ElEmpty
      v-else-if="!hasData"
      :description="t('midjourney.common.none')"
    />

    <!-- 数据态：表格 + 分页 -->
    <template v-else>
      <MjLogsTable
        :logs="logs"
        :loading="loading"
        :is-compact="isCompact"
        :is-admin="isAdmin"
        @view-image="openImageDialog"
        @view-prompt="openPromptDialog"
      />

      <div class="mj-page__pagination">
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

    <!-- 图片预览对话框 -->
    <MjImageDialog
      v-model="imageDialog.visible"
      :image-url="imageDialog.url"
    />

    <!-- Prompt/失败原因查看对话框 -->
    <MjPromptDialog
      v-model="promptDialog.visible"
      :content="promptDialog.content"
      :title="promptDialog.title"
    />
  </div>
</template>

<style scoped>
.mj-page {
  padding: var(--ys-spacing-4);
}

.mj-page__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--ys-spacing-3);
}

.mj-page__title {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: var(--ys-font-size-base);
  font-weight: 500;
  color: var(--el-text-color-regular);
}

.mj-page__title-icon {
  font-size: var(--ys-font-size-lg);
  color: var(--el-text-color-secondary);
}

.mj-page__skeleton {
  padding: var(--ys-spacing-4) 0;
}

.mj-page__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--ys-spacing-4);
}
</style>
