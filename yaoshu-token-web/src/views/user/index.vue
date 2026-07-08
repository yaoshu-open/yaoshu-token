<script setup lang="ts">
/**
 * 用户管理页。
 *
 * 全量迁移：表格 + 紧凑模式(T-US-02) + 编辑抽屉 + 额度调整(T-US-01) + 批量操作(T-US-03)。
 */
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElButton } from 'element-plus'
import UserToolbar from '@/components/user/UserToolbar.vue'
import UserTable from '@/components/user/UserTable.vue'
import UserMutateDrawer from '@/components/user/UserMutateDrawer.vue'
import UserQuotaDialog from '@/components/user/dialogs/UserQuotaDialog.vue'
import { useUsersData } from '@/composables/user/useUsersData'
import { useUserActions } from '@/composables/user/useUserActions'
import { useMobile } from '@/composables/useMobile'
import type { User } from '@/api/user/types'

const { t } = useI18n()
const isMobile = useMobile()

const {
  users, loading, error, filters, pagination, selectedIds, isCompact,
  fetchUsers, handleSearch, handleResetFilters,
  handlePageChange, handlePageSizeChange, handleSelectionChange, clearSelection,
} = useUsersData()

const {
  handleManageUser, deleteUserId,
  batchDelete, batchToggleStatus,
} = useUserActions(fetchUsers)

// 编辑抽屉
const editDrawerOpen = ref(false)
const editingId = ref<number | null>(null)

// 额度对话框
const quotaDialogOpen = ref(false)
const quotaUser = ref<User | null>(null)

// ============================================================================
// 行操作
// ============================================================================

function handleRowAction(action: string, user: User): void {
  switch (action) {
    case 'edit':
      editingId.value = user.id
      editDrawerOpen.value = true
      break
    case 'quota':
      quotaUser.value = user
      quotaDialogOpen.value = true
      break
    case 'promote':
    case 'demote':
    case 'enable':
    case 'disable':
      handleManageUser(user.id, action)
      break
    case 'delete':
      deleteUserId(user.id, user.username)
      break
  }
}

// ============================================================================
// 顶部操作
// ============================================================================

function handleAdd(): void {
  editingId.value = null
  editDrawerOpen.value = true
}

// ============================================================================
// T-US-03 批量操作补齐（批量删除/启用/禁用）
// ============================================================================

async function handleBatchDelete(): Promise<void> {
  if (!selectedIds.value.length) return
  await batchDelete(selectedIds.value)
  clearSelection()
}

async function handleBatchEnable(): Promise<void> {
  if (!selectedIds.value.length) return
  await batchToggleStatus(selectedIds.value, users.value, true)
  clearSelection()
}

async function handleBatchDisable(): Promise<void> {
  if (!selectedIds.value.length) return
  await batchToggleStatus(selectedIds.value, users.value, false)
  clearSelection()
}

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100]
</script>

<template>
  <div class="user-view">
    <div class="user-view__header">
      <div>
        <h2 class="user-view__title">
          <i class="i-ep-user" />
          {{ t('nav.users') }}
        </h2>
        <p class="user-view__subtitle">
          {{ t('user.list.subtitle') }}
        </p>
      </div>
      <el-button
        type="primary"
        size="small"
        @click="handleAdd"
      >
        {{ t('user.actions.add') }}
      </el-button>
    </div>

    <UserToolbar
      :filters="filters"
      :is-compact="isCompact"
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

    <!-- T-US-03 批量操作补齐 -->
    <div
      v-if="selectedIds.length > 0"
      class="user-view__bulk-actions"
    >
      <span class="user-view__bulk-count">{{ t('common.selected') }} {{ selectedIds.length }}</span>
      <el-button
        size="small"
        @click="handleBatchEnable"
      >
        {{ t('common.enable') }}
      </el-button>
      <el-button
        size="small"
        @click="handleBatchDisable"
      >
        {{ t('common.disable') }}
      </el-button>
      <el-button
        size="small"
        type="danger"
        @click="handleBatchDelete"
      >
        {{ t('common.delete') }}
      </el-button>
      <el-button
        size="small"
        text
        @click="clearSelection"
      >
        {{ t('common.clearSelection') }}
      </el-button>
    </div>

    <div class="user-view__table-wrapper">
      <UserTable
        :users="users"
        :loading="loading"
        :is-compact="isCompact"
        :selected-ids="selectedIds"
        @selection-change="handleSelectionChange"
        @row-action="handleRowAction"
      />
    </div>

    <div class="user-view__pagination">
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

    <UserMutateDrawer
      v-model:visible="editDrawerOpen"
      :editing-id="editingId"
      @success="fetchUsers"
    />

    <UserQuotaDialog
      v-model:visible="quotaDialogOpen"
      :user="quotaUser"
      @success="fetchUsers"
    />
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens' as *;

.user-view { display: flex; flex-direction: column; height: 100%; padding: $spacing-4 $spacing-6; }
.user-view__header { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: $spacing-2; }
.user-view__title { display: flex; gap: $spacing-2; align-items: center; margin: 0 0 $spacing-1; font-size: $font-size-xl; font-weight: $font-weight-semibold; color: var(--el-text-color-primary); }
.user-view__subtitle { margin: 0; font-size: $font-size-sm; color: var(--el-text-color-secondary); }
.user-view__table-wrapper { flex: 1; min-height: 0; overflow: auto; }
.user-view__pagination { display: flex; justify-content: flex-end; padding: $spacing-3 0; }
.user-view__bulk-actions { display: flex; gap: $spacing-2; align-items: center; padding: $spacing-2 $spacing-3; margin-bottom: $spacing-2; background: var(--el-fill-color-light); border-radius: $radius-sm; }
.user-view__bulk-count { margin-right: $spacing-1; font-size: $font-size-sm; color: var(--el-text-color-primary); }
</style>
