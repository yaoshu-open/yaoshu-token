<script setup lang="ts">
/**
 * 模型管理页。
 *
 * 第一批：表格主体 + 紧凑模式 T-MO-01。
 * 第二批：编辑抽屉 + 对话框（描述/未配置/供应商/同步/预填组）T-MO-02/03/04。
 */
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElAlert } from 'element-plus'
import ModelsToolbar from '@/components/model/ModelsToolbar.vue'
import ModelsTable from '@/components/model/ModelsTable.vue'
import ModelsPrimaryButtons from '@/components/model/ModelsPrimaryButtons.vue'
import ModelMutateDrawer from '@/components/model/ModelMutateDrawer.vue'
import DescriptionDialog from '@/components/model/dialogs/DescriptionDialog.vue'
import MissingModelsDialog from '@/components/model/dialogs/MissingModelsDialog.vue'
import VendorMutateDialog from '@/components/model/dialogs/VendorMutateDialog.vue'
import SyncWizardDialog from '@/components/model/dialogs/SyncWizardDialog.vue'
import UpstreamConflictDialog from '@/components/model/dialogs/UpstreamConflictDialog.vue'
import PrefillGroupManagementDialog from '@/components/model/dialogs/PrefillGroupManagementDialog.vue'
import { useModelsData } from '@/composables/model/useModelsData'
import { useModelActions } from '@/composables/model/useModelActions'
import { useMobile } from '@/composables/useMobile'
import type { Model } from '@/api/model/types'
import type { SyncDiffData } from '@/api/model/types'

const { t } = useI18n()
const isMobile = useMobile()

const {
  models,
  vendors,
  loading,
  error,
  filters,
  pagination,
  selectedIds,
  isCompact,
  vendorOptions,
  fetchModels,
  handleSearch,
  handleResetFilters,
  handlePageChange,
  handlePageSizeChange,
  handleSelectionChange,
  clearSelection,
} = useModelsData()

const {
  toggleModelStatus,
  deleteModelById,
  batchDeleteModels,
  batchToggleStatus,
  scanMissingModels,
} = useModelActions(fetchModels)

// ============================================================================
// 编辑/创建抽屉（第二批）
// ============================================================================

const mutateDrawerRef = ref<InstanceType<typeof ModelMutateDrawer>>()

// ============================================================================
// 对话框状态（第二批）
// ============================================================================

const descriptionOpen = ref(false)
const descriptionModel = ref<Model | null>(null)
const missingModelsOpen = ref(false)
const missingModels = ref<string[]>([])
const syncWizardOpen = ref(false)
const vendorDialogOpen = ref(false)
const prefillGroupsOpen = ref(false)
const upstreamConflictOpen = ref(false)
const upstreamConflicts = ref<SyncDiffData['conflicts']>([])

// 选中模型的名称列表（用于 T-MO-04 批量添加到预填组）
const selectedModelNames = computed(() =>
  models.value
    .filter((m) => selectedIds.value.includes(m.id))
    .map((m) => m.modelName)
)

// ============================================================================
// 行操作
// ============================================================================

function handleRowAction(action: string, model: Model): void {
  switch (action) {
    case 'edit':
      mutateDrawerRef.value?.initUpdate(model.id)
      break
    case 'enable':
    case 'disable':
      toggleModelStatus(model)
      break
    case 'description':
      descriptionModel.value = model
      descriptionOpen.value = true
      break
    case 'delete':
      deleteModelById(model.id, model.modelName)
      break
  }
}

// ============================================================================
// 顶部按钮操作
// ============================================================================

function handleAdd(): void {
  mutateDrawerRef.value?.initCreate()
}

async function handleMissingModels(): Promise<void> {
  missingModels.value = await scanMissingModels()
  if (missingModels.value.length === 0) {
    ElMessage.success(t('model.list.noMissingModels'))
  } else {
    missingModelsOpen.value = true
  }
}

function handleSync(): void {
  syncWizardOpen.value = true
}

function handleSyncConflicts(conflicts: SyncDiffData['conflicts']): void {
  upstreamConflicts.value = conflicts
  upstreamConflictOpen.value = true
}

function handleManageVendors(): void {
  vendorDialogOpen.value = true
}

function handlePrefillGroups(): void {
  prefillGroupsOpen.value = true
}

// ============================================================================
// 批量操作
// ============================================================================

async function handleBatchDelete(): Promise<void> {
  if (!selectedIds.value.length) return
  await batchDeleteModels(selectedIds.value)
  clearSelection()
}

async function handleBatchEnable(): Promise<void> {
  if (!selectedIds.value.length) return
  await batchToggleStatus(selectedIds.value, models.value, true)
  clearSelection()
}

async function handleBatchDisable(): Promise<void> {
  if (!selectedIds.value.length) return
  await batchToggleStatus(selectedIds.value, models.value, false)
  clearSelection()
}

// T-MO-04 批量添加到预填组
function handleBatchAddToPrefill(): void {
  if (!selectedIds.value.length) return
  prefillGroupsOpen.value = true
}

// ============================================================================
// 分页
// ============================================================================

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100]
</script>

<template>
  <div class="model-view">
    <!-- 页面标题 -->
    <div class="model-view__header">
      <div class="model-view__header-left">
        <h2 class="model-view__title">
          <i class="i-ep-cpu" />
          {{ t('model.list.title') }}
        </h2>
        <p class="model-view__subtitle">
          {{ t('model.list.subtitle') }}
        </p>
      </div>
      <ModelsPrimaryButtons
        @add="handleAdd"
        @missing-models="handleMissingModels"
        @sync-upstream="handleSync"
        @manage-vendors="handleManageVendors"
        @prefill-groups="handlePrefillGroups"
      />
    </div>

    <!-- 工具栏 -->
    <ModelsToolbar
      :filters="filters"
      :is-compact="isCompact"
      :vendor-options="vendorOptions"
      @update:filters="(val) => Object.assign(filters, val)"
      @search="handleSearch"
      @reset-filters="handleResetFilters"
      @toggle-compact="() => {}"
    />

    <!-- T-MO-02 市场展示提示 Banner -->
    <el-alert
      v-if="models.length > 0"
      :title="t('model.marketplace.title')"
      type="info"
      :description="t('model.marketplace.description')"
      show-icon
      :closable="true"
      class="model-view__market-alert"
    />

    <!-- 错误提示 -->
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
      class="model-view__error"
    />

    <!-- 批量操作栏 -->
    <div
      v-if="selectedIds.length > 0"
      class="model-view__bulk-actions"
    >
      <span class="model-view__bulk-count">
        {{ t('common.selected') }} {{ selectedIds.length }}
      </span>
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
        @click="handleBatchAddToPrefill"
      >
        {{ t('model.actions.prefillGroups') }}
      </el-button>
      <el-button
        size="small"
        text
        @click="clearSelection"
      >
        {{ t('common.clearSelection') }}
      </el-button>
    </div>

    <!-- 表格主体 -->
    <div class="model-view__table-wrapper">
      <ModelsTable
        :models="models"
        :loading="loading"
        :is-compact="isCompact"
        :vendors="vendors"
        :selected-ids="selectedIds"
        @selection-change="handleSelectionChange"
        @row-action="handleRowAction"
      />
    </div>

    <!-- 分页 -->
    <div class="model-view__pagination">
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

    <!-- 编辑抽屉（第二批 T-MO-02/03/04） -->
    <ModelMutateDrawer
      ref="mutateDrawerRef"
      @success="fetchModels"
    />

    <!-- 描述查看对话框 -->
    <DescriptionDialog
      v-model:visible="descriptionOpen"
      :model-name="descriptionModel?.modelName ?? ''"
      :description="descriptionModel?.description ?? ''"
    />

    <!-- 未配置模型扫描对话框 -->
    <MissingModelsDialog
      v-model:visible="missingModelsOpen"
      :models="missingModels"
    />

    <!-- 供应商管理对话框（T-MO-03） -->
    <VendorMutateDialog
      v-model:visible="vendorDialogOpen"
      @success="fetchModels"
    />

    <!-- 上游同步向导对话框 -->
    <SyncWizardDialog
      v-model:visible="syncWizardOpen"
      @success="fetchModels"
      @conflicts="handleSyncConflicts"
    />

    <!-- 上游冲突处理对话框 -->
    <UpstreamConflictDialog
      v-model:visible="upstreamConflictOpen"
      :conflicts="upstreamConflicts"
    />

    <!-- 预填组管理对话框（T-MO-04 批量添加到预填组） -->
    <PrefillGroupManagementDialog
      v-model:visible="prefillGroupsOpen"
      :selected-models="selectedModelNames"
    />
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens' as *;

.model-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: $spacing-4 $spacing-6;
}

.model-view__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: $spacing-2;
}

.model-view__title {
  display: flex;
  gap: $spacing-2;
  align-items: center;
  margin: 0 0 $spacing-1;
  font-size: $font-size-xl;
  font-weight: $font-weight-semibold;
  color: var(--el-text-color-primary);
}

.model-view__subtitle {
  margin: 0;
  font-size: $font-size-sm;
  color: var(--el-text-color-secondary);
}

.model-view__market-alert,
.model-view__error {
  margin-bottom: $spacing-3;
}

.model-view__table-wrapper {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.model-view__pagination {
  display: flex;
  justify-content: flex-end;
  padding: $spacing-3 0;
}

.model-view__bulk-actions {
  display: flex;
  gap: $spacing-2;
  align-items: center;
  padding: $spacing-2 $spacing-3;
  margin-bottom: $spacing-2;
  background: var(--el-fill-color-light);
  border-radius: $radius-sm;
}

.model-view__bulk-count {
  margin-right: $spacing-1;
  font-size: $font-size-sm;
  color: var(--el-text-color-primary);
}
</style>
