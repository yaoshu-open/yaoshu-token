/**
 * 渠道操作 composable：测试/余额/复制/删除等 API 调用 + 加载态 + 错误处理。
 *
 * 职责：封装单渠道和批量操作的 API 调用，管理加载态，通过 onSuccess 回调通知调用方刷新。
 * 不负责：测试对话框内部的多模型批量测试逻辑（ChannelTestDialog 自管理）。
 */
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  batchDeleteChannels,
  batchSetChannelTag,
  copyChannel,
  deleteChannel,
  deleteDisabledChannels,
  fixChannelAbilities,
  testAllChannels,
  testChannel as testChannelApi,
  updateAllChannelsBalance,
  updateChannelBalance
} from '@/api/channel'
import type {
  ChannelBalanceResponse,
  ChannelTestResponse,
  CopyChannelParams
} from '@/api/channel/types'

/** 测试结果（单模型）。失败时 error 来自拦截器 reject 的 Error(msg) */
export interface ChannelTestResult {
  success: boolean
  responseTime?: number
  error?: string
}

/** 余额查询结果 */
export interface ChannelBalanceResult {
  success: boolean
  balance?: number
  message?: string
}

/** useChannelActions 选项 */
export function useChannelActions(options?: {
  onSuccess?: () => void
}) {
  const { t } = useI18n()
  const onSuccess = options?.onSuccess

  // 加载态（按操作类型区分）
  const actionLoading = ref<Record<string, boolean>>({})

  function setLoading(key: string, value: boolean): void {
    actionLoading.value[key] = value
  }

  function isLoading(key: string): boolean {
    return actionLoading.value[key] ?? false
  }

  // ============================================================================
  // 单渠道操作
  // ============================================================================

  /**
   * 测试单个模型连通性。
   * ChannelTestDialog 内部直接调用 testChannel API，本方法供行操作快捷测试使用。
   */
  async function testChannel(
    id: number,
    params?: { model?: string; endpoint_type?: string; stream?: boolean }
  ): Promise<ChannelTestResult> {
    setLoading('test', true)
    try {
      const res: ChannelTestResponse = await testChannelApi(id, params)
      // 请求未抛异常即为成功（拦截器 flag=true 才返回 res）
      return {
        success: true,
        responseTime: res.time
      }
    } catch (e) {
      return {
        success: false,
        error: (e as Error)?.message || t('channel.dialog.test.failed')
      }
    } finally {
      setLoading('test', false)
    }
  }

  /** 更新渠道余额 */
  async function updateBalance(id: number): Promise<ChannelBalanceResult> {
    setLoading('balance', true)
    try {
      const res: ChannelBalanceResponse = await updateChannelBalance(id)
      // 请求未抛异常即为成功
      ElMessage.success(t('channel.dialog.balance.updateSuccess'))
      onSuccess?.()
      return { success: true, balance: res.balance }
    } catch (e) {
      ElMessage.error(t('channel.dialog.balance.updateFailed'))
      return { success: false, message: (e as Error)?.message }
    } finally {
      setLoading('balance', false)
    }
  }

  /** 复制渠道 */
  async function copyChannelAction(
    id: number,
    params?: CopyChannelParams
  ): Promise<boolean> {
    setLoading('copy', true)
    try {
      await copyChannel(id, params)
      ElMessage.success(t('channel.dialog.copy.success'))
      onSuccess?.()
      return true
    } catch {
      ElMessage.error(t('channel.dialog.copy.failed'))
      return false
    } finally {
      setLoading('copy', false)
    }
  }

  /** 删除渠道（带二次确认） */
  async function deleteChannelAction(
    id: number,
    displayName: string
  ): Promise<boolean> {
    try {
      await ElMessageBox.confirm(
        t('channel.actions.deleteConfirm', { name: displayName }),
        t('common.warning'),
        { type: 'warning' }
      )
    } catch {
      return false
    }
    setLoading('delete', true)
    try {
      await deleteChannel(id)
      ElMessage.success(t('channel.actions.deleteSuccess'))
      onSuccess?.()
      return true
    } catch {
      ElMessage.error(t('common.operationFailed'))
      return false
    } finally {
      setLoading('delete', false)
    }
  }

  // ============================================================================
  // 批量操作
  // ============================================================================

  /** 批量删除（带二次确认） */
  async function batchDelete(ids: number[]): Promise<boolean> {
    if (!ids.length) return false
    try {
      await ElMessageBox.confirm(
        t('channel.bulk.deleteConfirm', { count: ids.length }),
        t('common.warning'),
        { type: 'warning' }
      )
    } catch {
      return false
    }
    setLoading('batchDelete', true)
    try {
      await batchDeleteChannels({ ids })
      ElMessage.success(t('channel.bulk.deleteSuccess'))
      onSuccess?.()
      return true
    } catch {
      ElMessage.error(t('common.operationFailed'))
      return false
    } finally {
      setLoading('batchDelete', false)
    }
  }

  /** 批量设置标签 */
  async function batchSetTag(ids: number[], tag: string | null): Promise<boolean> {
    if (!ids.length) return false
    setLoading('batchSetTag', true)
    try {
      await batchSetChannelTag({ ids, tag })
      ElMessage.success(t('channel.bulk.setTagSuccess'))
      onSuccess?.()
      return true
    } catch {
      ElMessage.error(t('common.operationFailed'))
      return false
    } finally {
      setLoading('batchSetTag', false)
    }
  }

  /** 测试所有渠道（同步等待，可能耗时 1-5 分钟） */
  async function testAll(): Promise<boolean> {
    setLoading('testAll', true)
    try {
      const res = await testAllChannels()
      // 防御性处理：后端可能返回空 {} 或 {total, completed}
      if (res && typeof res.total === 'number') {
        ElMessage.success(
          t('channel.actions.testAllResult', { completed: res.completed, total: res.total })
        )
      } else {
        ElMessage.success(t('channel.actions.testAllSuccess'))
      }
      onSuccess?.()
      return true
    } catch {
      ElMessage.error(t('common.operationFailed'))
      return false
    } finally {
      setLoading('testAll', false)
    }
  }

  /** 更新所有渠道余额 */
  async function updateAllBalance(): Promise<boolean> {
    setLoading('updateAllBalance', true)
    try {
      await updateAllChannelsBalance()
      ElMessage.success(t('channel.actions.updateAllBalanceSuccess'))
      onSuccess?.()
      return true
    } catch {
      ElMessage.error(t('common.operationFailed'))
      return false
    } finally {
      setLoading('updateAllBalance', false)
    }
  }

  /** 修复渠道能力 */
  async function fixAbilities(): Promise<boolean> {
    setLoading('fixAbilities', true)
    try {
      await fixChannelAbilities()
      ElMessage.success(t('channel.actions.fixAbilitiesSuccess'))
      return true
    } catch {
      ElMessage.error(t('common.operationFailed'))
      return false
    } finally {
      setLoading('fixAbilities', false)
    }
  }

  /** 删除所有禁用渠道（带二次确认） */
  async function deleteDisabled(): Promise<boolean> {
    try {
      await ElMessageBox.confirm(
        t('channel.actions.deleteDisabledConfirm'),
        t('common.warning'),
        { type: 'warning' }
      )
    } catch {
      return false
    }
    setLoading('deleteDisabled', true)
    try {
      const res = await deleteDisabledChannels()
      ElMessage.success(
        t('channel.actions.deleteDisabledSuccess', { count: res ?? 0 })
      )
      onSuccess?.()
      return true
    } catch {
      ElMessage.error(t('common.operationFailed'))
      return false
    } finally {
      setLoading('deleteDisabled', false)
    }
  }

  return {
    actionLoading,
    isLoading,
    // 单渠道
    testChannel,
    updateBalance,
    copyChannelAction,
    deleteChannelAction,
    // 批量
    batchDelete,
    batchSetTag,
    testAll,
    updateAllBalance,
    fixAbilities,
    deleteDisabled
  }
}
