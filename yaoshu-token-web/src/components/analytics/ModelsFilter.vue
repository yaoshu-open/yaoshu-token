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
import type { DashboardFilters, TimeGranularity } from '@/api/dashboard/types'

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

// DatePicker 'x' value-format 返回毫秒字符串，需转为秒
function dateRangeToTimestamps(range: [string, string] | null): {
  start_timestamp?: number
  end_timestamp?: number
} {
  if (!range || range.length !== 2) return { start_timestamp: undefined, end_timestamp: undefined }
  return {
    start_timestamp: Math.floor(Number(range[0]) / 1000),
    end_timestamp: Math.floor(Number(range[1]) / 1000),
  }
}

function timestampsToDateRange(filters: DashboardFilters): [string, string] | null {
  if (!filters.start_timestamp || !filters.end_timestamp) return null
  return [String(filters.start_timestamp * 1000), String(filters.end_timestamp * 1000)]
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
          value-format="x"
          style="width: 100%"
          :start-placeholder="t('analytics.filter.timeRange')"
          :end-placeholder="t('analytics.filter.timeRange')"
          :model-value="timestampsToDateRange(draft)"
          @update:model-value="(v: [string, string] | null) => {
            const ts = dateRangeToTimestamps(v)
            draft = { ...draft, ...ts }
          }"
        />
      </ElFormItem>
      <ElFormItem :label="t('analytics.filter.granularity')">
        <ElSelect
          v-model="draft.time_granularity as TimeGranularity | undefined"
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
      <ElFormItem v-if="isAdmin" label="username">
        <ElInput
          v-model="draft.username as string"
          placeholder="username"
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
