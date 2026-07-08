/**
 * 令牌操作 composable。
 *
 * T-TK-02 批量复制格式选项（名称+密钥 / 仅密钥）。
 */
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { batchDeleteTokens, deleteToken, updateTokenStatus } from '@/api/token'
import type { Token } from '@/api/token/types'

export type CopyFormat = 'name_key' | 'key_only'

export function useTokenActions(refreshFn: () => Promise<void> | void) {
  const { t } = useI18n()
  const actionLoading = ref(false)

  async function toggleTokenStatus(token: Token): Promise<void> {
    actionLoading.value = true
    try {
      await updateTokenStatus(token.id, token.status === 1 ? 2 : 1)
      ElMessage.success(token.status === 1 ? t('token.actions.disableSuccess') : t('token.actions.enableSuccess'))
      await refreshFn()
    } catch {
      ElMessage.error(t('common.operationFailed'))
    } finally {
      actionLoading.value = false
    }
  }

  async function deleteTokenById(id: number, name: string): Promise<void> {
    try {
      await ElMessageBox.confirm(t('token.actions.deleteConfirm', { name }), t('common.warning'), { type: 'warning' })
      actionLoading.value = true
      await deleteToken(id)
      ElMessage.success(t('token.actions.deleteSuccess'))
      await refreshFn()
    } catch (e) {
      if (e !== 'cancel') ElMessage.error(t('common.operationFailed'))
    } finally {
      actionLoading.value = false
    }
  }

  async function batchDelete(ids: number[]): Promise<void> {
    try {
      await ElMessageBox.confirm(t('token.actions.batchDeleteConfirm', { count: ids.length }), t('common.warning'), { type: 'warning' })
      actionLoading.value = true
      await batchDeleteTokens(ids)
      ElMessage.success(t('token.actions.batchDeleteSuccess'))
      await refreshFn()
    } catch (e) {
      if (e !== 'cancel') ElMessage.error(t('common.operationFailed'))
    } finally {
      actionLoading.value = false
    }
  }

  // T-TK-02 批量复制格式选项
  function copyTokens(tokens: Token[], format: CopyFormat): string {
    return tokens
      .map((t) => format === 'name_key' ? `${t.name}\t${t.key}` : t.key)
      .join('\n')
  }

  return { actionLoading, toggleTokenStatus, deleteTokenById, batchDelete, copyTokens }
}
