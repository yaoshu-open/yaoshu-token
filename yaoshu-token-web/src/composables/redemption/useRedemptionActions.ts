/**
 * 兑换码操作 composable。
 */
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  clearInvalidRedemptions,
  createRedemption,
  deleteRedemption,
  updateRedemption,
} from '@/api/redemption'
import type {
  CreateRedemptionPayload,
  Redemption,
  UpdateRedemptionPayload,
} from '@/api/redemption/types'

export function useRedemptionActions(refreshFn: () => Promise<void> | void) {
  const { t } = useI18n()
  const actionLoading = ref(false)

  async function handleCreate(payload: CreateRedemptionPayload): Promise<boolean> {
    actionLoading.value = true
    try {
      await createRedemption(payload)
      ElMessage.success(t('redemption.actions.createSuccess'))
      await refreshFn()
      return true
    } catch {
      ElMessage.error(t('redemption.actions.createFailed'))
      return false
    } finally {
      actionLoading.value = false
    }
  }

  async function handleUpdate(payload: UpdateRedemptionPayload): Promise<boolean> {
    actionLoading.value = true
    try {
      await updateRedemption(payload)
      ElMessage.success(t('redemption.actions.updateSuccess'))
      await refreshFn()
      return true
    } catch {
      ElMessage.error(t('redemption.actions.updateFailed'))
      return false
    } finally {
      actionLoading.value = false
    }
  }

  async function handleDelete(id: number, name: string): Promise<void> {
    try {
      await ElMessageBox.confirm(
        t('redemption.confirm.delete', { name }),
        t('common.warning'),
        { type: 'warning' }
      )
      actionLoading.value = true
      await deleteRedemption(id)
      ElMessage.success(t('redemption.actions.deleteSuccess'))
      await refreshFn()
    } catch (e) {
      if (e !== 'cancel') ElMessage.error(t('redemption.actions.deleteFailed'))
    } finally {
      actionLoading.value = false
    }
  }

  async function handleClearInvalid(): Promise<void> {
    try {
      await ElMessageBox.confirm(
        t('redemption.confirm.clearInvalid'),
        t('common.warning'),
        { type: 'warning' }
      )
      actionLoading.value = true
      await clearInvalidRedemptions()
      ElMessage.success(t('redemption.actions.clearInvalidSuccess'))
      await refreshFn()
    } catch (e) {
      if (e !== 'cancel') ElMessage.error(t('redemption.actions.clearInvalidFailed'))
    } finally {
      actionLoading.value = false
    }
  }

  async function handleToggleStatus(row: Redemption): Promise<void> {
    // 兑换码状态：1未使用 → 禁用（无法直接改回未使用，仅逻辑禁用）
    // 后端 status_only 模式：仅在未使用时可禁用
    if (row.status === 2) {
      ElMessage.warning(t('redemption.actions.alreadyUsed'))
      return
    }
    actionLoading.value = true
    try {
      const payload: UpdateRedemptionPayload = {
        id: row.id,
        statusOnly: true,
        status: 2,
      }
      await updateRedemption(payload)
      ElMessage.success(t('redemption.actions.disableSuccess'))
      await refreshFn()
    } catch {
      ElMessage.error(t('redemption.actions.disableFailed'))
    } finally {
      actionLoading.value = false
    }
  }

  async function handleBatchDelete(ids: number[]): Promise<void> {
    if (ids.length === 0) return
    try {
      await ElMessageBox.confirm(
        t('redemption.confirm.batchDelete', { count: ids.length }),
        t('common.warning'),
        { type: 'warning' }
      )
      actionLoading.value = true
      // API 无批量删除接口，逐条删除
      for (const id of ids) {
        await deleteRedemption(id)
      }
      ElMessage.success(t('redemption.actions.batchDeleteSuccess'))
      await refreshFn()
    } catch (e) {
      if (e !== 'cancel') ElMessage.error(t('redemption.actions.batchDeleteFailed'))
    } finally {
      actionLoading.value = false
    }
  }

  return {
    actionLoading,
    handleCreate,
    handleUpdate,
    handleDelete,
    handleBatchDelete,
    handleClearInvalid,
    handleToggleStatus,
  }
}
