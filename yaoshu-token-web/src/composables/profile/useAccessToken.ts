// 职责：生成/重生成系统访问令牌

import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { generateAccessToken } from '@/api/profile'

export function useAccessToken() {
  const { t } = useI18n()
  const token = ref('')
  const loading = ref(false)

  async function generate(): Promise<string> {
    loading.value = true
    try {
      const newToken = await generateAccessToken()
      token.value = newToken
      ElMessage.success(t('profile.accessTokenGenerateSuccess'))
      return newToken
    } catch {
      return ''
    } finally {
      loading.value = false
    }
  }

  return {
    token,
    loading,
    generate,
  }
}
