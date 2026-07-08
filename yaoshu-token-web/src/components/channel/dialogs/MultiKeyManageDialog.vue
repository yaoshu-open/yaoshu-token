<script setup lang="ts">
/**
 * 多密钥管理对话框：统计卡片 + 分页表格 + 状态筛选 + 操作确认流。
 *
 * 消费 useMultiKeyManage composable，复杂状态（分页/统计/操作）由 composable 管理。
 */
import { computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { MULTI_KEY_FILTER_OPTIONS } from '@/api/channel/constants'
import type { Channel } from '@/api/channel/types'
import { useMultiKeyManage } from '@/composables/channel/useMultiKeyManage'
import {
  getMultiKeyConfirmMessage,
  getMultiKeyStatusConfig,
  isDestructiveAction
} from '@/lib/channel/multi-key-utils'
import StatusBadge from '@/components/StatusBadge.vue'
import { formatTimestamp } from '@/lib/channel/channel-utils'

const props = defineProps<{
  modelValue: boolean
  channel: Channel | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'success'): void
}>()

const { t } = useI18n()

const {
  keys,
  loading,
  currentPage,
  pageSize,
  total,
  totalPages,
  enabledCount,
  manualDisabledCount,
  autoDisabledCount,
  statusFilter,
  performingAction,
  confirmAction,
  loadKeys,
  handleStatusFilterChange,
  handlePageChange,
  performAction
} = useMultiKeyManage()

// 对话框打开时重置状态筛选并加载第 1 页
watch(
  () => props.modelValue,
  (open) => {
    if (open && props.channel?.id) {
      // handleStatusFilterChange 内部设置 statusFilter=null 并 loadKeys(page=1)
      void handleStatusFilterChange(props.channel.id, null)
    }
  }
)

const channelId = computed(() => props.channel?.id ?? 0)

// ElTable 需要可变数组，从 readonly keys 解包
const tableData = computed(() => [...keys.value])

function onStatusFilterChange(value: string): void {
  const newFilter = value === 'all' ? null : parseInt(value)
  if (channelId.value) {
    void handleStatusFilterChange(channelId.value, newFilter)
  }
}

function onPageChange(page: number): void {
  if (channelId.value) {
    void handlePageChange(channelId.value, page)
  }
}

function onRefresh(): void {
  if (channelId.value) {
    void loadKeys(channelId.value)
  }
}

function setAction(type: 'enable' | 'disable' | 'delete', keyIndex: number): void
function setAction(type: 'enable-all' | 'disable-all' | 'delete-disabled'): void
function setAction(type: string, keyIndex?: number): void {
  if (type === 'enable-all') {
    confirmAction.value = { type: 'enable-all' }
  } else if (type === 'disable-all') {
    confirmAction.value = { type: 'disable-all' }
  } else if (type === 'delete-disabled') {
    confirmAction.value = { type: 'delete-disabled' }
  } else if (keyIndex !== undefined) {
    if (type === 'enable' || type === 'disable' || type === 'delete') {
      confirmAction.value = { type, keyIndex }
    }
  }
}

async function handleConfirm(): Promise<void> {
  if (!channelId.value) return
  const ok = await performAction(channelId.value)
  if (ok) {
    emit('success')
  }
}

function formatTime(timestamp?: number): string {
  if (!timestamp) return '-'
  return formatTimestamp(timestamp)
}

const statusFilterValue = computed(() =>
  statusFilter.value === null ? 'all' : String(statusFilter.value)
)

const showEnableAll = computed(
  () => manualDisabledCount.value + autoDisabledCount.value > 0
)
const showDisableAll = computed(() => enabledCount.value > 0)
const showDeleteDisabled = computed(() => autoDisabledCount.value > 0)
</script>

<template>
  <div>
    <ElDialog
      :model-value="modelValue"
      :title="t('channel.multiKey.title')"
      width="900px"
      :close-on-click-modal="false"
      append-to-body
      @update:model-value="emit('update:modelValue', $event)"
    >
      <template #header>
        <div class="multi-key__header">
          <h3 class="multi-key__title">
            {{ t('channel.multiKey.title') }}
          </h3>
          <StatusBadge
            v-if="channel"
            :label="channel.name"
            variant="neutral"
          />
          <StatusBadge
            v-if="channel?.channelInfo?.multiKeyMode"
            :label="channel.channelInfo.multiKeyMode === 'random' ? t('channel.multiKey.modeRandom') : t('channel.multiKey.modePolling')"
            variant="neutral"
          />
        </div>
      </template>

      <div class="multi-key">
        <!-- 统计卡片 -->
        <div class="multi-key__stats">
          <div class="multi-key__stat-card">
            <div class="multi-key__stat-label">
              {{ t('channel.multiKey.statusEnabled') }}
            </div>
            <div class="multi-key__stat-value">
              <span class="multi-key__stat-count">{{ enabledCount }}</span>
              <span class="multi-key__stat-total">/ {{ total }}</span>
            </div>
          </div>
          <div class="multi-key__stat-card">
            <div class="multi-key__stat-label">
              {{ t('channel.multiKey.statusManualDisabled') }}
            </div>
            <div class="multi-key__stat-value">
              <span class="multi-key__stat-count">{{ manualDisabledCount }}</span>
              <span class="multi-key__stat-total">/ {{ total }}</span>
            </div>
          </div>
          <div class="multi-key__stat-card">
            <div class="multi-key__stat-label">
              {{ t('channel.multiKey.statusAutoDisabled') }}
            </div>
            <div class="multi-key__stat-value">
              <span class="multi-key__stat-count">{{ autoDisabledCount }}</span>
              <span class="multi-key__stat-total">/ {{ total }}</span>
            </div>
          </div>
        </div>

        <ElDivider />

        <!-- 工具栏 -->
        <div class="multi-key__toolbar">
          <ElSelect
            :model-value="statusFilterValue"
            class="multi-key__filter"
            @change="onStatusFilterChange"
          >
            <ElOption
              v-for="opt in MULTI_KEY_FILTER_OPTIONS"
              :key="opt.value"
              :value="opt.value"
              :label="t(opt.label)"
            />
          </ElSelect>

          <div class="multi-key__actions">
            <ElButton
              :icon="'Refresh'"
              :loading="loading"
              circle
              size="small"
              @click="onRefresh"
            />
            <ElButton
              v-if="showEnableAll"
              type="primary"
              size="small"
              @click="setAction('enable-all')"
            >
              <i class="i-ep-turn-off mr-1" />
              {{ t('channel.multiKey.enableAll') }}
            </ElButton>
            <ElButton
              v-if="showDisableAll"
              type="danger"
              size="small"
              @click="setAction('disable-all')"
            >
              <i class="i-ep-open mr-1" />
              {{ t('channel.multiKey.disableAll') }}
            </ElButton>
            <ElButton
              v-if="showDeleteDisabled"
              type="danger"
              size="small"
              @click="setAction('delete-disabled')"
            >
              <i class="i-ep-delete mr-1" />
              {{ t('channel.multiKey.deleteAutoDisabled') }}
            </ElButton>
          </div>
        </div>

        <!-- 表格 -->
        <ElTable
          v-loading="loading"
          :data="tableData"
          border
          stripe
          style="width: 100%"
        >
          <ElTableColumn
            :label="t('channel.multiKey.index')"
            width="80"
          >
            <template #default="{ row }">
              <span class="mono">#{{ row.index + 1 }}</span>
            </template>
          </ElTableColumn>

          <ElTableColumn
            :label="t('channel.multiKey.status')"
            width="120"
          >
            <template #default="{ row }">
              <StatusBadge
                :label="t(getMultiKeyStatusConfig(row.status).label)"
                :variant="getMultiKeyStatusConfig(row.status).variant"
              />
            </template>
          </ElTableColumn>

          <ElTableColumn
            :label="t('channel.multiKey.disabledReason')"
            min-width="200"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              {{ row.reason || '-' }}
            </template>
          </ElTableColumn>

          <ElTableColumn
            :label="t('channel.multiKey.disabledTime')"
            width="180"
          >
            <template #default="{ row }">
              {{ formatTime(row.disabledTime) }}
            </template>
          </ElTableColumn>

          <ElTableColumn
            :label="t('common.action')"
            width="180"
            align="right"
          >
            <template #default="{ row }">
              <div class="multi-key__row-actions">
                <ElButton
                  v-if="row.status === 1"
                  size="small"
                  @click="setAction('disable', row.index)"
                >
                  {{ t('common.disable') }}
                </ElButton>
                <ElButton
                  v-else
                  size="small"
                  @click="setAction('enable', row.index)"
                >
                  {{ t('common.enable') }}
                </ElButton>
                <ElButton
                  type="danger"
                  size="small"
                  @click="setAction('delete', row.index)"
                >
                  {{ t('common.delete') }}
                </ElButton>
              </div>
            </template>
          </ElTableColumn>

          <template #empty>
            <span>{{ t('channel.multiKey.noKeys') }}</span>
          </template>
        </ElTable>

        <!-- 分页 -->
        <div
          v-if="totalPages > 1"
          class="multi-key__pagination"
        >
          <span class="multi-key__page-info">
            {{ t('channel.multiKey.pageOf', { current: currentPage, total: totalPages }) }}
          </span>
          <ElPagination
            :current-page="currentPage"
            :page-size="pageSize"
            :total="total"
            layout="prev, pager, next"
            small
            @current-change="onPageChange"
          />
        </div>
      </div>
    </ElDialog>

    <!-- 操作确认对话框 -->
    <ElDialog
      :model-value="confirmAction !== null"
      :title="t('channel.multiKey.confirmTitle')"
      width="420px"
      append-to-body
      @update:model-value="(v: boolean) => !v && (confirmAction = null)"
    >
      <p>{{ t(getMultiKeyConfirmMessage(confirmAction)) }}</p>
      <template #footer>
        <ElButton
          :disabled="performingAction"
          @click="confirmAction = null"
        >
          {{ t('common.cancel') }}
        </ElButton>
        <ElButton
          :type="isDestructiveAction(confirmAction) ? 'danger' : 'primary'"
          :loading="performingAction"
          @click="handleConfirm"
        >
          {{ t('common.confirm') }}
        </ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped lang="scss">
.multi-key {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-3);

  &__header {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
  }

  &__title {
    margin: 0;
    font-size: var(--ys-font-size-lg);
    font-weight: 600;
  }

  &__stats {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: var(--ys-spacing-2);
  }

  &__stat-card {
    padding: 10px;
    border: 1px solid var(--el-border-color);
    border-radius: var(--ys-radius-sm);
  }

  &__stat-label {
    margin-bottom: var(--ys-spacing-1);
    font-size: var(--ys-font-size-sm);
    font-weight: 500;
    color: var(--el-text-color-secondary);
  }

  &__stat-value {
    display: flex;
    gap: var(--ys-spacing-1);
    align-items: baseline;
  }

  &__stat-count {
    font-size: 22px;
    font-weight: 600;
  }

  &__stat-total {
    font-size: var(--ys-font-size-base);
    color: var(--el-text-color-secondary);
  }

  &__toolbar {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
    justify-content: space-between;
  }

  &__filter {
    width: 160px;
  }

  &__actions {
    display: flex;
    gap: 6px;
    align-items: center;
  }

  &__row-actions {
    display: flex;
    gap: 6px;
    justify-content: flex-end;
  }

  &__pagination {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__page-info {
    font-size: var(--ys-font-size-sm);
    color: var(--el-text-color-secondary);
  }
}

.mono {
  font-family: var(--el-font-family-mono, monospace);
  font-size: var(--ys-font-size-sm);
}
</style>
