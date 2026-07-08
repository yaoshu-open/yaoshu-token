<script setup lang="ts">
/**
 * 令牌管理页。
 *
 * 全量迁移：表格 + 紧凑模式(T-TK-01) + 编辑抽屉 + 批量复制格式(T-TK-02) + CCSwitch。
 */
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElButton, ElDropdown, ElDropdownMenu, ElDropdownItem } from 'element-plus'
import TokenToolbar from '@/components/token/TokenToolbar.vue'
import TokenTable from '@/components/token/TokenTable.vue'
import TokenMutateDrawer from '@/components/token/TokenMutateDrawer.vue'
import CCSwitchDialog from '@/components/token/dialogs/CCSwitchDialog.vue'
import { useTokensData } from '@/composables/token/useTokensData'
import { useTokenActions } from '@/composables/token/useTokenActions'
import { useMobile } from '@/composables/useMobile'
import { resolveComponent } from '@/plugins/spi/registry'
import type { Token } from '@/api/token/types'

const { t } = useI18n()
const isMobile = useMobile()

// SPI 扩展点：商业版可注入令牌概览统计卡片
const tokenOverview = resolveComponent('token-overview')

const {
  tokens, loading, error, filters, pagination, selectedIds, isCompact,
  fetchTokens, handleSearch, handleResetFilters,
  handlePageChange, handlePageSizeChange, handleSelectionChange, clearSelection,
} = useTokensData()

const { deleteTokenById, batchDelete, toggleTokenStatus } = useTokenActions(fetchTokens)

// 编辑抽屉
const editDrawerOpen = ref(false)
const editingId = ref<number | null>(null)

// CCSwitch 对话框
const ccSwitchOpen = ref(false)
const ccSwitchToken = ref<Token | null>(null)

// 选中令牌列表（用于批量复制 T-TK-02）
const selectedTokens = computed(() =>
  tokens.value.filter((tk) => selectedIds.value.includes(tk.id))
)

// ============================================================================
// 行操作
// ============================================================================

function handleRowAction(action: string, token: Token): void {
  switch (action) {
    case 'edit':
      editingId.value = token.id
      editDrawerOpen.value = true
      break
    case 'enable':
    case 'disable':
      toggleTokenStatus(token)
      break
    case 'copyKey':
      copyToClipboard(token.key, t('common.copySuccess'))
      break
    case 'ccSwitch':
      ccSwitchToken.value = token
      ccSwitchOpen.value = true
      break
    case 'delete':
      deleteTokenById(token.id, token.name)
      break
  }
}

function copyToClipboard(text: string, successMsg: string): void {
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success(successMsg)
  }).catch(() => {
    ElMessage.error(t('common.copyFailed'))
  })
}

// ============================================================================
// 顶部操作
// ============================================================================

function handleAdd(): void {
  editingId.value = null
  editDrawerOpen.value = true
}

// ============================================================================
// 批量操作（含 T-TK-02 批量复制格式选项）
// ============================================================================

async function handleBatchDelete(): Promise<void> {
  if (!selectedIds.value.length) return
  await batchDelete(selectedIds.value)
  clearSelection()
}

// T-TK-02 批量复制格式选项
async function handleBatchCopy(format: 'name_key' | 'key_only'): Promise<void> {
  if (!selectedTokens.value.length) return
  const text = selectedTokens.value
    .map((tk) => format === 'name_key' ? `${tk.name}\t${tk.key}` : tk.key)
    .join('\n')
  copyToClipboard(text, `${selectedTokens.value.length} tokens copied`)
  clearSelection()
}

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100]
</script>

<template>
  <div class="token-view">
    <div class="token-view__header">
      <div>
        <h2 class="token-view__title">
          <i class="i-ep-key" />
          {{ t('nav.apiKeys') }}
        </h2>
        <p class="token-view__subtitle">
          {{ t('token.list.subtitle') }}
        </p>
      </div>
      <div class="token-view__actions">
        <el-button
          type="primary"
          size="small"
          @click="handleAdd"
        >
          {{ t('token.actions.add') }}
        </el-button>
      </div>
    </div>

    <!-- SPI 扩展点：商业版统计卡片（未注册时不渲染） -->
    <component
      :is="tokenOverview"
      v-if="tokenOverview"
      :tokens="tokens"
      :pagination="pagination"
    />

    <TokenToolbar
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
      class="token-view__error"
    />

    <!-- 批量操作栏（含 T-TK-02 批量复制格式选项） -->
    <div
      v-if="selectedIds.length > 0"
      class="token-view__bulk-actions"
    >
      <span class="token-view__bulk-count">{{ t('common.selected') }} {{ selectedIds.length }}</span>
      <el-button
        size="small"
        type="danger"
        @click="handleBatchDelete"
      >
        {{ t('common.delete') }}
      </el-button>
      <!-- T-TK-02 批量复制格式选项 -->
      <el-dropdown @command="(fmt: string) => handleBatchCopy(fmt as 'name_key' | 'key_only')">
        <el-button size="small">
          {{ t('common.copy') }} {{ t('nav.apiKeys') }}
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="name_key">
              Name + Key
            </el-dropdown-item>
            <el-dropdown-item command="key_only">
              Key Only
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-button
        size="small"
        text
        @click="clearSelection"
      >
        {{ t('token.batch.clear') }}
      </el-button>
    </div>

    <div class="token-view__table-wrapper">
      <TokenTable
        :tokens="tokens"
        :loading="loading"
        :is-compact="isCompact"
        :selected-ids="selectedIds"
        @selection-change="handleSelectionChange"
        @row-action="handleRowAction"
      />
    </div>

    <div class="token-view__pagination">
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

    <TokenMutateDrawer
      v-model:visible="editDrawerOpen"
      :editing-id="editingId"
      @success="fetchTokens"
    />

    <CCSwitchDialog
      v-model:visible="ccSwitchOpen"
      :token="ccSwitchToken"
    />
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens' as *;

.token-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: $spacing-4 $spacing-6;
}

.token-view__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: $spacing-2;
}

.token-view__title {
  display: flex;
  gap: $spacing-2;
  align-items: center;
  margin: 0 0 $spacing-1;
  font-size: $font-size-xl;
  font-weight: $font-weight-semibold;
  color: var(--el-text-color-primary);
}

.token-view__subtitle {
  margin: 0;
  font-size: $font-size-sm;
  color: var(--el-text-color-secondary);
}

.token-view__actions {
  display: flex;
  gap: $spacing-2;
}

.token-view__error {
  margin-bottom: $spacing-3;
}

.token-view__table-wrapper {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.token-view__pagination {
  display: flex;
  justify-content: flex-end;
  padding: $spacing-3 0;
}

.token-view__bulk-actions {
  display: flex;
  gap: $spacing-2;
  align-items: center;
  padding: $spacing-2 $spacing-3;
  margin-bottom: $spacing-2;
  background: var(--el-fill-color-light);
  border-radius: $radius-sm;
}

.token-view__bulk-count {
  margin-right: $spacing-1;
  font-size: $font-size-sm;
  color: var(--el-text-color-primary);
}
</style>
