/**
 * 用户操作 composable。
 */
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { deleteUser, manageUser, manageUserQuota } from '@/api/user'
import type { ManageUserAction, User } from '@/api/user/types'

export function useUserActions(refreshFn: () => Promise<void> | void) {
  const { t } = useI18n()
  const actionLoading = ref(false)

  async function handleManageUser(id: number, action: ManageUserAction, name?: string): Promise<void> {
    try {
      if (action === 'delete') {
        await ElMessageBox.confirm(t('user.actions.deleteConfirm', { name: name ?? id }), t('common.warning'), { type: 'warning' })
      } else if (action === 'promote' || action === 'demote') {
        await ElMessageBox.confirm(
          action === 'promote'
            ? t('user.actions.promoteConfirm', { name: name ?? id })
            : t('user.actions.demoteConfirm', { name: name ?? id }),
          t('common.warning'),
          { type: 'warning' }
        )
      }
      actionLoading.value = true
      await manageUser(id, action)
      ElMessage.success(t('user.actions.manageSuccess', { action }))
      await refreshFn()
    } catch (e) {
      if (e !== 'cancel') ElMessage.error(t('common.operationFailed'))
    } finally {
      actionLoading.value = false
    }
  }

  async function deleteUserId(id: number, name: string): Promise<void> {
    try {
      await ElMessageBox.confirm(t('user.actions.deleteConfirm', { name }), t('common.warning'), { type: 'warning' })
      actionLoading.value = true
      await deleteUser(id)
      ElMessage.success(t('user.actions.deleteSuccess'))
      await refreshFn()
    } catch (e) {
      if (e !== 'cancel') ElMessage.error(t('user.actions.deleteFailed'))
    } finally {
      actionLoading.value = false
    }
  }

  async function batchDelete(ids: number[]): Promise<void> {
    try {
      await ElMessageBox.confirm(t('user.actions.batchDeleteConfirm', { count: ids.length }), t('common.warning'), { type: 'warning' })
      actionLoading.value = true
      await Promise.all(ids.map((id) => deleteUser(id)))
      ElMessage.success(t('user.actions.batchDeleteSuccess'))
      await refreshFn()
    } catch (e) {
      if (e !== 'cancel') ElMessage.error(t('user.actions.batchDeleteFailed'))
    } finally {
      actionLoading.value = false
    }
  }

  // T-US-03 批量操作补齐
  async function batchToggleStatus(ids: number[], users: User[], enable: boolean): Promise<void> {
    actionLoading.value = true
    try {
      await Promise.all(
        ids.map((id) => {
          const user = users.find((u) => u.id === id)
          if (user && (enable ? user.status !== 1 : user.status === 1)) {
            return manageUser(id, enable ? 'enable' : 'disable')
          }
          return Promise.resolve()
        })
      )
      ElMessage.success(enable ? t('user.actions.batchEnableSuccess') : t('user.actions.batchDisableSuccess'))
      await refreshFn()
    } catch {
      ElMessage.error(t('common.operationFailed'))
    } finally {
      actionLoading.value = false
    }
  }

  async function adjustQuota(userId: number, quota: number, action: 'add' | 'subtract' | 'override'): Promise<void> {
    actionLoading.value = true
    try {
      await manageUserQuota({ id: userId, action: 'add_quota', value: quota, mode: action })
      ElMessage.success(t('user.actions.quotaAdjusted'))
      await refreshFn()
    } catch {
      ElMessage.error(t('user.actions.quotaAdjustFailed'))
    } finally {
      actionLoading.value = false
    }
  }

  return {
    actionLoading,
    handleManageUser,
    deleteUserId,
    batchDelete,
    batchToggleStatus,
    adjustQuota,
  }
}
