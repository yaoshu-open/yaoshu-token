// 职责：自定义 OAuth 绑定列表查询 + 解绑

import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { getSelfOAuthBindings, unbindCustomOAuth } from '@/api/profile'
import type { CustomOAuthBinding } from '@/api/profile/types'

export function useOAuthBindings() {
  const { t } = useI18n()
  const bindings = ref<CustomOAuthBinding[]>([])
  const loading = ref(false)
  const unbinding = ref(false)

  async function fetchBindings(): Promise<void> {
    loading.value = true
    try {
      bindings.value = await getSelfOAuthBindings()
    } catch {
      bindings.value = []
    } finally {
      loading.value = false
    }
  }

  async function unbind(binding: CustomOAuthBinding): Promise<boolean> {
    unbinding.value = true
    try {
      await unbindCustomOAuth(binding.providerId)
      ElMessage.success(t('profile.oauth.unbound', { provider: binding.providerName }))
      await fetchBindings()
      return true
    } catch {
      return false
    } finally {
      unbinding.value = false
    }
  }

  return {
    bindings,
    loading,
    unbinding,
    fetchBindings,
    unbind,
  }
}
