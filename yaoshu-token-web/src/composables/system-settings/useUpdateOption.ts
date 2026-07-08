/**
 * 系统配置更新 composable。
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { updateSystemOption, batchUpdateSystemOptions } from '@/api/system-option'
import type { UpdateOptionRequest } from '@/api/system-option/types'

export function useUpdateOption() {
  const saving = ref(false)
  const { t } = useI18n()

  async function save(payload: UpdateOptionRequest): Promise<boolean> {
    saving.value = true
    try {
      await updateSystemOption(payload)
      ElMessage.success(t('common.saveSuccess'))
      return true
    } catch (e) {
      ElMessage.error(e instanceof Error ? e.message : t('common.saveFailed'))
      return false
    } finally {
      saving.value = false
    }
  }

  async function saveBatch(payload: UpdateOptionRequest[]): Promise<boolean> {
    saving.value = true
    try {
      await batchUpdateSystemOptions(payload)
      ElMessage.success(t('common.saveSuccess'))
      return true
    } catch (e) {
      ElMessage.error(e instanceof Error ? e.message : t('common.saveFailed'))
      return false
    } finally {
      saving.value = false
    }
  }

  return { saving, save, saveBatch }
}
