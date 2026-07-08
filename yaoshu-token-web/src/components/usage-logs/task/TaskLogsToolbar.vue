<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Search, Refresh } from '@element-plus/icons-vue'
import type { TaskLogsFilters } from '@/composables/usage-logs/useTaskLogsData'

interface TaskLogsToolbarProps {
  filters: TaskLogsFilters
  loading?: boolean
  isAdmin?: boolean
}

const props = defineProps<TaskLogsToolbarProps>()
const emit = defineEmits<{
  (e: 'search'): void
  (e: 'reset'): void
  (e: 'update:filters', partial: Partial<TaskLogsFilters>): void
}>()
const { t } = useI18n()

function update<K extends keyof TaskLogsFilters>(key: K, value: TaskLogsFilters[K]) {
  emit('update:filters', { [key]: value } as Partial<TaskLogsFilters>)
}
</script>

<template>
  <div class="task-logs-toolbar">
    <div class="task-logs-toolbar__row">
      <ElDatePicker
        :model-value="props.filters.dateRange"
        type="datetimerange"
        :start-placeholder="t('usageLogs.filter.startTime')"
        :end-placeholder="t('usageLogs.filter.endTime')"
        @update:model-value="(v: [Date, Date] | null) => update('dateRange', v)"
      />
      <ElInput
        :model-value="props.filters.taskId"
        :placeholder="t('usageLogs.filter.taskId')"
        clearable
        @update:model-value="(v: string) => update('taskId', v)"
        @keyup.enter="emit('search')"
      />
      <ElInput
        v-if="isAdmin"
        :model-value="props.filters.channelId"
        :placeholder="t('usageLogs.filter.channelId')"
        clearable
        @update:model-value="(v: string) => update('channelId', v)"
        @keyup.enter="emit('search')"
      />
    </div>
    <div class="task-logs-toolbar__actions">
      <ElButton
        type="primary"
        :icon="Search"
        :loading="loading"
        @click="emit('search')"
      >
        {{ t('common.search') }}
      </ElButton>
      <ElButton
        :icon="Refresh"
        @click="emit('reset')"
      >
        {{ t('common.reset') }}
      </ElButton>
    </div>
  </div>
</template>

<style scoped lang="scss">
.task-logs-toolbar {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-3);
  padding: var(--ys-spacing-4);
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--ys-radius-md);

  &__row {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ys-spacing-2);

    .el-date-editor,
    .el-input {
      width: 240px;
    }
  }

  &__actions {
    display: flex;
    gap: var(--ys-spacing-2);
  }
}
</style>
