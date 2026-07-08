<script setup lang="ts">
/**
 * 内部使用 useBillingHistory composable，支持分页 + 搜索 + 管理员完成订单。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useBillingHistory } from '@/composables/wallet/useBillingHistory'
import {
  formatBillingTimestamp,
  getBillingStatusConfig,
  getPaymentMethodName,
} from '@/utils/wallet/billing'
import { formatCurrency } from '@/utils/wallet/format'

interface Props {
  visible: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
}>()

const { t } = useI18n()

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})

const {
  records,
  total,
  page,
  pageSize,
  keyword,
  loading,
  completing,
  isAdmin,
  handlePageChange,
  handlePageSizeChange,
  handleSearch,
  handleCompleteOrder,
} = useBillingHistory()
</script>

<template>
  <ElDialog
    v-model="dialogVisible"
    :title="t('wallet.billing.title')"
    width="800px"
    align-center
    destroy-on-close
  >
    <div class="billing-history">
      <ElInput
        :model-value="keyword"
        :placeholder="t('wallet.billing.searchPlaceholder')"
        clearable
        class="billing-history__search"
        @update:model-value="handleSearch"
      />
      <ElTable
        v-loading="loading"
        :data="records"
        size="small"
        class="billing-history__table"
      >
        <ElTableColumn
          prop="tradeNo"
          :label="t('wallet.billing.tradeNo')"
          min-width="180"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="amount"
          :label="t('wallet.billing.amount')"
          width="100"
        />
        <ElTableColumn
          prop="money"
          :label="t('wallet.billing.money')"
          width="100"
        >
          <template #default="{ row }">
            {{ formatCurrency(row.money) }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="paymentMethod"
          :label="t('wallet.billing.method')"
          width="120"
        >
          <template #default="{ row }">
            {{ getPaymentMethodName(row.paymentMethod, t) }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="createTime"
          :label="t('wallet.billing.createTime')"
          width="160"
        >
          <template #default="{ row }">
            {{ formatBillingTimestamp(row.createTime) }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="status"
          :label="t('wallet.billing.status')"
          width="100"
        >
          <template #default="{ row }">
            <ElTag
              :type="getBillingStatusConfig(row.status).type"
              size="small"
            >
              {{ t(`wallet.billing.statusLabel.${row.status}`) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn
          v-if="isAdmin"
          :label="t('wallet.billing.actions')"
          width="100"
          fixed="right"
        >
          <template #default="{ row }">
            <ElButton
              v-if="row.status === 'pending'"
              type="primary"
              size="small"
              link
              :loading="completing"
              @click="handleCompleteOrder(row.tradeNo)"
            >
              {{ t('wallet.billing.complete') }}
            </ElButton>
          </template>
        </ElTableColumn>
        <template #empty>
          <ElEmpty :description="t('wallet.billing.empty')" />
        </template>
      </ElTable>
      <div class="billing-history__footer">
        <ElPagination
          :current-page="page"
          :page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          small
          @current-change="handlePageChange"
          @size-change="handlePageSizeChange"
        />
      </div>
    </div>
  </ElDialog>
</template>

<style scoped lang="scss">
.billing-history {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-3);

  &__search {
    max-width: 320px;
  }

  &__table {
    width: 100%;
  }

  &__footer {
    display: flex;
    justify-content: flex-end;
  }
}
</style>
