// 职责：profile 数据 fetch/refresh/updateProfile/updateSettings
// 刷新策略：PUT/DELETE 成功后静默 refreshProfile（无 loading 态）

import { ref, onMounted, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  getUserProfile,
  updateUserProfile,
  updateUserSettings,
} from '@/api/profile'
import type {
  UpdateUserRequest,
  UpdateUserSettingsRequest,
  UserProfile,
} from '@/api/profile/types'

export function useProfile() {
  const { t } = useI18n()
  const profile = ref<UserProfile | null>(null)
  const loading = ref(true)
  const updating = ref(false)

  async function fetchProfile(silent = false): Promise<void> {
    try {
      if (!silent) loading.value = true
      const data = await getUserProfile()
      profile.value = data
    } catch (error) {
      console.error('[Profile] Failed to fetch profile:', error)
      if (!silent) {
        // 错误已由 request 拦截器弹 ElMessage，此处不重复
      }
    } finally {
      if (!silent) loading.value = false
    }
  }

  async function refreshProfile(): Promise<void> {
    await fetchProfile(true)
  }

  async function updateProfile(data: UpdateUserRequest): Promise<boolean> {
    try {
      updating.value = true
      await updateUserProfile(data)
      ElMessage.success(t('profile.updateSuccess'))
      await refreshProfile()
      return true
    } catch {
      return false
    } finally {
      updating.value = false
    }
  }

  async function updateSettings(
    data: UpdateUserSettingsRequest
  ): Promise<boolean> {
    try {
      updating.value = true
      await updateUserSettings(data)
      ElMessage.success(t('profile.settingsUpdateSuccess'))
      await refreshProfile()
      return true
    } catch {
      return false
    } finally {
      updating.value = false
    }
  }

  onMounted(() => {
    fetchProfile()
  })

  return {
    profile: profile as Ref<UserProfile | null>,
    loading,
    updating,
    fetchProfile,
    refreshProfile,
    updateProfile,
    updateSettings,
  }
}
