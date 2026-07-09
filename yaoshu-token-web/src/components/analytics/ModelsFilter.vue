<script setup lang="ts">
/**
 * 筛选条件 Dialog（对齐原版 ModelsFilter）。
 * 支持时间范围 + 时间粒度 + username（admin only）覆盖偏好。
 */
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  ElDialog,
  ElForm,
  ElFormItem,
  ElSelect,
  ElOption,
  ElButton,
  ElDatePicker,
  ElInput,
} from 'element-plus'
import { Filter } from '@element-plus/icons-vue'
import { TIME_GRANULARITY_OPTIONS } from '@/api/dashboard/constants'
import type { DashboardFilters } from '@/api/dashboard/types'

const props = defineProps<{
  filters: DashboardFilters
  isAdmin?: boolean
}>()
const emit = defineEmits<{
  (e: 'update:filters', next: DashboardFilters): void
  (e: 'reset'): void
}>()

const { t } = useI18n()
const visible = ref(false)
const draft = ref<DashboardFilters>({ ...props.filters })

// 日期选择器默认时间：起始 00:00:00，结束 23:59:59（确保结束日期当天数据被包含）
const defaultTime: [Date, Date] = [
  new Date(2000, 0, 1, 0, 0, 0),
  new Date(2000, 0, 1, 23, 59, 59),
]

watch(visible, (open) => {
  if (open) draft.value = { ...props.filters }
})

function handleApply() {
  emit('update:filters', draft.value)
  visible.value = false
}

function handleReset() {
  emit('reset')
  visible.value = false
}

// Date 对象 -> 秒级时间戳
function dateRangeToTimestamps(range: [Date, Date] | null): {
  start_timestamp?: number
  end_timestamp?: number
} {
  if (!range || range.length !== 2) return { start_timestamp: undefined, end_timestamp: undefined }
  return {
    start_timestamp: Math.floor(range[0].getTime() / 1000),
    end_timestamp: Math.floor(range[1].getTime() / 1000),
  }
}

// 秒级时间戳 -> Date 对象
function timestampsToDateRange(filters: DashboardFilters): [Date, Date] | null {
  if (!filters.start_timestamp || !filters.end_timestamp) return null
  return [new Date(filters.start_timestamp * 1000), new Date(filters.end_timestamp * 1000)]
}
</script>

<template>
  <ElButton
    size="small"
    :icon="Filter"
    @click="visible = true"
  >
    {{ t('analytics.filter.title') }}
  </ElButton>
  <ElDialog
    v-model="visible"
    :title="t('analytics.filter.title')"
    width="480px"
    append-to-body
  >
    <ElForm label-width="100px" label-position="left">
      <ElFormItem :label="t('analytics.filter.timeRange')">
        <ElDatePicker
          type="datetimerange"
          style="width: 100%"
          :start-placeholder="t('analytics.filter.timeRange')"
          :end-placeholder="t('analytics.filter.timeRange')"
          :default-time="defaultTime"
          :model-value="timestampsToDateRange(draft)"
          @update:model-value="(v: [Date, Date] | null) => {
            const ts = dateRangeToTimestamps(v)
            draft = { ...draft, ...ts }
          }"
        />
      </ElFormItem>
      <ElFormItem :label="t('analytics.filter.granularity')">
        <ElSelect
          v-model="draft.time_granularity"
          style="width: 100%"
        >
          <ElOption
            v-for="opt in TIME_GRANULARITY_OPTIONS"
            :key="opt.value"
            :label="t(opt.label)"
            :value="opt.value"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem v-if="isAdmin" :label="t('analytics.filter.username')">
        <ElInput
          v-model="draft.username"
          :placeholder="t('analytics.filter.usernamePlaceholder')"
          clearable
        />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="handleReset">{{ t('analytics.filter.reset') }}</ElButton>
      <ElButton type="primary" @click="handleApply">
        {{ t('common.search') }}
      </ElButton>
    </template>
  </ElDialog>
</template>
