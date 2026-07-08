/**
 * 邀请返利 composable。
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { getAffiliateCode, transferAffiliateQuota } from '@/api/wallet'

export function useAffiliate() {
  const { t } = useI18n()
  const affiliateLink = ref('')
  const loading = ref(false)
  const transferring = ref(false)

  async function fetchAffiliateLink(): Promise<void> {
    loading.value = true
    try {
      const code = await getAffiliateCode()
      affiliateLink.value = code || ''
    } catch {
      affiliateLink.value = ''
    } finally {
      loading.value = false
    }
  }

  async function transferQuota(quota: number): Promise<boolean> {
    if (quota <= 0) {
      ElMessage.warning(t('wallet.transfer.invalidAmount'))
      return false
    }
    transferring.value = true
    try {
      await transferAffiliateQuota({ quota })
      ElMessage.success(t('wallet.transfer.success'))
      return true
    } catch {
      ElMessage.error(t('wallet.transfer.failed'))
      return false
    } finally {
      transferring.value = false
    }
  }

  return {
    affiliateLink,
    loading,
    transferring,
    fetchAffiliateLink,
    transferQuota,
  }
}
