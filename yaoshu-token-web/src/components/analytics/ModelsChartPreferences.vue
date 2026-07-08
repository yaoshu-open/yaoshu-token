<script setup lang="ts">
/**
 * 图表偏好设置 Dialog（对齐原版 ModelsChartPreferences）。
 * 配置项：默认时间范围 / 默认时间粒度 / 默认消费分布图 / 默认模型分析图。
 */
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElDialog, ElForm, ElFormItem, ElSelect, ElOption, ElButton } from 'element-plus'
import { Setting } from '@element-plus/icons-vue'
import {
  CONSUMPTION_DISTRIBUTION_CHART_OPTIONS,
  MODEL_ANALYTICS_CHART_OPTIONS,
  TIME_GRANULARITY_OPTIONS,
  TIME_RANGE_PRESETS,
} from '@/api/dashboard/constants'
import type {
  ConsumptionDistributionChartType,
  DashboardChartPreferences,
  ModelAnalyticsChartTab,
} from '@/api/dashboard/types'

const props = defineProps<{
  preferences: DashboardChartPreferences
}>()
const emit = defineEmits<{
  (e: 'update:preferences', next: DashboardChartPreferences): void
}>()

const { t } = useI18n()
const visible = ref(false)
const draft = ref<DashboardChartPreferences>({ ...props.preferences })

watch(visible, (open) => {
  if (open) draft.value = { ...props.preferences }
})

function handleSave() {
  emit('update:preferences', draft.value)
  visible.value = false
}
</script>

<template>
  <ElButton
    size="small"
    :icon="Setting"
    @click="visible = true"
  >
    {{ t('analytics.preferences.title') }}
  </ElButton>
  <ElDialog
    v-model="visible"
    :title="t('analytics.preferences.title')"
    width="420px"
    append-to-body
  >
    <ElForm label-width="140px" label-position="left">
      <ElFormItem :label="t('analytics.preferences.defaultRange')">
        <ElSelect v-model="draft.defaultTimeRangeDays" style="width: 100%">
          <ElOption
            v-for="opt in TIME_RANGE_PRESETS"
            :key="opt.days"
            :label="t(opt.label)"
            :value="opt.days"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem :label="t('analytics.preferences.defaultGranularity')">
        <ElSelect v-model="draft.defaultTimeGranularity" style="width: 100%">
          <ElOption
            v-for="opt in TIME_GRANULARITY_OPTIONS"
            :key="opt.value"
            :label="t(opt.label)"
            :value="opt.value"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem :label="t('analytics.preferences.defaultConsumptionChart')">
        <ElSelect
          v-model="draft.consumptionDistributionChart as ConsumptionDistributionChartType"
          style="width: 100%"
        >
          <ElOption
            v-for="opt in CONSUMPTION_DISTRIBUTION_CHART_OPTIONS"
            :key="opt.value"
            :label="t(opt.labelKey)"
            :value="opt.value"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem :label="t('analytics.preferences.defaultModelChart')">
        <ElSelect
          v-model="draft.modelAnalyticsChart as ModelAnalyticsChartTab"
          style="width: 100%"
        >
          <ElOption
            v-for="opt in MODEL_ANALYTICS_CHART_OPTIONS"
            :key="opt.value"
            :label="t(opt.labelKey)"
            :value="opt.value"
          />
        </ElSelect>
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="visible = false">{{ t('common.cancel') }}</ElButton>
      <ElButton type="primary" @click="handleSave">
        {{ t('analytics.preferences.save') }}
      </ElButton>
    </template>
  </ElDialog>
</template>
