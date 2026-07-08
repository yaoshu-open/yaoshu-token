<script setup lang="ts">
/**
 * MJ 任务日志筛选区。
 *
 * 筛选项：日期范围（默认最近 30 天）+ 任务 ID + 渠道 ID（管理员）。
 */
import { ElButton, ElDatePicker, ElInput } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import type { MjLogsFilters } from '@/composables/midjourney/useMjLogsData'

defineProps<{
  filters: MjLogsFilters
  loading: boolean
  isAdmin: boolean
}>()

const emit = defineEmits<{
  (e: 'search'): void
  (e: 'reset'): void
  (e: 'update:filters', partial: Partial<MjLogsFilters>): void
}>()

const { t } = useI18n()

function updateField<K extends keyof MjLogsFilters>(key: K, value: MjLogsFilters[K]): void {
  emit('update:filters', { [key]: value } as Partial<MjLogsFilters>)
}

function updateDateRange(value: [Date, Date] | null): void {
  emit('update:filters', { dateRange: value })
}
</script>

<template>
  <div class="mj-toolbar">
    <div class="mj-toolbar__row">
      <!-- 日期范围 -->
      <ElDatePicker
        :model-value="filters.dateRange"
        type="datetimerange"
        :start-placeholder="t('midjourney.filters.startPh')"
        :end-placeholder="t('midjourney.filters.endPh')"
        clearable
        size="default"
        class="mj-toolbar__date-range"
        @update:model-value="updateDateRange"
      />

      <!-- 任务 ID -->
      <ElInput
        :model-value="filters.mjId"
        :placeholder="t('midjourney.filters.taskIdPh')"
        :prefix-icon="Search"
        clearable
        size="default"
        class="mj-toolbar__input"
        @update:model-value="updateField('mjId', $event)"
      />

      <!-- 渠道 ID（仅管理员） -->
      <ElInput
        v-if="isAdmin"
        :model-value="filters.channelId"
        :placeholder="t('midjourney.filters.channelIdPh')"
        :prefix-icon="Search"
        clearable
        size="default"
        class="mj-toolbar__input"
        @update:model-value="updateField('channelId', $event)"
      />
    </div>

    <div class="mj-toolbar__actions">
      <ElButton
        type="primary"
        :loading="loading"
        size="default"
        @click="emit('search')"
      >
        {{ t('midjourney.filters.search') }}
      </ElButton>
      <ElButton
        size="default"
        @click="emit('reset')"
      >
        {{ t('midjourney.filters.reset') }}
      </ElButton>
    </div>
  </div>
</template>

<style scoped>
.mj-toolbar {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-3);
  margin-bottom: 16px;
}

.mj-toolbar__row {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr;
  gap: var(--ys-spacing-3);
  align-items: center;
}

.mj-toolbar__date-range {
  width: 100%;
}

.mj-toolbar__input {
  width: 100%;
}

.mj-toolbar__actions {
  display: flex;
  gap: var(--ys-spacing-2);
  justify-content: flex-end;
}

@media (width <= 768px) {
  .mj-toolbar__row {
    grid-template-columns: 1fr;
  }
}
</style>
