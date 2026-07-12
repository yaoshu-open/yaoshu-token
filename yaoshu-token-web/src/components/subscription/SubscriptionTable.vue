<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import {
  ElTable,
  ElTableColumn,
  ElTag,
  ElButton,
  ElPopconfirm,
} from 'element-plus'
import type { SubscriptionPlan } from '@/api/subscription/types'
import { formatPlanPrice } from '@/utils/currency'

defineProps<{
  plans: SubscriptionPlan[]
  loading: boolean
}>()

const emit = defineEmits<{
  (e: 'edit', plan: SubscriptionPlan): void
  (e: 'toggle-status', plan: SubscriptionPlan): void
}>()

const { t } = useI18n()

const durationLabels: Record<string, string> = {
  year: 'subscription.duration.year',
  month: 'subscription.duration.month',
  day: 'subscription.duration.day',
  hour: 'subscription.duration.hour',
  custom: 'subscription.duration.custom',
}

function formatDuration(plan: SubscriptionPlan) {
  const unit = t(durationLabels[plan.durationUnit] || plan.durationUnit)
  return `${plan.durationValue} ${unit}`
}
</script>

<template>
  <ElTable
    v-loading="loading"
    :data="plans"
    stripe
    style="width: 100%"
  >
    <ElTableColumn
      prop="title"
      :label="t('subscription.title')"
      min-width="160"
    />
    <ElTableColumn
      :label="t('subscription.priceAmount')"
      width="120"
      align="right"
    >
      <template #default="{ row }">
        {{ formatPlanPrice(row.priceAmount, row.currency) }}
      </template>
    </ElTableColumn>
    <ElTableColumn
      :label="t('subscription.durationUnit')"
      width="120"
    >
      <template #default="{ row }">
        {{ formatDuration(row as SubscriptionPlan) }}
      </template>
    </ElTableColumn>
    <ElTableColumn
      prop="totalAmount"
      :label="t('subscription.totalAmount')"
      width="100"
      align="right"
    />
    <ElTableColumn
      :label="t('subscription.quotaResetPeriod')"
      width="110"
    >
      <template #default="{ row }">
        {{ t(`subscription.reset.${row.quotaResetPeriod}`) }}
      </template>
    </ElTableColumn>
    <ElTableColumn
      :label="t('subscription.enabled')"
      width="90"
      align="center"
    >
      <template #default="{ row }">
        <ElTag
          :type="row.enabled ? 'success' : 'info'"
          size="small"
        >
          {{ row.enabled ? t('common.enabled') : t('common.disabled') }}
        </ElTag>
      </template>
    </ElTableColumn>
    <ElTableColumn
      :label="t('subscription.sortOrder')"
      width="80"
      align="center"
    >
      <template #default="{ row }">
        {{ row.sortOrder }}
      </template>
    </ElTableColumn>
    <ElTableColumn
      :label="t('common.actions')"
      width="200"
      fixed="right"
    >
      <template #default="{ row }">
        <ElButton
          size="small"
          text
          type="primary"
          @click="emit('edit', row as SubscriptionPlan)"
        >
          {{ t('common.edit') }}
        </ElButton>
        <ElPopconfirm
          :title="
            row.enabled
              ? t('subscription.disableConfirm')
              : t('subscription.enableConfirm')
          "
          @confirm="emit('toggle-status', row as SubscriptionPlan)"
        >
          <template #reference>
            <ElButton
              size="small"
              text
              :type="row.enabled ? 'warning' : 'success'"
            >
              {{ row.enabled ? t('common.disable') : t('common.enable') }}
            </ElButton>
          </template>
        </ElPopconfirm>
      </template>
    </ElTableColumn>
  </ElTable>
</template>
