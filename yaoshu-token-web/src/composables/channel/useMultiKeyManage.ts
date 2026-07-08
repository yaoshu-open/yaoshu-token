/**
 * 多密钥管理 composable：分页加载/状态筛选/统计/操作执行。
 *
 * 职责：封装多密钥 API 调用 + 分页/统计状态管理，对话框组件消费本 composable。
 */
import { ref, readonly } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  deleteDisabledMultiKeys,
  deleteMultiKey,
  disableAllMultiKeys,
  disableMultiKey,
  enableAllMultiKeys,
  enableMultiKey,
  getMultiKeyStatus
} from '@/api/channel'
import type {
  KeyStatus,
  MultiKeyConfirmAction
} from '@/api/channel/types'

export function useMultiKeyManage() {
  const { t } = useI18n()

  // 数据状态
  const keys = ref<KeyStatus[]>([])
  const loading = ref(false)
  const currentPage = ref(1)
  const pageSize = ref(10)
  const total = ref(0)
  const totalPages = ref(0)
  const enabledCount = ref(0)
  const manualDisabledCount = ref(0)
  const autoDisabledCount = ref(0)

  // UI 状态
  const statusFilter = ref<number | null>(null)
  const performingAction = ref(false)
  const confirmAction = ref<MultiKeyConfirmAction | null>(null)

  /** 加载密钥状态（分页） */
  async function loadKeys(
    channelId: number,
    page = currentPage.value,
    size = pageSize.value,
    status = statusFilter.value
  ): Promise<void> {
    if (!channelId) return
    loading.value = true
    try {
      const res = await getMultiKeyStatus(channelId, page, size, status ?? undefined)
      keys.value = res.keys || []
      total.value = res.total || 0
      currentPage.value = res.page || 1
      pageSize.value = res.pageSize || 10
      totalPages.value = res.totalPages || 0
      enabledCount.value = res.enabledCount || 0
      manualDisabledCount.value = res.manualDisabledCount || 0
      autoDisabledCount.value = res.autoDisabledCount || 0
    } catch (e) {
      ElMessage.error(
        (e as Error)?.message || t('channel.multiKey.loadFailed')
      )
    } finally {
      loading.value = false
    }
  }

  /** 切换状态筛选（重置到第 1 页） */
  async function handleStatusFilterChange(
    channelId: number,
    value: number | null
  ): Promise<void> {
    statusFilter.value = value
    currentPage.value = 1
    await loadKeys(channelId, 1, pageSize.value, value)
  }

  /** 切换页码 */
  async function handlePageChange(
    channelId: number,
    newPage: number
  ): Promise<void> {
    currentPage.value = newPage
    await loadKeys(channelId, newPage)
  }

  /** 执行确认后的操作 */
  async function performAction(channelId: number): Promise<boolean> {
    const action = confirmAction.value
    if (!action || !channelId) return false

    performingAction.value = true
    try {
      const { type, keyIndex } = action as {
        type: string
        keyIndex?: number
      }

      if (type === 'enable' && keyIndex !== undefined) {
        await enableMultiKey(channelId, keyIndex)
      } else if (type === 'disable' && keyIndex !== undefined) {
        await disableMultiKey(channelId, keyIndex)
      } else if (type === 'delete' && keyIndex !== undefined) {
        await deleteMultiKey(channelId, keyIndex)
      } else if (type === 'enable-all') {
        await enableAllMultiKeys(channelId)
      } else if (type === 'disable-all') {
        await disableAllMultiKeys(channelId)
      } else if (type === 'delete-disabled') {
        await deleteDisabledMultiKeys(channelId)
      } else {
        return false
      }

      ElMessage.success(t('common.operationSuccess'))

      // 批量操作重置到第 1 页；单密钥操作保持当前页
      const isBulkAction =
        type.includes('all') || type === 'delete-disabled'
      if (isBulkAction) {
        currentPage.value = 1
        await loadKeys(channelId, 1, pageSize.value)
      } else {
        // 删除后若当前页越界，回退一页
        if (keys.value.length <= 1 && currentPage.value > 1) {
          currentPage.value = currentPage.value - 1
        }
        await loadKeys(channelId, currentPage.value)
      }
      return true
    } catch (e) {
      ElMessage.error(
        (e as Error)?.message || t('common.operationFailed')
      )
      return false
    } finally {
      performingAction.value = false
      confirmAction.value = null
    }
  }

  return {
    // 数据状态
    keys: readonly(keys),
    loading: readonly(loading),
    currentPage: readonly(currentPage),
    pageSize: readonly(pageSize),
    total: readonly(total),
    totalPages: readonly(totalPages),
    enabledCount: readonly(enabledCount),
    manualDisabledCount: readonly(manualDisabledCount),
    autoDisabledCount: readonly(autoDisabledCount),
    // UI 状态
    statusFilter: readonly(statusFilter),
    performingAction: readonly(performingAction),
    confirmAction,
    // 方法
    loadKeys,
    handleStatusFilterChange,
    handlePageChange,
    performAction
  }
}
