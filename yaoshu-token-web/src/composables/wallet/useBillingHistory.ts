/**
 * 支持分页、关键字搜索、管理员完成订单。
 */
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  completeOrder,
  getAllBillingHistory,
  getUserBillingHistory,
} from '@/api/wallet'
import { useUserPermissions } from '@/composables/useUserPermissions'
import type { TopupRecord } from '@/api/wallet/types'

export interface UseBillingHistoryOptions {
  initialPage?: number
  initialPageSize?: number
}

export function useBillingHistory(options: UseBillingHistoryOptions = {}) {
  const { initialPage = 1, initialPageSize = 10 } = options
  const { t } = useI18n()
  const { isAdmin } = useUserPermissions()

  const records = ref<TopupRecord[]>([])
  const total = ref(0)
  const page = ref(initialPage)
  const pageSize = ref(initialPageSize)
  const keyword = ref('')
  const loading = ref(false)
  const completing = ref(false)

  async function fetchBillingHistory(): Promise<void> {
    loading.value = true
    try {
      const response = isAdmin.value
        ? await getAllBillingHistory(page.value, pageSize.value, keyword.value)
        : await getUserBillingHistory(page.value, pageSize.value, keyword.value)
      records.value = response.list || []
      total.value = response.total || 0
    } catch {
      ElMessage.error(t('wallet.billing.loadFailed'))
      records.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  async function handleCompleteOrder(tradeNo: string): Promise<boolean> {
    if (!isAdmin.value) {
      ElMessage.error(t('wallet.billing.adminRequired'))
      return false
    }
    completing.value = true
    try {
      await completeOrder({ tradeNo })
      ElMessage.success(t('wallet.billing.completeSuccess'))
      await fetchBillingHistory()
      return true
    } catch {
      ElMessage.error(t('wallet.billing.completeFailed'))
      return false
    } finally {
      completing.value = false
    }
  }

  function handlePageChange(newPage: number): void {
    page.value = newPage
  }

  function handlePageSizeChange(newPageSize: number): void {
    pageSize.value = newPageSize
    page.value = 1
  }

  function handleSearch(newKeyword: string): void {
    keyword.value = newKeyword
    page.value = 1
  }

  watch([page, pageSize, keyword], fetchBillingHistory, { immediate: true })

  return {
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
    refresh: fetchBillingHistory,
  }
}
