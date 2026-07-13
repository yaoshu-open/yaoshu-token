<script setup lang="ts">
/**
 * 渠道管理页。
 *
 * 第一批：表格主体 + 紧凑模式 T-CH-01/T-CH-02。
 * 第二批：编辑抽屉 T-CH-04。
 * 第三批：高级对话框 T-CH-05（测试/余额/复制）。
 */
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import ChannelsToolbar from '@/components/channel/ChannelsToolbar.vue'
import ChannelsTable from '@/components/channel/ChannelsTable.vue'
import EditTagDialog from '@/components/channel/EditTagDialog.vue'
import TagBatchEditDialog from '@/components/channel/dialogs/TagBatchEditDialog.vue'
import ChannelMutateDrawer from '@/components/channel/ChannelMutateDrawer.vue'
import ChannelTestDialog from '@/components/channel/dialogs/ChannelTestDialog.vue'
import ChannelBatchTestResultDialog from '@/components/channel/dialogs/ChannelBatchTestResultDialog.vue'
import ChannelKeyDialog from '@/components/channel/dialogs/ChannelKeyDialog.vue'
import BalanceQueryDialog from '@/components/channel/dialogs/BalanceQueryDialog.vue'
import CopyChannelDialog from '@/components/channel/dialogs/CopyChannelDialog.vue'
import MultiKeyManageDialog from '@/components/channel/dialogs/MultiKeyManageDialog.vue'
import UpstreamUpdateDialog from '@/components/channel/dialogs/UpstreamUpdateDialog.vue'
import CodexUsageDialog from '@/components/channel/dialogs/CodexUsageDialog.vue'
import OllamaModelsDialog from '@/components/channel/dialogs/OllamaModelsDialog.vue'
import ModelDiagnoseDialog from '@/components/channel/dialogs/ModelDiagnoseDialog.vue'
import { useChannelsData } from '@/composables/channel/useChannelsData'
import { useChannelUpstreamUpdates } from '@/composables/channel/useChannelUpstreamUpdates'
import { useGlobalPassThrough } from '@/composables/channel/useGlobalPassThrough'
import { useMobile } from '@/composables/useMobile'
import {
  batchDeleteChannels,
  deleteChannel,
  deleteDisabledChannels,
  enableTagChannels,
  disableTagChannels,
  fetchUpstreamModels,
  fixChannelAbilities,
  testAllChannels,
  testChannelsByIds,
  updateAllChannelsBalance,
  updateChannel
} from '@/api/channel'
import { CHANNELS_TABLE_PAGE_SIZE_OPTIONS, CHANNEL_STATUS, MODEL_FETCHABLE_TYPES } from '@/api/channel/constants'
import { parseUpstreamUpdateMeta } from '@/lib/channel/upstream-update-utils'
import type { Channel } from '@/api/channel/types'
import type { ChannelBatchTestResponse } from '@/api/channel/types'

const { t } = useI18n()
const isMobile = useMobile()

const { globalPassThroughEnabled } = useGlobalPassThrough()

const {
  channels,
  loading,
  error,
  filters,
  sort,
  pagination,
  selectedIds,
  isCompact,
  hasSelection,
  selectedCount,
  fetchChannels,
  handlePageChange,
  handlePageSizeChange,
  handleSortChange,
  handleSearch,
  handleResetFilters,
  handleSelectionChange,
  handleSelectAll,
  toggleCompact
} = useChannelsData()

// ============================================================================
// 标签编辑（保留已有功能）
// ============================================================================

const editTagOpen = ref(false)
const editTagValue = ref('')

// 标签批量编辑（按标签覆盖该标签下所有渠道配置）
const tagBatchOpen = ref(false)
const tagBatchValue = ref('')

// ============================================================================
// 编辑抽屉（T-CH-04 第二批）
// ============================================================================

const editDrawerOpen = ref(false)
const editingChannelId = ref<number | null>(null)

// ============================================================================
// 第三批对话框（T-CH-05）
// ============================================================================

const testDialogOpen = ref(false)
const testDialogChannel = ref<Channel | null>(null)
const balanceDialogOpen = ref(false)
const balanceDialogChannel = ref<Channel | null>(null)
const copyDialogOpen = ref(false)
const copyDialogChannel = ref<Channel | null>(null)
const viewKeyDialogOpen = ref(false)
const viewKeyDialogChannel = ref<Channel | null>(null)

// ============================================================================
// 第四批对话框（T-CH-06/07/08/09）
// ============================================================================

const multiKeyDialogOpen = ref(false)
const multiKeyDialogChannel = ref<Channel | null>(null)
const codexUsageDialogOpen = ref(false)
const codexUsageDialogChannel = ref<Channel | null>(null)
const ollamaDialogOpen = ref(false)
const ollamaDialogChannel = ref<Channel | null>(null)

// 模型可用性诊断
const diagnoseDialogOpen = ref(false)

// 上游更新 composable（T-CH-07）
const upstream = useChannelUpstreamUpdates(() => fetchChannels())

function openEditTag(): void {
  if (!selectedIds.value.length) {
    ElMessage.warning(t('channel.bulk.selectFirst'))
    return
  }
  editTagOpen.value = true
}

// ============================================================================
// 标签行操作（表格 tag 列下拉菜单：编辑标签名 / 批量编辑 / 启用·禁用全部）
// ============================================================================

async function handleTagAction(action: string, tag: string): Promise<void> {
  switch (action) {
    case 'editTag':
      editTagValue.value = tag
      editTagOpen.value = true
      break
    case 'batchEdit':
      tagBatchValue.value = tag
      tagBatchOpen.value = true
      break
    case 'enableAll':
      try {
        await ElMessageBox.confirm(
          t('channel.tagRow.enableConfirm', { tag }),
          t('common.warning'),
          { type: 'warning' }
        )
        await enableTagChannels(tag)
        ElMessage.success(t('channel.tagRow.enableSuccess'))
        fetchChannels()
      } catch (e) {
        if (e !== 'cancel') ElMessage.error(t('common.operationFailed'))
      }
      break
    case 'disableAll':
      try {
        await ElMessageBox.confirm(
          t('channel.tagRow.disableConfirm', { tag }),
          t('common.warning'),
          { type: 'warning' }
        )
        await disableTagChannels(tag)
        ElMessage.success(t('channel.tagRow.disableSuccess'))
        fetchChannels()
      } catch (e) {
        if (e !== 'cancel') ElMessage.error(t('common.operationFailed'))
      }
      break
  }
}

// ============================================================================
// 行操作
// ============================================================================

function handleRowAction(action: string, channel: Channel): void {
  switch (action) {
    case 'test':
      testDialogChannel.value = channel
      testDialogOpen.value = true
      break
    case 'edit':
      editingChannelId.value = channel.id
      editDrawerOpen.value = true
      break
    case 'copy':
      copyDialogChannel.value = channel
      copyDialogOpen.value = true
      break
    case 'balance':
      balanceDialogChannel.value = channel
      balanceDialogOpen.value = true
      break
    case 'fetchModels':
      fetchUpstreamModels(channel.id)
        .then(() => ElMessage.success(t('channel.actions.fetchModelsSuccess')))
        .catch(() => ElMessage.error(t('common.operationFailed')))
      break
    case 'viewKey':
      viewKeyDialogChannel.value = channel
      viewKeyDialogOpen.value = true
      break
    case 'multiKey':
      multiKeyDialogChannel.value = channel
      multiKeyDialogOpen.value = true
      break
    case 'codexUsage':
      codexUsageDialogChannel.value = channel
      codexUsageDialogOpen.value = true
      break
    case 'ollama':
      ollamaDialogChannel.value = channel
      ollamaDialogOpen.value = true
      break
    case 'upstream':
      if (MODEL_FETCHABLE_TYPES.has(channel.type)) {
        const meta = parseUpstreamUpdateMeta(channel.settings)
        if (meta.pendingAddModels.length > 0 || meta.pendingRemoveModels.length > 0) {
          upstream.openModal(
            channel,
            meta.pendingAddModels,
            meta.pendingRemoveModels,
            meta.pendingAddModels.length > 0 ? 'add' : 'remove'
          )
        } else {
          void upstream.detectChannelUpdates(channel)
        }
      }
      break
    case 'delete':
      ElMessageBox.confirm(
        t('channel.actions.deleteConfirm', { name: channel.name }),
        t('common.warning'),
        { type: 'warning' }
      )
        .then(() => deleteChannel(channel.id))
        .then(() => {
          ElMessage.success(t('channel.actions.deleteSuccess'))
          fetchChannels()
        })
        .catch((e) => {
          if (e !== 'cancel') ElMessage.error(t('common.operationFailed'))
        })
      break
  }
}

// ============================================================================
// 批量操作
// ============================================================================

async function handleBatchDelete(): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('channel.bulk.deleteConfirm', { count: selectedCount.value }),
      t('common.warning'),
      { type: 'warning' }
    )
    await batchDeleteChannels({ ids: selectedIds.value })
    ElMessage.success(t('channel.bulk.deleteSuccess'))
    fetchChannels()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('common.operationFailed'))
  }
}
async function batchUpdateStatus(
  status: number,
  confirmKey: string,
  successKey: string
): Promise<void> {
  if (!selectedIds.value.length) {
    ElMessage.warning(t('channel.bulk.selectFirst'))
    return
  }
  try {
    await ElMessageBox.confirm(
      t(confirmKey, { count: selectedCount.value }),
      t('common.warning'),
      { type: 'warning' }
    )
    const promises = selectedIds.value.map((id) => updateChannel(id, { status }))
    const results = await Promise.allSettled(promises)
    const successCount = results.filter((r) => r.status === 'fulfilled').length
    const failCount = results.length - successCount
    if (successCount > 0) {
      ElMessage.success(t(successKey, { count: successCount }))
      fetchChannels()
    }
    if (failCount > 0) {
      ElMessage.error(t('channel.bulk.partialFail', { count: failCount }))
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('common.operationFailed'))
  }
}

function handleBatchEnable(): void {
  void batchUpdateStatus(
    CHANNEL_STATUS.ENABLED,
    'channel.bulk.enableConfirm',
    'channel.bulk.enableSuccess'
  )
}

function handleBatchDisable(): void {
  void batchUpdateStatus(
    CHANNEL_STATUS.MANUAL_DISABLED,
    'channel.bulk.disableConfirm',
    'channel.bulk.disableSuccess'
  )
}

// ============================================================================
// 主操作
// ============================================================================

const testAllLoading = ref(false)
const testAllResultVisible = ref(false)
const testAllResultData = ref<ChannelBatchTestResponse | null>(null)
const testAllResultLoading = ref(false)
const testAllResultError = ref(false)
const updateAllBalanceLoading = ref(false)

function handleAdd(): void {
  editingChannelId.value = null
  editDrawerOpen.value = true
}

async function handleTestAll(): Promise<void> {
  // 立即弹窗 + loading 态，让用户知道测试已触发
  testAllResultData.value = null
  testAllResultError.value = false
  testAllResultLoading.value = true
  testAllResultVisible.value = true
  testAllLoading.value = true
  try {
    // 选中渠道时调用按 ID 列表批量测试，未选中时调用全量测试
    const hasSelection = selectedIds.value.length > 0
    const res = hasSelection
      ? await testChannelsByIds(selectedIds.value)
      : await testAllChannels()
    if (res && typeof res.total === 'number') {
      ElMessage.success(t('channel.actions.testAllResult', { completed: res.completed, total: res.total }))
      if (res.results?.length) {
        testAllResultData.value = res
      }
    } else {
      ElMessage.success(t('channel.actions.testAllSuccess'))
    }
    await fetchChannels()
  } catch {
    testAllResultError.value = true
    ElMessage.error(t('common.operationFailed'))
  } finally {
    testAllResultLoading.value = false
    testAllLoading.value = false
  }
}

async function handleUpdateAllBalance(): Promise<void> {
  updateAllBalanceLoading.value = true
  try {
    await updateAllChannelsBalance()
    ElMessage.success(t('channel.actions.updateAllBalanceSuccess'))
    await fetchChannels()
  } catch {
    ElMessage.error(t('common.operationFailed'))
  } finally {
    updateAllBalanceLoading.value = false
  }
}

async function handleFixAbilities(): Promise<void> {
  try {
    await fixChannelAbilities()
    ElMessage.success(t('channel.actions.fixAbilitiesSuccess'))
  } catch {
    ElMessage.error(t('common.operationFailed'))
  }
}

async function handleDeleteDisabled(): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('channel.actions.deleteDisabledConfirm'),
      t('common.warning'),
      { type: 'warning' }
    )
    const res = await deleteDisabledChannels()
    ElMessage.success(t('channel.actions.deleteDisabledSuccess', { count: res ?? 0 }))
    fetchChannels()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('common.operationFailed'))
  }
}

// ============================================================================
// 排序代理（ChannelsTable emit 的 prop/order 转换为 useChannelsData 的格式）
// ============================================================================

function onSortChange(payload: { prop: string; order: 'ascending' | 'descending' | null }): void {
  handleSortChange(payload)
}

// ============================================================================
// 计算属性
// ============================================================================
</script>

<template>
  <div class="channel-view">
    <!-- 页面标题 -->
    <div class="channel-view__header">
      <h2 class="channel-view__title">
        <i class="i-ep-connection" />
        {{ t('channel.list.title') }}
      </h2>
      <p class="channel-view__subtitle">
        {{ t('channel.list.subtitle') }}
      </p>
    </div>

    <!-- 全局透传警告 Banner -->
    <el-alert
      v-if="globalPassThroughEnabled"
      :title="t('channel.banner.globalPassThrough')"
      type="warning"
      show-icon
      :closable="false"
      style="margin-bottom: var(--ys-spacing-3)"
    />

    <!-- 工具栏 -->
    <ChannelsToolbar
      :filters="filters"
      :is-compact="isCompact"
      :selected-count="selectedCount"
      :has-selection="hasSelection"
      :test-all-loading="testAllLoading"
      :update-all-balance-loading="updateAllBalanceLoading"
      @update:filters="(val) => Object.assign(filters, val)"
      @search="handleSearch"
      @reset-filters="handleResetFilters"
      @toggle-compact="toggleCompact"
      @add="handleAdd"
      @test-all="handleTestAll"
      @update-all-balance="handleUpdateAllBalance"
      @fix-abilities="handleFixAbilities"
      @delete-disabled="handleDeleteDisabled"
      @diagnose="diagnoseDialogOpen = true"
      @batch-delete="handleBatchDelete"
      @batch-set-tag="openEditTag"
      @batch-enable="handleBatchEnable"
      @batch-disable="handleBatchDisable"
      @clear-selection="handleSelectAll"
    />

    <!-- 错误提示 -->
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
      style="margin-bottom: var(--ys-spacing-3)"
    />

    <!-- 表格主体 -->
    <div class="channel-view__table-wrapper">
      <ChannelsTable
        :channels="channels"
        :loading="loading"
        :is-compact="isCompact"
        :selected-ids="selectedIds"
        :sort-by="sort.sortBy"
        :sort-order="sort.sortOrder"
        @selection-change="handleSelectionChange"
        @sort-change="onSortChange"
        @row-action="handleRowAction"
        @tag-action="handleTagAction"
      />
    </div>

    <!-- 分页 -->
    <div class="channel-view__pagination">
      <el-pagination
        :current-page="pagination.page"
        :page-size="pagination.pageSize"
        :page-sizes="CHANNELS_TABLE_PAGE_SIZE_OPTIONS"
        :total="pagination.total"
        :layout="isMobile ? 'prev, pager, next' : 'total, sizes, prev, pager, next, jumper'"
        :small="isMobile"
        background
        @current-change="handlePageChange"
        @size-change="handlePageSizeChange"
      />
    </div>

    <!-- 标签编辑弹窗（保留已有功能） -->
    <EditTagDialog
      v-model="editTagOpen"
      :tag="editTagValue"
      @success="fetchChannels"
    />

    <!-- 按标签批量编辑弹窗（T-CH-05 第三批 O5 接入） -->
    <TagBatchEditDialog
      v-model="tagBatchOpen"
      :tag="tagBatchValue"
      @success="fetchChannels"
    />

    <!-- 编辑抽屉（T-CH-04 第二批） -->
    <ChannelMutateDrawer
      v-model="editDrawerOpen"
      :channel-id="editingChannelId"
      @success="fetchChannels"
    />

    <!-- 渠道测试对话框（T-CH-05 第三批） -->
    <ChannelTestDialog
      v-model="testDialogOpen"
      :channel="testDialogChannel"
    />

    <!-- 批量渠道测试结果摘要对话框 -->
    <ChannelBatchTestResultDialog
      v-model:visible="testAllResultVisible"
      :data="testAllResultData"
      :loading="testAllResultLoading"
      :error="testAllResultError"
    />

    <!-- 余额查询对话框（T-CH-05 第三批） -->
    <BalanceQueryDialog
      v-model="balanceDialogOpen"
      :channel="balanceDialogChannel"
      @success="fetchChannels"
    />

    <!-- 复制渠道对话框（T-CH-05 第三批） -->
    <CopyChannelDialog
      v-model="copyDialogOpen"
      :channel="copyDialogChannel"
      @success="fetchChannels"
    />

    <!-- 密钥查看对话框（T-CH-05 第三批，集成安全验证） -->
    <ChannelKeyDialog
      v-model="viewKeyDialogOpen"
      :channel="viewKeyDialogChannel"
    />

    <!-- 多密钥管理对话框（T-CH-06 第四批） -->
    <MultiKeyManageDialog
      v-model="multiKeyDialogOpen"
      :channel="multiKeyDialogChannel"
      @success="fetchChannels"
    />

    <!-- 上游模型更新对话框（T-CH-07 第四批） -->
    <UpstreamUpdateDialog
      :model-value="upstream.showModal.value"
      :add-models="upstream.addModels.value"
      :remove-models="upstream.removeModels.value"
      :preferred-tab="upstream.preferredTab.value"
      :confirm-loading="upstream.applyLoading.value"
      @confirm="(data: { addModels: string[]; removeModels: string[] }) => upstream.applyUpdates(data)"
      @cancel="upstream.closeModal()"
      @update:model-value="(v: boolean) => { if (!v) upstream.closeModal() }"
    />

    <!-- Codex 用量对话框（T-CH-08 第四批） -->
    <CodexUsageDialog
      v-model="codexUsageDialogOpen"
      :channel="codexUsageDialogChannel"
    />

    <!-- Ollama 模型管理对话框（T-CH-09 第四批） -->
    <OllamaModelsDialog
      v-model="ollamaDialogOpen"
      :channel="ollamaDialogChannel"
      @success="fetchChannels"
    />

    <!-- 模型可用性诊断对话框 -->
    <ModelDiagnoseDialog v-model="diagnoseDialogOpen" />
  </div>
</template>

<style scoped>
.channel-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: var(--ys-spacing-4) var(--ys-spacing-6);
}

.channel-view__header {
  margin-bottom: var(--ys-spacing-2);
}

.channel-view__title {
  display: flex;
  gap: var(--ys-spacing-2);
  align-items: center;
  margin: 0 0 var(--ys-spacing-1);
  font-size: var(--ys-font-size-xl);
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.channel-view__subtitle {
  margin: 0;
  font-size: var(--ys-font-size-sm);
  color: var(--el-text-color-secondary);
}

.channel-view__table-wrapper {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.channel-view__pagination {
  display: flex;
  justify-content: flex-end;
  padding: var(--ys-spacing-3) 0;
}
</style>
