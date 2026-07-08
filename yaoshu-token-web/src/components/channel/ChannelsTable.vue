<script setup lang="ts">
/**
 * 渠道表格主体组件。
 *
 * 职责：渲染渠道列表表格，支持选择/排序/紧凑模式。
 * 不负责：数据加载（useChannelsData）/ 工具栏（ChannelsToolbar）/ 操作确认。
 *
 * 紧凑模式（T-CH-01）：通过 CSS 类切换行高与 padding，不修改通用 DataTable 组件。
 */
import { computed, ref } from 'vue'
import { ElTableColumn } from 'element-plus'
import type { TableInstance } from 'element-plus'
import { MoreFilled } from '@element-plus/icons-vue'
import {
  CHANNEL_COLUMNS,
  type ChannelColumnConfig,
  type ChannelColumnKey
} from './ChannelsColumns'
import {
  formatBalance,
  formatResponseTime,
  formatTimestamp,
  getBalanceVariant,
  getChannelTypeName,
  getGroupList,
  getResponseTimeConfig,
  getStatusConfig,
  isMultiKeyChannel,
  truncateModels,
  variantToTagType
} from '@/lib/channel/channel-utils'
import type { Channel, ChannelSortBy, ChannelSortOrder } from '@/api/channel/types'

const props = withDefaults(
  defineProps<{
    channels: Channel[]
    loading?: boolean
    isCompact?: boolean
    selectedIds?: number[]
    sortBy?: ChannelSortBy
    sortOrder?: ChannelSortOrder
    visibleColumns?: ChannelColumnKey[]
  }>(),
  {
    loading: false,
    isCompact: false,
    selectedIds: () => [],
    sortBy: 'id',
    sortOrder: 'desc',
    visibleColumns: () =>
      CHANNEL_COLUMNS.filter((c) => c.defaultVisible).map((c) => c.key)
  }
)

const emit = defineEmits<{
  (e: 'selection-change', ids: number[]): void
  (e: 'sort-change', payload: { prop: string; order: 'ascending' | 'descending' | null }): void
  (e: 'row-action', action: string, channel: Channel): void
  (e: 'tag-action', action: string, tag: string): void
}>()

const tableRef = ref<TableInstance | null>(null)

// ============================================================================
// 列计算
// ============================================================================

const tableColumns = computed<ChannelColumnConfig[]>(() => {
  const visibleSet = new Set(props.visibleColumns)
  let cols = CHANNEL_COLUMNS.filter((c) => visibleSet.has(c.key))

  if (props.isCompact) {
    cols = cols.filter((c) => !c.hideInCompact)
  }

  return cols
})

// ============================================================================
// 选择
// ============================================================================

function handleSelectionChange(rows: Channel[]): void {
  emit('selection-change', rows.map((r) => r.id))
}

// ============================================================================
// 排序
// ============================================================================

function handleSortChange({
  prop,
  order
}: {
  prop: string | null
  order: 'ascending' | 'descending' | null
}): void {
  emit('sort-change', { prop: prop ?? '', order })
}

// ============================================================================
// 行操作
// ============================================================================

function handleRowAction(action: string, channel: Channel): void {
  emit('row-action', action, channel)
}

function handleTagAction(action: string, tag: string): void {
  emit('tag-action', action, tag)
}

defineExpose({
  tableRef
})
</script>

<template>
  <div
    class="channels-table"
    :class="{ 'channels-table--compact': isCompact }"
  >
    <ElTable
      ref="tableRef"
      v-loading="loading"
      :data="channels"
      :row-key="(row: Channel) => String(row.id)"
      :border="true"
      :stripe="!isCompact"
      size="default"
      height="100%"
      @selection-change="handleSelectionChange"
      @sort-change="handleSortChange"
    >
      <template
        v-for="col in tableColumns"
        :key="col.key"
      >
        <!-- 选择列 -->
        <ElTableColumn
          v-if="col.key === 'selection'"
          type="selection"
          :width="col.width"
          :fixed="col.fixed"
          :reserve-selection="true"
        />

        <!-- ID 列 -->
        <ElTableColumn
          v-else-if="col.key === 'id'"
          :prop="col.key"
          :label="$t(`channel.columns.${col.labelKey}`)"
          :width="col.width"
          :sortable="col.sortable ? 'custom' : false"
          :sort-orders="['ascending', 'descending']"
          :fixed="col.fixed"
        >
          <template #default="{ row }: { row: any }">
            <span class="channel-id">#{{ row.id }}</span>
          </template>
        </ElTableColumn>

        <!-- 名称列 -->
        <ElTableColumn
          v-else-if="col.key === 'name'"
          :prop="col.key"
          :label="$t(`channel.columns.${col.labelKey}`)"
          :min-width="col.minWidth"
          :sortable="col.sortable ? 'custom' : false"
          :fixed="col.fixed"
        >
          <template #default="{ row }: { row: any }">
            <div class="channel-name-cell">
              <span class="channel-name">{{ row.name }}</span>
              <el-tag
                v-if="isMultiKeyChannel(row)"
                size="small"
                type="info"
                effect="plain"
              >
                {{ $t('channel.multiKey.label') }} ({{ row.channelInfo?.multiKeySize }})
              </el-tag>
            </div>
          </template>
        </ElTableColumn>

        <!-- 类型列 -->
        <ElTableColumn
          v-else-if="col.key === 'type'"
          :prop="col.key"
          :label="$t(`channel.columns.${col.labelKey}`)"
          :width="col.width"
        >
          <template #default="{ row }: { row: any }">
            <span class="channel-type">{{ getChannelTypeName(row.type) }}</span>
          </template>
        </ElTableColumn>

        <!-- 状态列 -->
        <ElTableColumn
          v-else-if="col.key === 'status'"
          :prop="col.key"
          :label="$t(`channel.columns.${col.labelKey}`)"
          :width="col.width"
          :sortable="col.sortable ? 'custom' : false"
        >
          <template #default="{ row }: { row: any }">
            <el-tag
              :type="variantToTagType(getStatusConfig(row.status).variant)"
              size="small"
              effect="light"
            >
              {{ $t(getStatusConfig(row.status).label) }}
            </el-tag>
          </template>
        </ElTableColumn>

        <!-- 分组列 -->
        <ElTableColumn
          v-else-if="col.key === 'group'"
          :prop="col.key"
          :label="$t(`channel.columns.${col.labelKey}`)"
          :width="col.width"
        >
          <template #default="{ row }: { row: any }">
            <div class="channel-groups">
              <el-tag
                v-for="g in getGroupList(row)"
                :key="g"
                size="small"
                type="info"
                effect="plain"
              >
                {{ g }}
              </el-tag>
              <span v-if="!getGroupList(row).length">-</span>
            </div>
          </template>
        </ElTableColumn>

        <!-- 标签列 -->
        <ElTableColumn
          v-else-if="col.key === 'tag'"
          :prop="col.key"
          :label="$t(`channel.columns.${col.labelKey}`)"
          :width="col.width"
        >
          <template #default="{ row }: { row: any }">
            <div
              v-if="row.tag"
              class="channel-tag-cell"
            >
              <el-tag
                size="small"
                type="warning"
                effect="plain"
              >
                {{ row.tag }}
              </el-tag>
              <el-dropdown
                trigger="click"
                @command="(cmd: string) => handleTagAction(cmd, row.tag)"
              >
                <el-button
                  link
                  type="primary"
                  size="small"
                  class="channel-tag-cell__menu"
                >
                  <el-icon><MoreFilled /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="editTag">
                      {{ $t('channel.tagRow.editTag') }}
                    </el-dropdown-item>
                    <el-dropdown-item command="batchEdit">
                      {{ $t('channel.tagRow.batchEdit') }}
                    </el-dropdown-item>
                    <el-dropdown-item
                      divided
                      command="enableAll"
                    >
                      {{ $t('channel.tagRow.enableAll') }}
                    </el-dropdown-item>
                    <el-dropdown-item command="disableAll">
                      {{ $t('channel.tagRow.disableAll') }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
            <span v-else>-</span>
          </template>
        </ElTableColumn>

        <!-- 模型列 -->
        <ElTableColumn
          v-else-if="col.key === 'models'"
          :prop="col.key"
          :label="$t(`channel.columns.${col.labelKey}`)"
          :min-width="col.minWidth"
        >
          <template #default="{ row }: { row: any }">
            <span
              class="channel-models"
              :title="row.models"
            >
              {{ truncateModels(row.models, isCompact ? 2 : 3) }}
            </span>
          </template>
        </ElTableColumn>

        <!-- 余额列 -->
        <ElTableColumn
          v-else-if="col.key === 'balance'"
          :prop="col.key"
          :label="$t(`channel.columns.${col.labelKey}`)"
          :width="col.width"
          :sortable="col.sortable ? 'custom' : false"
        >
          <template #default="{ row }: { row: any }">
            <span :class="['channel-balance', `variant-${getBalanceVariant(row.balance)}`]">
              {{ formatBalance(row.balance) }}
            </span>
          </template>
        </ElTableColumn>

        <!-- 响应时间列 -->
        <ElTableColumn
          v-else-if="col.key === 'response_time'"
          :prop="col.key"
          :label="$t(`channel.columns.${col.labelKey}`)"
          :width="col.width"
          :sortable="col.sortable ? 'custom' : false"
        >
          <template #default="{ row }: { row: any }">
            <span :class="['channel-response-time', `variant-${getResponseTimeConfig(row.responseTime).variant}`]">
              {{ formatResponseTime(row.responseTime) }}
            </span>
          </template>
        </ElTableColumn>

        <!-- 优先级列 -->
        <ElTableColumn
          v-else-if="col.key === 'priority'"
          :prop="col.key"
          :label="$t(`channel.columns.${col.labelKey}`)"
          :width="col.width"
          :sortable="col.sortable ? 'custom' : false"
        >
          <template #default="{ row }: { row: any }">
            <span class="channel-priority">{{ row.priority }}</span>
          </template>
        </ElTableColumn>

        <!-- 权重列 -->
        <ElTableColumn
          v-else-if="col.key === 'weight'"
          :prop="col.key"
          :label="$t(`channel.columns.${col.labelKey}`)"
          :width="col.width"
          :sortable="col.sortable ? 'custom' : false"
        >
          <template #default="{ row }: { row: any }">
            <span class="channel-weight">{{ row.weight }}</span>
          </template>
        </ElTableColumn>

        <!-- 已用配额列 -->
        <ElTableColumn
          v-else-if="col.key === 'used_quota'"
          :prop="col.key"
          :label="$t(`channel.columns.${col.labelKey}`)"
          :width="col.width"
          :sortable="col.sortable ? 'custom' : false"
        >
          <template #default="{ row }: { row: any }">
            <span class="channel-used-quota">{{ Number(row.usedQuota ?? 0).toLocaleString() }}</span>
          </template>
        </ElTableColumn>

        <!-- 测试时间列 -->
        <ElTableColumn
          v-else-if="col.key === 'test_time'"
          :prop="col.key"
          :label="$t(`channel.columns.${col.labelKey}`)"
          :width="col.width"
          :sortable="col.sortable ? 'custom' : false"
        >
          <template #default="{ row }: { row: any }">
            <span class="channel-test-time">{{ formatTimestamp(row.testTime) }}</span>
          </template>
        </ElTableColumn>

        <!-- 操作列 -->
        <ElTableColumn
          v-else-if="col.key === 'actions'"
          :label="$t(`channel.columns.${col.labelKey}`)"
          :width="col.width"
          :fixed="col.fixed"
        >
          <template #default="{ row }: { row: any }">
            <div class="channel-actions">
              <el-button
                link
                type="primary"
                size="small"
                @click="handleRowAction('test', row)"
              >
                {{ $t('channel.actions.test') }}
              </el-button>
              <el-button
                link
                type="primary"
                size="small"
                @click="handleRowAction('edit', row)"
              >
                {{ $t('channel.actions.edit') }}
              </el-button>
              <el-dropdown
                trigger="click"
                @command="(cmd: string) => handleRowAction(cmd, row)"
              >
                <el-button
                  link
                  type="primary"
                  size="small"
                >
                  <el-icon><MoreFilled /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="copy">
                      {{ $t('channel.actions.copy') }}
                    </el-dropdown-item>
                    <el-dropdown-item command="balance">
                      {{ $t('channel.actions.updateBalance') }}
                    </el-dropdown-item>
                    <el-dropdown-item command="fetchModels">
                      {{ $t('channel.actions.fetchModels') }}
                    </el-dropdown-item>
                    <el-dropdown-item command="viewKey">
                      {{ $t('channel.actions.viewKey') }}
                    </el-dropdown-item>
                    <el-dropdown-item
                      divided
                      command="delete"
                    >
                      {{ $t('channel.actions.delete') }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </ElTableColumn>
      </template>

      <!-- 空数据 -->
      <template #empty>
        <div class="channels-empty">
          {{ $t('channel.empty') }}
        </div>
      </template>
    </ElTable>
  </div>
</template>

<style scoped>
.channels-table {
  width: 100%;
  height: 100%;
}

/* 紧凑模式：减小行高与 padding */
.channels-table--compact :deep(.el-table) {
  --el-table-row-height: 36px;
}

.channels-table--compact :deep(.el-table .cell) {
  padding: 0 var(--ys-spacing-2);
}

.channels-table--compact :deep(.el-table th.el-table__cell) {
  padding: var(--ys-spacing-1) 0;
}

.channels-table--compact :deep(.el-table td.el-table__cell) {
  padding: 2px 0;
}

/* 单元格内容样式 */
.channel-id {
  font-family: var(--el-font-family-mono, monospace);
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
}

.channel-name-cell {
  display: flex;
  gap: 6px;
  align-items: center;
}

.channel-name {
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.channel-tag-cell {
  display: flex;
  gap: var(--ys-spacing-1);
  align-items: center;
}

.channel-tag-cell__menu {
  padding: 0;
}

.channel-type {
  font-size: var(--ys-font-size-sm);
  color: var(--el-text-color-regular);
}

.channel-groups {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ys-spacing-1);
}

.channel-models {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

/* 余额颜色变体 */
.channel-balance.variant-success {
  color: var(--el-color-success);
}

.channel-balance.variant-warning {
  color: var(--el-color-warning);
}

.channel-balance.variant-danger {
  color: var(--el-color-danger);
}

.channel-balance.variant-neutral {
  color: var(--el-text-color-secondary);
}

/* 响应时间等级颜色 */
.channel-response-time.variant-success {
  color: var(--el-color-success);
}

.channel-response-time.variant-info {
  color: var(--el-color-primary);
}

.channel-response-time.variant-warning {
  color: var(--el-color-warning);
}

.channel-response-time.variant-danger {
  color: var(--el-color-danger);
}

.channel-response-time.variant-neutral {
  color: var(--el-text-color-secondary);
}

.channel-actions {
  display: flex;
  gap: var(--ys-spacing-1);
  align-items: center;
}

.channels-empty {
  padding: var(--ys-spacing-8) 0;
  color: var(--el-text-color-secondary);
}
</style>
