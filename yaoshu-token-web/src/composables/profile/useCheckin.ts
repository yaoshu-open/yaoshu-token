// 职责：月度签到状态查询 + 执行签到

import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { getCheckinStatus, performCheckin } from '@/api/profile'
import { BusinessError } from '@/utils/request'
import type { CheckinStatusResponse } from '@/api/profile/types'

function getCurrentMonth(): string {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  return `${year}-${month}`
}

export function useCheckin() {
  const { t } = useI18n()
  const status = ref<CheckinStatusResponse | null>(null)
  const loading = ref(false)
  const checking = ref(false)
  const featureDisabled = ref(false)
  const currentMonth = ref(getCurrentMonth())

  async function fetchStatus(month?: string): Promise<void> {
    loading.value = true
    try {
      status.value = await getCheckinStatus(month || currentMonth.value)
      featureDisabled.value = status.value?.enabled === false
    } catch (err) {
      status.value = null
      // 后端在功能未启用时返回 flag:false 业务错误，由 _silent 抑制 toast，
      // 此处识别该业务状态以便前端渲染「功能未启用」空态而非日历。
      if (err instanceof BusinessError) {
        featureDisabled.value = true
      } else {
        featureDisabled.value = false
      }
    } finally {
      loading.value = false
    }
  }

  async function checkin(turnstileToken?: string): Promise<boolean> {
    checking.value = true
    try {
      const res = await performCheckin(turnstileToken)
      ElMessage.success(t('profile.checkin.success', { quota: res.quotaAwarded }))
      await fetchStatus()
      return true
    } catch {
      return false
    } finally {
      checking.value = false
    }
  }

  async function changeMonth(month: string): Promise<void> {
    currentMonth.value = month
    await fetchStatus(month)
  }

  return {
    status,
    loading,
    checking,
    featureDisabled,
    currentMonth,
    fetchStatus,
    checkin,
    changeMonth,
  }
}
