/**
 * 模型操作 composable。
 *
 * 职责：模型 CRUD 操作 + 批量操作 + 供应商管理。
 * 不负责：列表数据加载（useModelsData）/ 表单编辑（useModelMutateForm）。
 */
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  deleteModel,
  getMissingModels,
  updateModel,
} from '@/api/model'
import type { Model } from '@/api/model/types'

export function useModelActions(refreshFn: () => Promise<void> | void) {
  const { t } = useI18n()
  const actionLoading = ref(false)

  // ============================================================================
  // 单模型操作
  // ============================================================================

  async function toggleModelStatus(model: Model): Promise<void> {
    actionLoading.value = true
    try {
      await updateModel({
        id: model.id,
        status: model.status === 1 ? 0 : 1,
      })
      ElMessage.success(model.status === 1 ? t('model.actions.disableSuccess') : t('model.actions.enableSuccess'))
      await refreshFn()
    } catch {
      ElMessage.error(t('common.operationFailed'))
    } finally {
      actionLoading.value = false
    }
  }

  async function deleteModelById(id: number, name: string): Promise<void> {
    try {
      await ElMessageBox.confirm(
        t('model.actions.deleteConfirm', { name }),
        t('common.warning'),
        { type: 'warning' }
      )
      actionLoading.value = true
      await deleteModel(id)
      ElMessage.success(t('model.actions.deleteSuccess'))
      await refreshFn()
    } catch (e) {
      if (e !== 'cancel') ElMessage.error(t('common.operationFailed'))
    } finally {
      actionLoading.value = false
    }
  }

  // ============================================================================
  // 批量操作
  // ============================================================================

  async function batchDeleteModels(ids: number[]): Promise<void> {
    try {
      await ElMessageBox.confirm(
        t('model.actions.batchDeleteConfirm', { count: ids.length }),
        t('common.warning'),
        { type: 'warning' }
      )
      actionLoading.value = true
      // 后端暂无批量删除端点，逐个删除
      await Promise.all(ids.map((id) => deleteModel(id)))
      ElMessage.success(t('model.actions.batchDeleteSuccess'))
      await refreshFn()
    } catch (e) {
      if (e !== 'cancel') ElMessage.error(t('common.operationFailed'))
    } finally {
      actionLoading.value = false
    }
  }

  async function batchToggleStatus(ids: number[], models: Model[], enable: boolean): Promise<void> {
    actionLoading.value = true
    try {
      // 逐个更新状态
      await Promise.all(
        ids.map((id) => {
          const model = models.find((m) => m.id === id)
          if (model && (enable ? model.status !== 1 : model.status === 1)) {
            return updateModel({ id, status: enable ? 1 : 0 })
          }
          return Promise.resolve()
        })
      )
      ElMessage.success(enable ? t('model.actions.batchEnableSuccess') : t('model.actions.batchDisableSuccess'))
      await refreshFn()
    } catch {
      ElMessage.error(t('common.operationFailed'))
    } finally {
      actionLoading.value = false
    }
  }

  // ============================================================================
  // 工具操作
  // ============================================================================

  async function scanMissingModels(): Promise<string[]> {
    try {
      return await getMissingModels()
    } catch {
      ElMessage.error(t('model.actions.scanMissingFailed'))
      return []
    }
  }

  return {
    actionLoading,
    toggleModelStatus,
    deleteModelById,
    batchDeleteModels,
    batchToggleStatus,
    scanMissingModels,
  }
}
