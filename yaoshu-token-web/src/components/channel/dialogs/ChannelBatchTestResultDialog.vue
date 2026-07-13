<script setup lang="ts">
/**
 * 批量渠道测试结果摘要对话框。
 * 展示每个被测渠道的测试明细：渠道名/测试模型/成功与否/响应耗时/状态变更/失败原因。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ChannelBatchTestResponse, ChannelBatchTestItem } from '@/api/channel/types'

interface Props {
  visible: boolean
  data: ChannelBatchTestResponse | null
  loading?: boolean
  error?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  error: false,
})
const emit = defineEmits<{
  'update:visible': [val: boolean]
}>()

const { t } = useI18n()

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})

// 成功/失败统计
const summary = computed(() => {
  const results = props.data?.results ?? []
  const success = results.filter((r) => r.success).length
  const failed = results.length - success
  const statusChanged = results.filter((r) => r.statusChanged).length
  return { total: results.length, success, failed, statusChanged }
})

// 响应耗时格式化：毫秒 → 友好显示（<1s 显示 ms，>=1s 显示秒）
function formatResponseTime(ms: number): string {
  if (!ms && ms !== 0) return '-'
  if (ms < 1000) return `${ms} ms`
  return `${(ms / 1000).toFixed(1)} s`
}

function getStatusType(item: ChannelBatchTestItem): 'success' | 'danger' | 'warning' {
  if (item.success) return 'success'
  return 'danger'
}

function getStatusLabel(item: ChannelBatchTestItem): string {
  return item.success ? t('channel.dialog.batchTest.success') : t('channel.dialog.batchTest.failed')
}
</script>

<template>
  <ElDialog
    v-model="dialogVisible"
    :title="t('channel.dialog.batchTest.title')"
    width="720px"
    append-to-body
  >
    <!-- 测试中：转圈等待 -->
    <div
      v-if="loading"
      v-loading="true"
      class="batch-test-result__loading"
    >
      <p class="batch-test-result__loading-text">
        {{ t('channel.dialog.batchTest.testing') }}
      </p>
    </div>

    <!-- 测试失败：错误态 -->
    <div
      v-else-if="error"
      class="batch-test-result__error-state"
    >
      <ElEmpty
        :description="t('channel.dialog.batchTest.loadFailed')"
        :image-size="80"
      />
    </div>

    <!-- 测试完成：结果展示 -->
    <div
      v-else-if="data"
      class="batch-test-result"
    >
      <!-- 汇总统计 -->
      <div class="batch-test-result__summary">
        <span class="batch-test-result__summary-item">
          {{ t('channel.dialog.batchTest.completed') }}：{{ data.completed }}/{{ data.total }}
        </span>
        <span class="batch-test-result__summary-item batch-test-result__summary-item--success">
          {{ t('channel.dialog.batchTest.successCount') }}：{{ summary.success }}
        </span>
        <span class="batch-test-result__summary-item batch-test-result__summary-item--danger">
          {{ t('channel.dialog.batchTest.failedCount') }}：{{ summary.failed }}
        </span>
        <span
          v-if="summary.statusChanged > 0"
          class="batch-test-result__summary-item batch-test-result__summary-item--warning"
        >
          {{ t('channel.dialog.batchTest.statusChangedCount') }}：{{ summary.statusChanged }}
        </span>
      </div>

      <!-- 渠道测试明细表格 -->
      <ElTable
        :data="data.results"
        size="small"
        max-height="400"
        class="batch-test-result__table"
      >
        <ElTableColumn
          :label="t('channel.dialog.batchTest.colChannelName')"
          prop="channelName"
          min-width="120"
        />
        <ElTableColumn
          :label="t('channel.dialog.batchTest.colTestModel')"
          prop="testModel"
          min-width="140"
        >
          <template #default="{ row }">
            <span class="batch-test-result__model">{{ row.testModel || '-' }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn
          :label="t('channel.dialog.batchTest.colStatus')"
          width="90"
          align="center"
        >
          <template #default="{ row }">
            <ElTag
              :type="getStatusType(row as ChannelBatchTestItem)"
              size="small"
              effect="light"
            >
              {{ getStatusLabel(row as ChannelBatchTestItem) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn
          :label="t('channel.dialog.batchTest.colResponseTime')"
          width="110"
          align="right"
        >
          <template #default="{ row }">
            <span class="batch-test-result__time">{{ formatResponseTime(row.responseTime) }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn
          :label="t('channel.dialog.batchTest.colStatusChanged')"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <ElTag
              v-if="row.statusChanged"
              type="warning"
              size="small"
              effect="plain"
            >
              {{ t('channel.dialog.batchTest.changed') }}
            </ElTag>
            <span v-else class="batch-test-result__dash">-</span>
          </template>
        </ElTableColumn>
        <ElTableColumn
          :label="t('channel.dialog.batchTest.colError')"
          min-width="160"
        >
          <template #default="{ row }">
            <span
              v-if="row.error"
              class="batch-test-result__error"
              :title="row.error"
            >
              {{ row.error }}
            </span>
            <span v-else class="batch-test-result__dash">-</span>
          </template>
        </ElTableColumn>
      </ElTable>

      <!-- 说明 -->
      <p class="batch-test-result__hint">
        {{ t('channel.dialog.batchTest.hint') }}
      </p>
    </div>
  </ElDialog>
</template>

<style scoped lang="scss">
.batch-test-result {
  &__loading {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    min-height: 200px;
    gap: var(--ys-spacing-3);
  }

  &__loading-text {
    margin: 0;
    font-size: var(--ys-font-size-sm);
    color: var(--el-text-color-secondary);
  }

  &__error-state {
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 200px;
  }

  &__summary {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-4);
    margin-bottom: var(--ys-spacing-4);
    padding: var(--ys-spacing-3) var(--ys-spacing-4);
    background: var(--el-fill-color-light);
    border-radius: var(--ys-radius-sm);
  }

  &__summary-item {
    font-size: var(--ys-font-size-sm);
    color: var(--el-text-color-regular);

    &--success {
      color: var(--el-color-success);
    }

    &--danger {
      color: var(--el-color-danger);
    }

    &--warning {
      color: var(--el-color-warning);
    }
  }

  &__table {
    width: 100%;
  }

  &__model {
    font-family: 'JetBrains Mono', monospace;
    font-size: var(--ys-font-size-xs);
  }

  &__time {
    font-variant-numeric: tabular-nums;
    font-size: var(--ys-font-size-sm);
  }

  &__error {
    display: -webkit-box;
    overflow: hidden;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    font-size: var(--ys-font-size-xs);
    color: var(--el-color-danger);
  }

  &__dash {
    color: var(--el-text-color-placeholder);
  }

  &__hint {
    margin: var(--ys-spacing-3) 0 0;
    font-size: var(--ys-font-size-xs);
    line-height: 1.5;
    color: var(--el-text-color-secondary);
  }
}
</style>
