<script setup lang="ts">
/**
 * 兑换码表格主体组件。
 * 紧凑模式：通过 CSS 类切换行高与 padding。
 */
import { useI18n } from 'vue-i18n'
import {
  ElTable,
  ElTableColumn,
  ElTag,
  ElButton,
  ElDropdown,
  ElDropdownMenu,
  ElDropdownItem,
  ElIcon,
} from 'element-plus'
import { MoreFilled, CopyDocument } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { REDEMPTION_STATUS_CONFIG } from '@/api/redemption/constants'
import { formatQuotaWithCurrency } from '@/utils/currency'
import type { Redemption, RedemptionRowAction } from '@/api/redemption/types'

const { t } = useI18n()

defineProps<{
  redemptions: Redemption[]
  loading?: boolean
  isCompact?: boolean
  selectedIds?: number[]
}>()

const emit = defineEmits<{
  (e: 'selection-change', ids: number[]): void
  (e: 'row-action', action: RedemptionRowAction, row: Redemption): void
}>()

function formatTime(timestamp?: number): string {
  if (!timestamp || timestamp === 0) return t('redemption.column.permanent')
  return new Date(timestamp * 1000).toLocaleString()
}

function formatRedeemedTime(timestamp?: number): string {
  if (!timestamp || timestamp === 0) return '-'
  return new Date(timestamp * 1000).toLocaleString()
}

async function copyKey(key: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(key)
    ElMessage.success(t('redemption.actions.copySuccess'))
  } catch {
    ElMessage.error(t('redemption.actions.copyFailed'))
  }
}

function getRowClassName({ row }: { row: Redemption }): string {
  if (row.status === 2) return 'redemption-table__row--used'
  return ''
}
</script>

<template>
  <div
    class="redemption-table"
    :class="{ 'redemption-table--compact': isCompact }"
  >
    <el-table
      :data="redemptions"
      :loading="loading"
      row-key="id"
      stripe
      :row-class-name="getRowClassName"
      @selection-change="emit('selection-change', $event.map((r: Redemption) => r.id))"
    >
      <el-table-column
        type="selection"
        width="45"
        fixed="left"
      />
      <el-table-column
        prop="id"
        label="ID"
        width="70"
      />
      <el-table-column
        :label="t('redemption.column.key')"
        min-width="280"
        fixed="left"
      >
        <template #default="{ row }">
          <div class="redemption-table__key">
            <span
              class="redemption-table__key-text"
              :title="row.key"
            >{{ row.key }}</span>
            <el-button
              text
              size="small"
              circle
              :title="t('redemption.actions.copyKey')"
              @click="copyKey(row.key)"
            >
              <el-icon><CopyDocument /></el-icon>
            </el-button>
          </div>
        </template>
      </el-table-column>
      <el-table-column
        prop="name"
        :label="t('redemption.column.name')"
        min-width="140"
      />
      <el-table-column
        :label="t('redemption.column.status')"
        width="110"
      >
        <template #default="{ row }">
          <el-tag
            :type="REDEMPTION_STATUS_CONFIG[row.status as 1 | 2]?.variant || 'info'"
            size="small"
          >
            {{ t(REDEMPTION_STATUS_CONFIG[row.status as 1 | 2]?.i18nKey || 'common.unknown') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('redemption.column.quota')"
        width="120"
      >
        <template #default="{ row }">
          <span class="redemption-table__quota">{{ formatQuotaWithCurrency(row.quota) }}</span>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('redemption.column.expiredTime')"
        width="180"
      >
        <template #default="{ row }">
          <span class="redemption-table__time">{{ formatTime(row.expiredTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('redemption.column.redeemedTime')"
        width="180"
      >
        <template #default="{ row }">
          <span class="redemption-table__time">{{ formatRedeemedTime(row.redeemedTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('redemption.column.usedUserId')"
        width="120"
      >
        <template #default="{ row }">
          <span class="redemption-table__time">{{ row.usedUserId || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column
        label=""
        width="80"
        fixed="right"
      >
        <template #default="{ row }">
          <el-dropdown
            trigger="click"
            @command="(cmd: RedemptionRowAction) => emit('row-action', cmd, row as Redemption)"
          >
            <el-button
              text
              size="small"
              circle
            >
              <el-icon><MoreFilled /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  command="edit"
                  :disabled="row.status === 2"
                >
                  {{ t('redemption.actions.edit') }}
                </el-dropdown-item>
                <el-dropdown-item command="copyKey">
                  {{ t('redemption.actions.copyKey') }}
                </el-dropdown-item>
                <el-dropdown-item
                  command="delete"
                  divided
                >
                  {{ t('common.delete') }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.redemption-table {
  height: 100%;
}

.redemption-table--compact :deep(.el-table__row) {
  height: 36px;
}

.redemption-table--compact :deep(.el-table__cell) {
  padding: var(--ys-spacing-1) var(--ys-spacing-2);
}

.redemption-table__key {
  display: flex;
  gap: var(--ys-spacing-1);
  align-items: center;
}

.redemption-table__key-text {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-family: monospace;
  font-size: var(--ys-font-size-xs);
  white-space: nowrap;
}

.redemption-table__quota {
  font-family: monospace;
  font-size: var(--ys-font-size-xs);
}

.redemption-table__time {
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
}

:deep(.redemption-table__row--used) {
  opacity: 0.7;
}
</style>
