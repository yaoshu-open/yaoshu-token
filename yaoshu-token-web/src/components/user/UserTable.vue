<script setup lang="ts">
/**
 * 用户表格主体组件。
 * 紧凑模式（T-US-02）：通过 CSS 类切换行高与 padding。
 */
import { useI18n } from 'vue-i18n'
import { ElTable, ElTableColumn, ElTag, ElButton, ElDropdown, ElDropdownMenu, ElDropdownItem, ElIcon, ElProgress } from 'element-plus'
import { MoreFilled } from '@element-plus/icons-vue'
import { USER_STATUS_CONFIG, USER_ROLE_CONFIG } from '@/api/user/constants'
import { formatQuotaWithCurrency } from '@/utils/currency'
import type { User } from '@/api/user/types'

const { t } = useI18n()

defineProps<{
  users: User[]
  loading?: boolean
  isCompact?: boolean
  selectedIds?: number[]
}>()

const emit = defineEmits<{
  (e: 'selection-change', ids: number[]): void
  (e: 'row-action', action: string, user: User): void
}>()

function formatTime(timestamp?: number): string {
  if (!timestamp || timestamp === 0) return '-'
  return new Date(timestamp * 1000).toLocaleDateString()
}

function getRowClassName({ row }: { row: User }): string {
  if (row.status === 2) return 'users-table__row--disabled'
  if (row.status === 3) return 'users-table__row--deleted'
  return ''
}
</script>

<template>
  <div
    class="users-table"
    :class="{ 'users-table--compact': isCompact }"
  >
    <el-table
      :data="users"
      :loading="loading"
      row-key="id"
      stripe
      :row-class-name="getRowClassName"
      @selection-change="emit('selection-change', $event.map((u: User) => u.id))"
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
        :label="t('user.table.username')"
        min-width="140"
        fixed="left"
      >
        <template #default="{ row }">
          <div>
            <span class="users-table__username">{{ row.username }}</span>
            <span
              v-if="row.remark"
              class="users-table__remark"
            >({{ row.remark }})</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column
        prop="displayName"
        :label="t('user.table.displayName')"
        min-width="120"
      />
      <el-table-column
        :label="t('user.table.role')"
        width="100"
      >
        <template #default="{ row }">
          <el-tag
            :type="USER_ROLE_CONFIG[row.role as 1 | 2 | 3]?.type || 'info'"
            size="small"
          >
            {{ t(USER_ROLE_CONFIG[row.role as 1 | 2 | 3]?.label || 'common.unknown') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('common.status')"
        width="100"
      >
        <template #default="{ row }">
          <el-tag
            :type="USER_STATUS_CONFIG[row.status as 1 | 2 | 3]?.type || 'info'"
            size="small"
          >
            {{ t(USER_STATUS_CONFIG[row.status as 1 | 2 | 3]?.label || 'common.unknown') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('user.table.quota')"
        width="140"
      >
        <template #default="{ row }">
          <div class="users-table__quota">
            <span>{{ formatQuotaWithCurrency(row.quota) }}</span>
            <el-progress
              v-if="row.quota > 0"
              :percentage="Math.min(100, Math.round((row.usedQuota / row.quota) * 100))"
              :stroke-width="4"
              :show-text="false"
              style="width: 60px"
            />
          </div>
        </template>
      </el-table-column>
      <el-table-column
        prop="group"
        :label="t('user.table.group')"
        width="100"
      />
      <el-table-column
        prop="requestCount"
        :label="t('user.table.requests')"
        width="100"
      />
      <el-table-column
        :label="t('user.table.created')"
        width="120"
      >
        <template #default="{ row }">
          <span class="users-table__time">{{ formatTime(row.createdAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('user.table.lastSeen')"
        width="120"
      >
        <template #default="{ row }">
          <span class="users-table__time">{{ formatTime(row.lastLoginAt) }}</span>
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
            @command="(cmd: string) => emit('row-action', cmd, row as User)"
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
                <el-dropdown-item command="edit">
                  {{ t('common.edit') }}
                </el-dropdown-item>
                <el-dropdown-item command="quota">
                  {{ t('user.balance.quota') }}
                </el-dropdown-item>
                <el-dropdown-item
                  v-if="row.role < 2"
                  command="promote"
                >
                  {{ t('user.balance.promote') }}
                </el-dropdown-item>
                <el-dropdown-item
                  v-if="row.role >= 2"
                  command="demote"
                >
                  {{ t('user.balance.demote') }}
                </el-dropdown-item>
                <el-dropdown-item
                  v-if="row.status !== 1"
                  command="enable"
                >
                  {{ t('common.enable') }}
                </el-dropdown-item>
                <el-dropdown-item
                  v-if="row.status === 1"
                  command="disable"
                >
                  {{ t('common.disable') }}
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
.users-table { height: 100%; }
.users-table--compact :deep(.el-table__row) { height: 36px; }
.users-table--compact :deep(.el-table__cell) { padding: var(--ys-spacing-1) var(--ys-spacing-2); }
.users-table__username { font-weight: 500; }
.users-table__remark { margin-left: 4px; font-size: var(--ys-font-size-xs); color: var(--el-text-color-secondary); }
.users-table__quota { display: flex; gap: var(--ys-spacing-2); align-items: center; font-family: monospace; font-size: var(--ys-font-size-xs); }
.users-table__time { font-size: var(--ys-font-size-xs); color: var(--el-text-color-secondary); }
:deep(.users-table__row--disabled) { opacity: 0.6; }
:deep(.users-table__row--deleted) { text-decoration: line-through; opacity: 0.4; }
</style>
