<script setup lang="ts">
/**
 * MJ 任务日志表格主体。
 *
 * 12 列：提交时间/花费时间/渠道[管理员]/类型/任务ID/提交结果[管理员]/任务状态/进度/结果图片/Prompt/PromptEn/失败原因。
 * 紧凑模式通过 CSS 类切换行高与 padding。
 */
import { ElButton, ElProgress, ElTable, ElTableColumn, ElTag } from 'element-plus'
import { useI18n } from 'vue-i18n'
import StatusBadge from '@/components/StatusBadge.vue'
import LongText from '@/components/LongText.vue'
import { useCopyToClipboard } from '@/composables/useCopyToClipboard'
import {
  getMjTaskTypeMapping,
  getMjStatusMapping,
  getMjSubmitResultMapping,
} from '@/api/midjourney/constants'
import type { MidjourneyLog } from '@/api/midjourney/types'

const props = defineProps<{
  logs: MidjourneyLog[]
  loading?: boolean
  isCompact?: boolean
  isAdmin?: boolean
}>()

const emit = defineEmits<{
  (e: 'view-image', url: string): void
  (e: 'view-prompt', content: string, title: string): void
}>()

const { t } = useI18n()
const { copy } = useCopyToClipboard()

/** 格式化时间戳（毫秒 → YYYY-MM-DD HH:mm:ss） */
function formatTime(ts?: number): string {
  if (!ts) return 'N/A'
  const d = new Date(ts)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/** 计算花费时间（秒） */
function calcDuration(submitTime?: number, finishTime?: number): { text: string; color: string } {
  if (!submitTime || !finishTime) return { text: 'N/A', color: '' }
  const sec = ((finishTime - submitTime) / 1000).toFixed(1)
  return {
    text: `${sec} ${t('midjourney.common.seconds')}`,
    color: Number(sec) > 60 ? 'danger' : 'success',
  }
}

/** 进度数值提取（"45%" → 45） */
function parseProgress(progress?: string): number {
  if (!progress) return 0
  return parseInt(progress.replace('%', ''), 10) || 0
}

async function copyText(text: string | number): Promise<void> {
  await copy(String(text))
}
</script>

<template>
  <div
    class="mj-table"
    :class="{ 'mj-table--compact': isCompact }"
  >
    <ElTable
      :data="logs"
      :loading="loading"
      row-key="id"
      stripe
      :size="isCompact ? 'small' : 'default'"
      style="width: 100%"
    >
      <!-- 提交时间 -->
      <ElTableColumn
        :label="t('midjourney.columns.submitTime')"
        width="170"
      >
        <template #default="{ row }">
          <span class="mj-table__mono">{{ formatTime(row.submitTime) }}</span>
        </template>
      </ElTableColumn>

      <!-- 花费时间 -->
      <ElTableColumn
        :label="t('midjourney.columns.duration')"
        width="110"
      >
        <template #default="{ row }">
          <ElTag
            v-if="calcDuration(row.submitTime, row.finishTime).color"
            :type="calcDuration(row.submitTime, row.finishTime).color as 'success' | 'danger'"
            size="small"
            effect="light"
          >
            {{ calcDuration(row.submitTime, row.finishTime).text }}
          </ElTag>
          <span
            v-else
            class="mj-table__muted"
          >{{ calcDuration(row.submitTime, row.finishTime).text }}</span>
        </template>
      </ElTableColumn>

      <!-- 渠道（管理员） -->
      <ElTableColumn
        v-if="isAdmin"
        :label="t('midjourney.columns.channel')"
        width="100"
      >
        <template #default="{ row }">
          <ElTag
            size="small"
            effect="plain"
            class="mj-table__channel-tag"
            @click="copyText(row.channelId)"
          >
            {{ row.channelId }}
          </ElTag>
        </template>
      </ElTableColumn>

      <!-- 类型 -->
      <ElTableColumn
        :label="t('midjourney.columns.type')"
        width="120"
      >
        <template #default="{ row }">
          <StatusBadge
            :label="t(`midjourney.taskType.${getMjTaskTypeMapping(row.action).labelKey}`)"
            :variant="getMjTaskTypeMapping(row.action).variant"
            size="sm"
          />
        </template>
      </ElTableColumn>

      <!-- 任务 ID -->
      <ElTableColumn
        prop="mjId"
        :label="t('midjourney.columns.taskId')"
        min-width="140"
      >
        <template #default="{ row }">
          <span class="mj-table__mono">{{ row.mjId || '-' }}</span>
        </template>
      </ElTableColumn>

      <!-- 提交结果（管理员） -->
      <ElTableColumn
        v-if="isAdmin"
        :label="t('midjourney.columns.submitResult')"
        width="110"
      >
        <template #default="{ row }">
          <StatusBadge
            :label="t(`midjourney.submitResult.${getMjSubmitResultMapping(row.code).labelKey}`)"
            :variant="getMjSubmitResultMapping(row.code).variant"
            size="sm"
          />
        </template>
      </ElTableColumn>

      <!-- 任务状态 -->
      <ElTableColumn
        :label="t('midjourney.columns.status')"
        width="110"
      >
        <template #default="{ row }">
          <StatusBadge
            :label="t(`midjourney.status.${getMjStatusMapping(row.status).labelKey}`)"
            :variant="getMjStatusMapping(row.status).variant"
            size="sm"
          />
        </template>
      </ElTableColumn>

      <!-- 进度 -->
      <ElTableColumn
        :label="t('midjourney.columns.progress')"
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

      <!-- 结果图片 -->
      <ElTableColumn
        :label="t('midjourney.columns.image')"
        width="100"
      >
        <template #default="{ row }">
          <ElButton
            v-if="row.imageUrl"
            size="small"
            text
            type="primary"
            @click="emit('view-image', row.imageUrl)"
          >
            {{ t('midjourney.image.view') }}
          </ElButton>
          <span
            v-else
            class="mj-table__muted"
          >{{ t('midjourney.common.none') }}</span>
        </template>
      </ElTableColumn>

      <!-- Prompt -->
      <ElTableColumn
        :label="t('midjourney.columns.prompt')"
        min-width="150"
      >
        <template #default="{ row }">
          <span
            v-if="row.prompt"
            class="mj-table__cell-text"
            @click="emit('view-prompt', row.prompt, t('midjourney.columns.prompt'))"
          >
            <LongText :content="row.prompt" />
          </span>
          <span
            v-else
            class="mj-table__muted"
          >{{ t('midjourney.common.none') }}</span>
        </template>
      </ElTableColumn>

      <!-- PromptEn -->
      <ElTableColumn
        :label="t('midjourney.columns.promptEn')"
        min-width="150"
      >
        <template #default="{ row }">
          <span
            v-if="row.promptEn"
            class="mj-table__cell-text"
            @click="emit('view-prompt', row.promptEn, t('midjourney.columns.promptEn'))"
          >
            <LongText :content="row.promptEn" />
          </span>
          <span
            v-else
            class="mj-table__muted"
          >{{ t('midjourney.common.none') }}</span>
        </template>
      </ElTableColumn>

      <!-- 失败原因 -->
      <ElTableColumn
        :label="t('midjourney.columns.failReason')"
        min-width="150"
        fixed="right"
      >
        <template #default="{ row }">
          <span
            v-if="row.failReason"
            class="mj-table__cell-text mj-table__cell-text--danger"
            @click="emit('view-prompt', row.failReason, t('midjourney.columns.failReason'))"
          >
            <LongText :content="row.failReason" />
          </span>
          <span
            v-else
            class="mj-table__muted"
          >{{ t('midjourney.common.none') }}</span>
        </template>
      </ElTableColumn>
    </ElTable>
  </div>
</template>

<style scoped>
.mj-table {
  width: 100%;
}

.mj-table__mono {
  font-family: var(--el-font-family-mono, monospace);
  font-size: var(--ys-font-size-xs);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.mj-table__muted {
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-placeholder);
}

.mj-table__channel-tag {
  cursor: pointer;
}

.mj-table__cell-text {
  display: block;
  max-width: 200px;
  cursor: pointer;
}

.mj-table__cell-text--danger {
  color: var(--el-color-danger);
}

.mj-table--compact :deep(.el-table__row) {
  height: 36px;
}

.mj-table--compact :deep(.el-table__cell) {
  padding: 2px 0;
}
</style>
