// 职责：敏感操作拦截 + 启动二次验证 dialog + 执行验证后回放原 API 调用
// 被消费方：M2 业务模块的敏感操作（如删除/转账/解绑 OAuth）触发点
//
// 核心模式：withVerification(apiCall) — 高阶包装，捕获后端「verification required」错误自动启动 dialog；
// 用户通过 dialog 完成验证后，verify() + 原始 apiCall() 顺序回放

import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { checkVerificationMethods, verify } from '@/api/auth-secure'
import type {
  SecureVerificationState,
  StartVerificationOptions,
  UseSecureVerificationOptions,
  VerificationMethod,
  VerificationMethods
} from '@/api/auth-secure/types'

const DEFAULT_METHODS: VerificationMethods = {
  has2FA: false,
  hasPasskey: false,
  passkeySupported: false
}

const INITIAL_STATE: SecureVerificationState = {
  method: null,
  loading: false,
  code: '',
  title: undefined,
  description: undefined
}

// 当前后端协议未明确「verification required」错误标识，使用约定 message 关键字
// 后端联调后改为 error.response.data.code 等更稳定的标识（待办）
function isVerificationRequiredError(error: unknown): boolean {
  if (error instanceof Error) {
    return /verification required|need verification|二次验证/i.test(
      error.message
    )
  }
  if (typeof error === 'object' && error !== null) {
    const msg = (error as { response?: { data?: { message?: string } } })
      ?.response?.data?.message
    if (msg) {
      return /verification required|need verification|二次验证/i.test(msg)
    }
  }
  return false
}

type ApiCall = (() => Promise<unknown>) | null

interface InternalState extends SecureVerificationState {
  apiCall: ApiCall
}

export function useSecureVerification(
  options: UseSecureVerificationOptions = {}
) {
  const { onSuccess, onError, successMessage, autoReset = true } = options
  const { t } = useI18n()

  const methods = ref<VerificationMethods>({ ...DEFAULT_METHODS })
  const state = ref<InternalState>({
    ...INITIAL_STATE,
    apiCall: null
  })
  const open = ref<boolean>(false)

  async function fetchVerificationMethods(): Promise<VerificationMethods> {
    const result = await checkVerificationMethods()
    methods.value = result
    return result
  }

  onMounted(() => {
    fetchVerificationMethods().catch(() => {
      // 错误已由 checkVerificationMethods 内部 console.error 处理
    })
  })

  function reset(): void {
    state.value = { ...INITIAL_STATE, apiCall: null }
    open.value = false
  }

  // 启动验证：传入原始 apiCall，弹 dialog 让用户选验证方式
  async function startVerification(
    apiCall: () => Promise<unknown>,
    config: StartVerificationOptions = {}
  ): Promise<boolean> {
    const { preferredMethod, title, description } = config
    const availableMethods = await fetchVerificationMethods()

    if (!availableMethods.has2FA && !availableMethods.hasPasskey) {
      ElMessage.error(t('auth.secureVerification.noMethods'))
      onError?.(
        new Error(
          'No verification methods available. Enable 2FA or Passkey to continue.'
        )
      )
      return false
    }

    // 默认方法选择：优先 Passkey（更安全），其次 2FA
    let defaultMethod: VerificationMethod | null = preferredMethod ?? null
    if (!defaultMethod) {
      if (
        availableMethods.hasPasskey &&
        availableMethods.passkeySupported
      ) {
        defaultMethod = 'passkey'
      } else if (availableMethods.has2FA) {
        defaultMethod = '2fa'
      }
    }

    state.value = {
      ...state.value,
      apiCall,
      method: defaultMethod,
      title,
      description
    }
    open.value = true
    return true
  }

  // 执行验证：先 verify 再回放原始 apiCall
  async function executeVerification(
    method?: VerificationMethod,
    code?: string
  ): Promise<unknown> {
    if (!state.value.apiCall) {
      ElMessage.error(t('auth.secureVerification.notConfigured'))
      return null
    }

    const actualMethod = method ?? state.value.method
    if (!actualMethod) {
      ElMessage.error(t('auth.secureVerification.selectMethod'))
      return null
    }

    state.value = { ...state.value, loading: true }

    try {
      await verify(actualMethod, code ?? state.value.code)
      // apiCall 可能在 reset 后变 null，提取局部引用后判空
      const apiCall = state.value.apiCall
      if (!apiCall) {
        throw new Error('Verification apiCall was reset')
      }
      const result = await apiCall()

      if (successMessage) {
        ElMessage.success(successMessage)
      }

      onSuccess?.(result, actualMethod)

      if (autoReset) {
        reset()
      }

      return result
    } catch (error) {
      const message =
        error instanceof Error ? error.message : t('auth.secureVerification.failed')
      ElMessage.error(message)
      onError?.(error)
      throw error
    } finally {
      state.value = { ...state.value, loading: false }
    }
  }

  function setCode(code: string): void {
    state.value = { ...state.value, code }
  }

  function switchMethod(method: VerificationMethod): void {
    state.value = { ...state.value, method, code: '' }
  }

  function cancel(): void {
    reset()
  }

  // 高阶包装：先尝试直接调用 apiCall，若后端返回「需要二次验证」则自动启动 dialog
  async function withVerification(
    apiCall: () => Promise<unknown>,
    config: StartVerificationOptions = {}
  ): Promise<unknown> {
    try {
      return await apiCall()
    } catch (error) {
      if (isVerificationRequiredError(error)) {
        await startVerification(apiCall, config)
        return null
      }
      throw error
    }
  }

  function canUseMethod(method: VerificationMethod): boolean {
    if (method === '2fa') return methods.value.has2FA
    if (method === 'passkey') {
      return methods.value.hasPasskey && methods.value.passkeySupported
    }
    return false
  }

  const recommendedMethod = computed<VerificationMethod | null>(() => {
    if (methods.value.hasPasskey && methods.value.passkeySupported) {
      return 'passkey'
    }
    if (methods.value.has2FA) return '2fa'
    return null
  })

  return {
    open,
    methods,
    state,
    startVerification,
    executeVerification,
    cancel,
    reset,
    setCode,
    switchMethod,
    withVerification,
    fetchVerificationMethods,
    canUseMethod,
    recommendedMethod,
    hasAnyMethod: computed(
      () => methods.value.has2FA || methods.value.hasPasskey
    ),
    isLoading: computed(() => state.value.loading),
    currentMethod: computed(() => state.value.method),
    code: computed(() => state.value.code)
  }
}
