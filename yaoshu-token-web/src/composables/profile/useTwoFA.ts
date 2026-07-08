// 职责：2FA 状态查询 / setup（secret+verify）/ disable / backup codes

import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  disable2FA,
  get2FABackupCodes,
  get2FASecret,
  get2FAStatus,
  verify2FA,
} from '@/api/profile'
import type { TwoFASetupData, TwoFAStatus } from '@/api/profile/types'

export function useTwoFA(enabled = true) {
  const { t } = useI18n()
  const status = ref<TwoFAStatus | null>(null)
  const loading = ref(false)
  const setupData = ref<TwoFASetupData | null>(null)
  const setupLoading = ref(false)
  const verifying = ref(false)
  const disabling = ref(false)
  const backupCodes = ref<string[]>([])
  const backupLoading = ref(false)

  async function fetchStatus(): Promise<void> {
    if (!enabled) return
    loading.value = true
    try {
      status.value = await get2FAStatus()
    } catch {
      status.value = null
    } finally {
      loading.value = false
    }
  }

  async function startSetup(): Promise<boolean> {
    setupLoading.value = true
    try {
      setupData.value = await get2FASecret()
      return true
    } catch {
      return false
    } finally {
      setupLoading.value = false
    }
  }

  async function verify(code: string): Promise<boolean> {
    if (!setupData.value) return false
    verifying.value = true
    try {
      await verify2FA({ code, secret: setupData.value.secret })
      ElMessage.success(t('profile.twoFAEnableSuccess'))
      await fetchStatus()
      return true
    } catch {
      return false
    } finally {
      verifying.value = false
    }
  }

  async function disable(password: string): Promise<boolean> {
    disabling.value = true
    try {
      await disable2FA({ password })
      ElMessage.success(t('profile.twoFADisableSuccess'))
      await fetchStatus()
      return true
    } catch {
      return false
    } finally {
      disabling.value = false
    }
  }

  async function fetchBackupCodes(): Promise<void> {
    backupLoading.value = true
    try {
      backupCodes.value = await get2FABackupCodes()
    } catch {
      backupCodes.value = []
    } finally {
      backupLoading.value = false
    }
  }

  return {
    status,
    loading,
    setupData,
    setupLoading,
    verifying,
    disabling,
    backupCodes,
    backupLoading,
    fetchStatus,
    startSetup,
    verify,
    disable,
    fetchBackupCodes,
  }
}
