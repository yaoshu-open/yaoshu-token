<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElSelect,
  ElOption,
  ElSwitch,
  ElButton,
  ElDivider,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import { useSubscriptionMutateForm } from '@/composables/subscription/useSubscriptionMutateForm'
import type { SubscriptionPlan } from '@/api/subscription/types'

const props = defineProps<{
  modelValue: boolean
  plan?: SubscriptionPlan | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'success'): void
}>()

const { t } = useI18n()

const { mode, loading, formData, initCreate, initUpdate, submit } =
  useSubscriptionMutateForm(() => {
    emit('success')
    emit('update:modelValue', false)
  })

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      if (props.plan) {
        initUpdate(props.plan)
      } else {
        initCreate()
      }
    }
  }
)

const durationUnits = [
  { label: 'subscription.duration.year', value: 'year' },
  { label: 'subscription.duration.month', value: 'month' },
  { label: 'subscription.duration.day', value: 'day' },
  { label: 'subscription.duration.hour', value: 'hour' },
  { label: 'subscription.duration.custom', value: 'custom' },
]

const resetPeriods = [
  { label: 'subscription.reset.never', value: 'never' },
  { label: 'subscription.reset.daily', value: 'daily' },
  { label: 'subscription.reset.weekly', value: 'weekly' },
  { label: 'subscription.reset.monthly', value: 'monthly' },
  { label: 'subscription.reset.custom', value: 'custom' },
]

const formRef = ref<FormInstance>()
const rules: FormRules = {
  title: [{ required: true, message: t('subscription.title'), trigger: 'blur' }],
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submit(props.plan?.id)
}

function handleClose() {
  emit('update:modelValue', false)
}
</script>

<template>
  <ElDrawer
    :model-value="modelValue"
    :title="
      mode === 'create'
        ? t('subscription.createPlan')
        : t('subscription.editPlan')
    "
    size="520px"
    direction="rtl"
    @close="handleClose"
  >
    <ElForm
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-width="140px"
      label-position="left"
    >
      <!-- Basic Info -->
      <ElFormItem
        :label="t('subscription.title')"
        prop="title"
        required
      >
        <ElInput
          v-model="formData.title"
          :placeholder="t('subscription.titlePlaceholder')"
        />
      </ElFormItem>
      <ElFormItem :label="t('subscription.subtitle')">
        <ElInput
          v-model="formData.subtitle"
          :placeholder="t('subscription.subtitlePlaceholder')"
        />
      </ElFormItem>

      <ElDivider />

      <!-- Pricing -->
      <ElFormItem
        :label="t('subscription.priceAmount')"
        required
      >
        <ElInputNumber
          v-model="formData.priceAmount"
          :min="0"
          :precision="2"
          style="width: 100%"
        />
      </ElFormItem>
      <ElFormItem :label="t('subscription.currency')">
        <ElSelect v-model="formData.currency">
          <ElOption
            label="USD"
            value="USD"
          />
          <ElOption
            label="CNY"
            value="CNY"
          />
          <ElOption
            label="EUR"
            value="EUR"
          />
        </ElSelect>
      </ElFormItem>

      <ElDivider />

      <!-- Duration -->
      <ElFormItem
        :label="t('subscription.durationUnit')"
        required
      >
        <ElSelect v-model="formData.durationUnit">
          <ElOption
            v-for="u in durationUnits"
            :key="u.value"
            :label="t(u.label)"
            :value="u.value"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem
        :label="t('subscription.durationValue')"
        required
      >
        <ElInputNumber
          v-model="formData.durationValue"
          :min="1"
          style="width: 100%"
        />
      </ElFormItem>
      <ElFormItem
        v-if="formData.durationUnit === 'custom'"
        :label="t('subscription.customSeconds')"
      >
        <ElInputNumber
          v-model="formData.customSeconds"
          :min="1"
          style="width: 100%"
        />
      </ElFormItem>

      <ElDivider />

      <!-- Quota Reset -->
      <ElFormItem
        :label="t('subscription.quotaResetPeriod')"
        required
      >
        <ElSelect v-model="formData.quotaResetPeriod">
          <ElOption
            v-for="p in resetPeriods"
            :key="p.value"
            :label="t(p.label)"
            :value="p.value"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem
        v-if="formData.quotaResetPeriod === 'custom'"
        :label="t('subscription.quotaResetCustomSeconds')"
      >
        <ElInputNumber
          v-model="formData.quotaResetCustomSeconds"
          :min="1"
          style="width: 100%"
        />
      </ElFormItem>

      <ElDivider />

      <!-- Config -->
      <ElFormItem
        :label="t('subscription.totalAmount')"
        required
      >
        <ElInputNumber
          v-model="formData.totalAmount"
          :min="0"
          style="width: 100%"
        />
      </ElFormItem>
      <ElFormItem :label="t('subscription.sortOrder')">
        <ElInputNumber
          v-model="formData.sortOrder"
          :min="0"
          style="width: 100%"
        />
      </ElFormItem>
      <ElFormItem :label="t('subscription.maxPurchasePerUser')">
        <ElInputNumber
          v-model="formData.maxPurchasePerUser"
          :min="1"
          style="width: 100%"
        />
      </ElFormItem>

      <ElDivider />

      <!-- Toggles -->
      <ElFormItem :label="t('subscription.enabled')">
        <ElSwitch v-model="formData.enabled" />
      </ElFormItem>
      <ElFormItem :label="t('subscription.allowBalancePay')">
        <ElSwitch v-model="formData.allowBalancePay" />
      </ElFormItem>

      <ElDivider />

      <!-- Optional Integration Fields -->
      <ElFormItem :label="t('subscription.upgradeGroup')">
        <ElInput
          v-model="formData.upgradeGroup"
          :placeholder="t('subscription.upgradeGroupHint')"
        />
      </ElFormItem>
      <ElFormItem :label="t('subscription.stripePriceId')">
        <ElInput
          v-model="formData.stripePriceId"
          placeholder="price_xxx"
        />
      </ElFormItem>
      <ElFormItem :label="t('subscription.creemProductId')">
        <ElInput
          v-model="formData.creemProductId"
          placeholder="prod_xxx"
        />
      </ElFormItem>
      <ElFormItem :label="t('subscription.waffoPancakeProductId')">
        <ElInput
          v-model="formData.waffoPancakeProductId"
          placeholder="prod_xxx"
        />
      </ElFormItem>
    </ElForm>

    <template #footer>
      <div class="drawer-footer">
        <ElButton @click="handleClose">
          {{ t('common.cancel') }}
        </ElButton>
        <ElButton
          type="primary"
          :loading="loading"
          @click="handleSubmit"
        >
          {{ mode === 'create' ? t('common.create') : t('common.save') }}
        </ElButton>
      </div>
    </template>
  </ElDrawer>
</template>

<style scoped lang="scss">
.drawer-footer {
  display: flex;
  gap: var(--ys-spacing-3);
  justify-content: flex-end;
}
</style>
