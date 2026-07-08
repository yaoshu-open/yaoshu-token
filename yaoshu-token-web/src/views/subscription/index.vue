<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElButton, ElInput, ElEmpty, ElAlert, ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useSubscriptionsData } from '@/composables/subscription/useSubscriptionsData'
import SubscriptionTable from '@/components/subscription/SubscriptionTable.vue'
import SubscriptionMutateDrawer from '@/components/subscription/SubscriptionMutateDrawer.vue'
import type { SubscriptionPlan } from '@/api/subscription/types'

const { t } = useI18n()

const {
  filteredPlans,
  loading,
  searchKeyword,
  complianceConfirmed,
  fetchPlans,
  togglePlanStatus,
} = useSubscriptionsData()

const drawerOpen = ref(false)
const editingPlan = ref<SubscriptionPlan | null>(null)

function handleCreate() {
  editingPlan.value = null
  drawerOpen.value = true
}

function handleEdit(plan: SubscriptionPlan) {
  editingPlan.value = plan
  drawerOpen.value = true
}

async function handleToggleStatus(plan: SubscriptionPlan) {
  try {
    await togglePlanStatus(plan.id, !plan.enabled)
    ElMessage.success(
      plan.enabled
        ? t('subscription.disabled')
        : t('subscription.enabled')
    )
  } catch {
    ElMessage.error(t('common.error'))
  }
}

function handleDrawerSuccess() {
  fetchPlans()
}
</script>

<template>
  <div class="subscription-page">
    <div class="subscription-page__header">
      <h1 class="subscription-page__title">
        {{ t('nav.subscriptions') }}
      </h1>
      <div class="subscription-page__actions">
        <ElInput
          v-model="searchKeyword"
          :placeholder="t('common.search')"
          clearable
          style="width: 240px"
        />
        <ElButton
          type="primary"
          :icon="Plus"
          :disabled="!complianceConfirmed"
          @click="handleCreate"
        >
          {{ t('subscription.createPlan') }}
        </ElButton>
      </div>
    </div>

    <ElAlert
      v-if="!complianceConfirmed"
      type="error"
      :closable="false"
      show-icon
      style="margin-bottom: var(--ys-spacing-4)"
    >
      {{ t('subscription.complianceLocked') }}
    </ElAlert>

    <div
      class="subscription-page__hint"
      style="margin-bottom: var(--ys-spacing-3)"
    >
      <ElAlert
        type="info"
        :closable="false"
      >
        {{ t('subscription.stripeCreemHint') }}
      </ElAlert>
    </div>

    <SubscriptionTable
      :plans="filteredPlans"
      :loading="loading"
      @edit="handleEdit"
      @toggle-status="handleToggleStatus"
    />

    <ElEmpty
      v-if="!loading && filteredPlans.length === 0"
      :description="t('subscription.noPlans')"
    />

    <SubscriptionMutateDrawer
      v-model="drawerOpen"
      :plan="editingPlan"
      @success="handleDrawerSuccess"
    />
  </div>
</template>

<style scoped lang="scss">
.subscription-page {
  padding: var(--ys-spacing-6);

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: var(--ys-spacing-4);
  }

  &__title {
    margin: 0;
    font-size: var(--ys-font-size-xl);
    font-weight: 600;
  }

  &__actions {
    display: flex;
    gap: var(--ys-spacing-3);
    align-items: center;
  }
}
</style>
