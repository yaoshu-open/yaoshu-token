<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElTable, ElTableColumn, ElProgress } from 'element-plus'
import StatusBadge from '@/components/StatusBadge.vue'
import LongText from '@/components/LongText.vue'
import { getTaskStatusMapping, getTaskActionMapping, getTaskPlatformMapping, TASK_ACTIONS, TASK_STATUS } from '@/api/task/types'
import type { TaskLog } from '@/api/task/types'

type StatusVariant = 'success' | 'warning' | 'danger' | 'info' | 'neutral' | 'primary'

function statusVariant(status: string): StatusVariant {
  return getTaskStatusMapping(status).variant as StatusVariant
}

interface TaskLogsTableProps {
  logs: TaskLog[]
  loading?: boolean
  isCompact?: boolean
  isAdmin?: boolean
}

const props = defineProps<TaskLogsTableProps>()
const emit = defineEmits<{
  (e: 'preview-audio', log: TaskLog): void
  (e: 'view-fail-reason', reason: string): void
}>()
const { t } = useI18n()

const tableSize = computed(() => (props.isCompact ? 'small' : 'default'))

function formatTime(ts?: number): string {
  if (!ts) return '-'
  const d = new Date(ts * 1000)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function duration(submit?: number, finish?: number): string {
  if (!submit || !finish) return '-'
  const sec = finish - submit
  if (sec < 60) return `${sec}s`
  return `${Math.floor(sec / 60)}m${sec % 60}s`
}

function parseProgress(progress?: string): number {
  if (!progress) return 0
  return parseInt(progress.replace('%', ''), 10) || 0
}

function isVideoTask(action: string): boolean {
  return [
    TASK_ACTIONS.GENERATE,
    TASK_ACTIONS.TEXT_GENERATE,
    TASK_ACTIONS.FIRST_TAIL_GENERATE,
    TASK_ACTIONS.REFERENCE_GENERATE,
    TASK_ACTIONS.REMIX_GENERATE,
  ].includes(action as never)
}

function isAudioTask(log: TaskLog): boolean {
  return log.platform === 'suno' && log.status === TASK_STATUS.SUCCESS && !!log.data
}

function isVideoUrl(log: TaskLog): boolean {
  return log.status === TASK_STATUS.SUCCESS && isVideoTask(log.action) && !!log.failReason?.startsWith('http')
}
</script>

<template>
  <div
    class="task-logs-table"
    :class="{ 'is-compact': isCompact }"
  >
    <ElTable
      :data="logs"
      :loading="loading"
      :size="tableSize"
      row-key="id"
      stripe
      style="width: 100%"
    >
      <ElTableColumn
        :label="t('usageLogs.columns.submitTime')"
        width="170"
      >
        <template #default="{ row }">
          <div class="task-logs-table__time">
            <span class="task-logs-table__mono">{{ formatTime(row.submitTime) }}</span>
            <span
              v-if="row.finishTime"
              class="task-logs-table__mono task-logs-table__muted"
            >
              {{ formatTime(row.finishTime) }}
            </span>
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
            class="task-logs-table__mono"
          >#{{ row.channelId }}</span>
          <span
            v-else
            class="task-logs-table__muted"
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
            class="task-logs-table__muted"
          >-</span>
        </template>
      </ElTableColumn>

      <ElTableColumn
        :label="t('usageLogs.columns.taskId')"
        min-width="160"
      >
        <template #default="{ row }">
          <div class="task-logs-table__task-id">
            <span class="task-logs-table__mono">{{ row.taskId || '-' }}</span>
            <span class="task-logs-table__meta">
              {{ t(getTaskPlatformMapping(row.platform).labelKey) }} · {{ t(getTaskActionMapping(row.action).labelKey) }}
            </span>
          </div>
        </template>
      </ElTableColumn>

      <ElTableColumn
        :label="t('usageLogs.columns.duration')"
        width="100"
      >
        <template #default="{ row }">
          <span class="task-logs-table__mono">{{ duration(row.submitTime, row.finishTime) }}</span>
        </template>
      </ElTableColumn>

      <ElTableColumn
        :label="t('usageLogs.columns.status')"
        width="120"
      >
        <template #default="{ row }">
          <StatusBadge
            :label="t(getTaskStatusMapping(row.status).labelKey)"
            :variant="statusVariant(row.status)"
            size="sm"
          />
        </template>
      </ElTableColumn>

      <ElTableColumn
        :label="t('usageLogs.columns.progress')"
        width="160"
      >
        <template #default="{ row }">
          <ElProgress
            :percentage="parseProgress(row.progress)"
            :status="row.status === 'FAILURE' ? 'exception' : undefined"
            :stroke-width="isCompact ? 10 : 14"
          />
        </template>
      </ElTableColumn>

      <ElTableColumn
        :label="t('usageLogs.columns.details')"
        min-width="200"
      >
        <template #default="{ row }">
          <ElButton
            v-if="isAudioTask(row as TaskLog)"
            size="small"
            text
            type="primary"
            @click="emit('preview-audio', row as TaskLog)"
          >
            {{ t('usageLogs.task.previewAudio') }}
          </ElButton>
          <a
            v-else-if="isVideoUrl(row as TaskLog)"
            :href="`/v1/videos/${row.taskId}/content`"
            target="_blank"
            rel="noopener noreferrer"
            class="task-logs-table__link"
          >
            {{ t('usageLogs.task.previewVideo') }}
          </a>
          <span
            v-else-if="row.failReason"
            class="task-logs-table__fail"
            @click="emit('view-fail-reason', row.failReason)"
          >
            <LongText
              :content="row.failReason"
              :max-length="60"
            />
          </span>
          <span
            v-else
            class="task-logs-table__muted"
          >-</span>
        </template>
      </ElTableColumn>
    </ElTable>
  </div>
</template>

<style scoped lang="scss">
.task-logs-table {
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
    font-size: 11px;
    color: var(--el-text-color-placeholder);
  }

  &__task-id {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  &__meta {
    font-size: 11px;
    color: var(--el-text-color-placeholder);
  }

  &__link {
    font-size: var(--ys-font-size-xs);
    color: var(--el-color-primary);
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }

  &__fail {
    color: var(--el-color-danger);
    cursor: pointer;
  }

  &.is-compact :deep(.el-table__row) {
    height: 36px;
  }

  &.is-compact :deep(.el-table__cell) {
    padding: 2px 0;
  }
}
</style>
