// 职责：Passkey 状态查询 / 注册（begin+finish）/ 删除 / 浏览器支持检测
// 被消费方：profile 页（M2-T1 UI 待办）/ SecureVerificationDialog（验证流程由 auth-secure api 处理）
// 登录流程的 Passkey 调用直接在 UserAuthForm 内联，不通过本 composable（语义不同）

import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  beginPasskeyRegistration,
  deletePasskey,
  finishPasskeyRegistration,
  getPasskeyStatus
} from '@/api/auth-passkey'
import type { PasskeyStatus } from '@/api/auth-passkey/types'
import {
  buildRegistrationResult,
  createCredential,
  isPasskeySupported,
  prepareCredentialCreationOptions
} from '@/utils/passkey'

interface UsePasskeyManagementOptions {
  onStatusChange?: (status: PasskeyStatus | null) => void
  // 是否跳过初始化时主动查询状态（用于非 profile 页场景，避免无 token 触发 401）
  autoFetch?: boolean
}

export function usePasskeyManagement(
  options: UsePasskeyManagementOptions = {}
) {
  const { autoFetch = true } = options
  const { t } = useI18n()

  const status = ref<PasskeyStatus | null>(null)
  const loading = ref<boolean>(autoFetch)
  const registering = ref<boolean>(false)
  const removing = ref<boolean>(false)
  const supported = ref<boolean>(false)

  async function fetchStatus(): Promise<void> {
    try {
      loading.value = true
      const res = await getPasskeyStatus()
      status.value = res
      options.onStatusChange?.(res)
    } catch {
      // 错误已由 request 拦截器弹 ElMessage，此处不重复
      status.value = null
    } finally {
      loading.value = false
    }
  }

  // autoFetch=true 时首次挂载主动拉取；false 时由消费方决定时机（如登录页内不拉取）
  onMounted(() => {
    if (autoFetch) {
      fetchStatus()
    }
    // Passkey 支持检测（与 fetchStatus 并发，不阻塞）
    isPasskeySupported()
      .then((s) => {
        supported.value = s
      })
      .catch(() => {
        supported.value = false
      })
  })

  // 注册 Passkey：begin → navigator.credentials.create → finish
  async function register(): Promise<boolean> {
    if (!supported.value) {
      ElMessage.error(t('auth.passkey.notSupported'))
      return false
    }
    if (!navigator?.credentials) {
      ElMessage.error(t('auth.passkey.envNotSupported'))
      return false
    }

    registering.value = true
    try {
      const beginResponse = await beginPasskeyRegistration()

      const publicKey = prepareCredentialCreationOptions(beginResponse)
      const credential = (await createCredential(
        publicKey
      )) as PublicKeyCredential | null

      if (!credential) {
        ElMessage.info(t('auth.passkey.cancelled'))
        return false
      }

      const attestation = buildRegistrationResult(credential)
      if (!attestation) {
        ElMessage.error(t('auth.passkey.invalidResponse'))
        return false
      }

      await finishPasskeyRegistration(attestation)
      ElMessage.success(t('auth.passkey.registered'))
      await fetchStatus()
      return true
    } catch (error: unknown) {
      if (error instanceof DOMException && error.name === 'NotAllowedError') {
        ElMessage.info(t('auth.passkey.cancelled'))
        return false
      }
      console.error('[Passkey] Registration error', error)
      const msg =
        error instanceof Error ? error.message : t('auth.passkey.registerFailed')
      ElMessage.error(msg)
      return false
    } finally {
      registering.value = false
    }
  }

  // 删除所有 Passkey
  async function remove(): Promise<boolean> {
    removing.value = true
    try {
      await deletePasskey()
      ElMessage.success(t('auth.passkey.removed'))
      await fetchStatus()
      return true
    } catch (error) {
      console.error('[Passkey] Removal error', error)
      ElMessage.error(t('auth.passkey.removeFailed'))
      return false
    } finally {
      removing.value = false
    }
  }

  const enabled = computed(() => Boolean(status.value?.enabled))
  const lastUsed = computed(() => status.value?.lastUsedAt ?? null)

  return {
    status,
    loading,
    registering,
    removing,
    supported,
    enabled,
    lastUsed,
    fetchStatus,
    register,
    remove
  }
}
