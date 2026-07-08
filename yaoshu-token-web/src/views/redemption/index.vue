<script setup lang="ts">
/**
 * 兑换码管理页（CC-2）。
 * 全量迁移：表格 + 紧凑模式 + 创建/编辑抽屉 + 批量清理失效。
 */
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElButton, ElPagination, ElAlert } from 'element-plus'
import RedemptionToolbar from '@/components/redemption/RedemptionToolbar.vue'
import RedemptionTable from '@/components/redemption/RedemptionTable.vue'
import RedemptionMutateDrawer from '@/components/redemption/RedemptionMutateDrawer.vue'
import { useRedemptionsData } from '@/composables/redemption/useRedemptionsData'
import { useRedemptionActions } from '@/composables/redemption/useRedemptionActions'
import { useMobile } from '@/composables/useMobile'
import type { Redemption, RedemptionRowAction } from '@/api/redemption/types'

const { t } = useI18n()
const isMobile = useMobile()

const {
  redemptions,
  loading,
  error,
  filters,
  pagination,
  selectedIds,
  fetchRedemptions,
  handleSearch,
  handleResetFilters,
  handlePageChange,
  handlePageSizeChange,
  handleSelectionChange,
} = useRedemptionsData()

const { handleDelete, handleClearInvalid } = useRedemptionActions(fetchRedemptions)

// 创建/编辑抽屉
const drawerOpen = ref(false)
const editingId = ref<number | null>(null)

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100]

function handleAdd(): void {
  editingId.value = null
  drawerOpen.value = true
}

function handleRowAction(action: RedemptionRowAction, row: Redemption): void {
  switch (action) {
    case 'edit':
      editingId.value = row.id
      drawerOpen.value = true
      break
    case 'delete':
      handleDelete(row.id, row.name)
      break
    case 'copyKey':
      // copyKey 在 Table 组件内直接处理（navigator.clipboard）
      break
  }
}
</script>

<template>
  <div class="redemption-view">
    <div class="redemption-view__header">
      <div>
        <h2 class="redemption-view__title">
          <i class="i-ep-gift" />
          {{ t('nav.redemptionCodes') }}
        </h2>
        <p class="redemption-view__subtitle">
          {{ t('redemption.list.subtitle') }}
        </p>
      </div>
      <div class="redemption-view__header-actions">
        <el-button
          size="small"
          @click="handleClearInvalid"
        >
          {{ t('redemption.actions.clearInvalid') }}
        </el-button>
        <el-button
          type="primary"
          size="small"
          @click="handleAdd"
        >
          {{ t('redemption.actions.add') }}
        </el-button>
      </div>
    </div>

    <RedemptionToolbar
      :filters="filters"
      :is-compact="false"
      @update:filters="(val) => Object.assign(filters, val)"
      @search="handleSearch"
      @reset-filters="handleResetFilters"
    />

    <el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
      style="margin-bottom: var(--ys-spacing-3)"
    />

    <div class="redemption-view__table-wrapper">
      <RedemptionTable
        :redemptions="redemptions"
        :loading="loading"
        :is-compact="false"
        :selected-ids="selectedIds"
        @selection-change="handleSelectionChange"
        @row-action="handleRowAction"
      />
    </div>

    <div class="redemption-view__pagination">
      <el-pagination
        :current-page="pagination.page"
        :page-size="pagination.pageSize"
        :page-sizes="PAGE_SIZE_OPTIONS"
        :total="pagination.total"
        :layout="isMobile ? 'prev, pager, next' : 'total, sizes, prev, pager, next, jumper'"
        :small="isMobile"
        background
        @current-change="handlePageChange"
        @size-change="handlePageSizeChange"
      />
    </div>

    <RedemptionMutateDrawer
      v-model:visible="drawerOpen"
      :editing-id="editingId"
      @success="fetchRedemptions"
    />
  </div>
</template>

<style scoped>
.redemption-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: var(--ys-spacing-4) var(--ys-spacing-6);
}

.redemption-view__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: var(--ys-spacing-2);
}

.redemption-view__title {
  display: flex;
  gap: var(--ys-spacing-2);
  align-items: center;
  margin: 0 0 var(--ys-spacing-1);
  font-size: var(--ys-font-size-xl);
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.redemption-view__subtitle {
  margin: 0;
  font-size: var(--ys-font-size-sm);
  color: var(--el-text-color-secondary);
}

.redemption-view__header-actions {
  display: flex;
  gap: var(--ys-spacing-2);
}

.redemption-view__table-wrapper {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.redemption-view__pagination {
  display: flex;
  justify-content: flex-end;
  padding: var(--ys-spacing-3) 0;
}
</style>
