/**
 * 部署通用操作 composable：delete / rename（按需）。
 *
 * 范围：本会话仅消费 refresh + delete（list view 走 ElMessageBox 二次确认 + refresh）。
 * rename 接口已暴露但 UI 不接（按需扩展，避免跨模块污染）。
 */
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { deleteDeployment, getDeployment, renameDeployment } from '@/api/deployment'
import type { DeploymentDetails } from '@/api/deployment/types'

export function useDeploymentActions() {
  const { t } = useI18n()
  const operationLoading = ref<Record<string, boolean>>({})

  function isOpLoading(op: string, id: string | number): boolean {
    return operationLoading.value[`${op}_${String(id)}`] ?? false
  }

  function setOpLoading(op: string, id: string | number, value: boolean): void {
    operationLoading.value[`${op}_${String(id)}`] = value
  }

  async function refreshOne(id: string | number): Promise<DeploymentDetails | null> {
    setOpLoading('refresh', id, true)
    try {
      return await getDeployment(id)
    } catch (e) {
      ElMessage.error(t('deployment.actions.refreshFailed') + ': ' + ((e as Error)?.message ?? ''))
      return null
    } finally {
      setOpLoading('refresh', id, false)
    }
  }

  async function deleteOne(id: string | number, displayName: string): Promise<boolean> {
    try {
      await ElMessageBox.confirm(
        t('deployment.actions.deleteConfirm', { name: displayName }),
        t('deployment.actions.deleteTitle'),
        {
          type: 'warning',
          confirmButtonText: t('common.confirm'),
          cancelButtonText: t('common.cancel')
        }
      )
    } catch {
      return false
    }
    setOpLoading('delete', id, true)
    try {
      await deleteDeployment(id)
      ElMessage.success(t('deployment.actions.deleteSuccess'))
      return true
    } catch (e) {
      ElMessage.error(t('deployment.actions.deleteFailed') + ': ' + ((e as Error)?.message ?? ''))
      return false
    } finally {
      setOpLoading('delete', id, false)
    }
  }

  async function renameOne(id: string | number, newName: string): Promise<DeploymentDetails | null> {
    setOpLoading('rename', id, true)
    try {
      const res = await renameDeployment(id, newName)
      ElMessage.success(t('deployment.actions.renameSuccess'))
      return res
    } catch (e) {
      ElMessage.error(t('deployment.actions.renameFailed') + ': ' + ((e as Error)?.message ?? ''))
      return null
    } finally {
      setOpLoading('rename', id, false)
    }
  }

  return {
    isOpLoading,
    refreshOne,
    deleteOne,
    renameOne
  }
}
