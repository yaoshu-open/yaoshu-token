/**
 * 充值码兑换 composable。
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { redeemTopupCode } from '@/api/wallet'

export function useRedemption() {
  const { t } = useI18n()
  const redeeming = ref(false)

  async function redeemCode(code: string): Promise<boolean> {
    if (!code.trim()) {
      ElMessage.warning(t('wallet.redemption.required'))
      return false
    }
    redeeming.value = true
    try {
      const quota = await redeemTopupCode({ key: code.trim() })
      ElMessage.success(t('wallet.redemption.success', { quota }))
      return true
    } catch {
      ElMessage.error(t('wallet.redemption.failed'))
      return false
    } finally {
      redeeming.value = false
    }
  }

  return {
    redeeming,
    redeemCode,
  }
}
