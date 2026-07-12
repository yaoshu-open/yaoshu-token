<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElTable, ElTableColumn, ElTag } from 'element-plus'
import StatusBadge from '@/components/StatusBadge.vue'
import LongText from '@/components/LongText.vue'
import { formatQuotaBilling } from '@/utils/currency'
import { getLogTypeMapping, LOG_TYPE_ENUM, type BillingDisplayMode } from '@/api/usage-log/constants'
import type { UsageLog } from '@/api/usage-log/types'

type StatusVariant = 'success' | 'warning' | 'danger' | 'info' | 'neutral' | 'primary'

function logTypeVariant(type: number): StatusVariant {
  return getLogTypeMapping(type).variant as StatusVariant
}

interface CommonLogsTableProps {
  logs: UsageLog[]
  loading?: boolean
  isCompact?: boolean
  isAdmin?: boolean
  billingDisplayMode: BillingDisplayMode
  sensitiveVisible: boolean
}

const props = defineProps<CommonLogsTableProps>()
const emit = defineEmits<{ (e: 'view-details', log: UsageLog): void }>()
const { t } = useI18n()

function formatTime(ts: number): string {
  if (!ts) return '-'
  const d = new Date(ts * 1000)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function quotaText(log: UsageLog): string {
  if (!props.sensitiveVisible) return '••••'
  // 金额体系统一：固定走定价货币（formatQuotaBilling 强制货币符号，TOKENS 模式回落 USD）
  if (props.billingDisplayMode === 'usd') {
    return formatQuotaBilling(log.quota)
  }
  return new Intl.NumberFormat().format(log.quota)
}

function useTimeText(log: UsageLog): string {
  if (!log.useTime) return '-'
  return `${log.useTime}s`
}

function useTimeType(log: UsageLog): 'success' | 'warning' | 'danger' | 'info' {
  const tm = log.useTime
  if (!tm) return 'info'
  if (tm > 30) return 'danger'
  if (tm > 10) return 'warning'
  return 'success'
}

// 解析 other JSON 取首字耗时（frt，单位 ms）
function parseFrt(log: UsageLog): number | null {
  if (!log.other) return null
  try {
    const o = JSON.parse(log.other) as { frt?: number | null }
    const frt = Number(o.frt)
    return Number.isFinite(frt) && frt > 0 ? frt : null
  } catch {
    return null
  }
}

// 首字耗时展示文本（流式请求才有 frt）
function frtText(log: UsageLog): string {
  const frt = parseFrt(log)
  if (frt == null) return ''
  return frt >= 1000 ? `首字 ${(frt / 1000).toFixed(1)}s` : `首字 ${frt}ms`
}

// 解析 other JSON 取缓存读 tokens（多源兼容：usage.promptTokensDetails / usage.promptCacheHitTokens / 顶层 cachedTokens）
function cachedTokens(log: UsageLog): number {
  if (!log.other) return 0
  try {
    const o = JSON.parse(log.other) as {
      cachedTokens?: number
      promptCacheHitTokens?: number
      usage?: {
        promptTokensDetails?: { cachedTokens?: number }
        promptTokenDetails?: { cachedTokens?: number }
        promptCacheHitTokens?: number
      }
    }
    const u = o.usage
    const v = u?.promptTokensDetails?.cachedTokens ?? u?.promptTokenDetails?.cachedTokens ?? u?.promptCacheHitTokens ?? o.cachedTokens ?? 0
    const n = Number(v)
    return Number.isFinite(n) && n > 0 ? n : 0
  } catch {
    return 0
  }
}

const tableSize = computed(() => (props.isCompact ? 'small' : 'default'))

function rowClassName({ row }: { row: UsageLog }): string {
  if (row.type === LOG_TYPE_ENUM.ERROR) return 'common-logs-table__row--error'
  if (row.type === LOG_TYPE_ENUM.REFUND) return 'common-logs-table__row--refund'
  return ''
}
</script>

<template>
  <div
    class="common-logs-table"
    :class="{ 'is-compact': isCompact }"
  >
    <ElTable
      :data="logs"
      :loading="loading"
      :size="tableSize"
      row-key="id"
      stripe
      :row-class-name="rowClassName"
      style="width: 100%"
    >
      <ElTableColumn
        :label="t('usageLogs.columns.time')"
        width="170"
      >
        <template #default="{ row }">
          <div class="common-logs-table__time">
            <span class="common-logs-table__mono">{{ formatTime(row.createdAt) }}</span>
            <StatusBadge
              :label="t(getLogTypeMapping(row.type).labelKey)"
              :variant="logTypeVariant(row.type)"
              size="sm"
            />
          </div>
        </template>
      </ElTableColumn>

      <ElTableColumn
        v-if="isAdmin"
        :label="t('usageLogs.columns.channel')"
        width="100"
      >
        <template #default="{ row }">
          <span
            v-if="row.channelId"
            class="common-logs-table__mono"
          >#{{ row.channelId }}</span>
          <span
            v-else
            class="common-logs-table__muted"
          >-</span>
        </template>
      </ElTableColumn>

      <ElTableColumn
        v-if="isAdmin"
        :label="t('usageLogs.columns.user')"
        width="120"
      >
        <template #default="{ row }">
          <span v-if="row.username">{{ row.username }}</span>
          <span
            v-else
            class="common-logs-table__muted"
          >-</span>
        </template>
      </ElTableColumn>

      <ElTableColumn
        :label="t('usageLogs.columns.token')"
        width="140"
      >
        <template #default="{ row }">
          <span v-if="row.tokenName">{{ row.tokenName }}</span>
          <span
            v-else
            class="common-logs-table__muted"
          >-</span>
        </template>
      </ElTableColumn>

      <ElTableColumn
        :label="t('usageLogs.columns.model')"
        min-width="140"
      >
        <template #default="{ row }">
          <span v-if="row.modelName">{{ row.modelName }}</span>
          <span
            v-else
            class="common-logs-table__muted"
          >-</span>
        </template>
      </ElTableColumn>

      <ElTableColumn
        :label="t('usageLogs.columns.timing')"
        width="110"
      >
        <template #default="{ row }">
          <div class="common-logs-table__timing">
            <ElTag
              :type="useTimeType(row as UsageLog)"
              size="small"
              effect="light"
            >
              {{ useTimeText(row as UsageLog) }}
            </ElTag>
            <span
              v-if="frtText(row as UsageLog)"
              class="common-logs-table__frt"
            >{{ frtText(row as UsageLog) }}</span>
          </div>
        </template>
      </ElTableColumn>

      <ElTableColumn
        :label="t('usageLogs.columns.promptTokens')"
        width="120"
      >
        <template #default="{ row }">
          <div class="common-logs-table__tokens">
            <span class="common-logs-table__mono">{{ row.promptTokens || 0 }}</span>
            <ElTag
              v-if="cachedTokens(row as UsageLog) > 0"
              size="small"
              type="success"
              effect="light"
              class="common-logs-table__cache-badge"
            >缓存 {{ new Intl.NumberFormat().format(cachedTokens(row as UsageLog)) }}</ElTag>
          </div>
        </template>
      </ElTableColumn>

      <ElTableColumn
        :label="t('usageLogs.columns.completionTokens')"
        width="120"
      >
        <template #default="{ row }">
          <span class="common-logs-table__mono">{{ row.completionTokens || 0 }}</span>
        </template>
      </ElTableColumn>

      <ElTableColumn
        :label="t('usageLogs.columns.quota')"
        width="120"
      >
        <template #default="{ row }">
          <span class="common-logs-table__mono">{{ quotaText(row as UsageLog) }}</span>
        </template>
      </ElTableColumn>

      <ElTableColumn
        :label="t('usageLogs.columns.requestId')"
        width="140"
      >
        <template #default="{ row }">
          <span
            v-if="row.requestId"
            class="common-logs-table__mono common-logs-table__ellipsis"
            :title="row.requestId"
          >{{ row.requestId }}</span>
          <span
            v-else
            class="common-logs-table__muted"
          >-</span>
        </template>
      </ElTableColumn>

      <ElTableColumn
        :label="t('usageLogs.columns.details')"
        min-width="200"
      >
        <template #default="{ row }">
          <div
            class="common-logs-table__details"
            @click="emit('view-details', row as UsageLog)"
          >
            <LongText
              v-if="row.content"
              :content="row.content"
              :max-length="60"
            />
            <span
              v-else
              class="common-logs-table__details-link"
            >
              {{ t('usageLogs.actions.viewDetails') }}
            </span>
          </div>
        </template>
      </ElTableColumn>
    </ElTable>
  </div>
</template>

<style scoped lang="scss">
.common-logs-table {
  &__time {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  &__mono {
    font-family: var(--el-font-family-mono, monospace);
    font-size: var(--ys-font-size-xs);
    font-variant-numeric: tabular-nums;
  }

  &__muted {
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-placeholder);
  }

  &__timing {
    display: flex;
    flex-direction: column;
    gap: 2px;
    align-items: flex-start;
  }

  &__frt {
    font-size: 11px;
    font-variant-numeric: tabular-nums;
    color: var(--el-text-color-secondary);
  }

  &__tokens {
    display: flex;
    flex-direction: column;
    gap: 2px;
    align-items: flex-start;
  }

  &__cache-badge {
    font-size: 11px;
    font-variant-numeric: tabular-nums;
  }

  &__ellipsis {
    display: inline-block;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    vertical-align: middle;
    white-space: nowrap;
  }

  &__details {
    color: var(--el-color-primary);
    cursor: pointer;
    transition: opacity 0.15s ease;

    &:hover {
      opacity: 0.75;
    }
  }

  &__details-link {
    font-size: var(--ys-font-size-xs);
    color: var(--el-color-primary);
    text-decoration: underline;
    text-underline-offset: 2px;
  }

  :deep(&__row--error) {
    background-color: var(--el-color-danger-light-9);
  }

  :deep(&__row--refund) {
    background-color: var(--el-color-info-light-9);
  }

  &.is-compact :deep(.el-table__row) {
    height: 36px;
  }

  &.is-compact :deep(.el-table__cell) {
    padding: 2px 0;
  }
}
</style>
