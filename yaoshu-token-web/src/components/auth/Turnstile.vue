<script setup lang="ts">
// 职责：动态注入 Turnstile script + 渲染 widget + 暴露 verify/expire/error 事件
// 被消费方：UserAuthForm/SignUpForm/ForgotPasswordForm（status.turnstileCheck=true 时显示）

import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

// 扩展 Window 类型：声明 Turnstile SDK 及动态回调函数
declare global {
  interface Window {
    turnstile?: {
      render: (container: HTMLElement, options: Record<string, unknown>) => string
      reset: (widgetId: string) => void
      remove: (widgetId: string) => void
    }
    [key: string]: unknown
  }
}

interface Props {
  siteKey: string
  action?: string
}

interface Emits {
  (e: 'verify', token: string): void
  (e: 'expire'): void
  (e: 'error'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const containerRef = ref<HTMLDivElement | null>(null)
// Turnstile widgetId（用于 reset/remove）
const widgetId = ref<string | null>(null)
const SCRIPT_SRC = 'https://challenges.cloudflare.com/turnstile/v0/api.js'
const scriptLoaded = ref<boolean>(false)

// 全局回调名（每个实例独立，避免 widget 间互相覆盖）
const callbackName = `__turnstile_cb_${Math.random().toString(36).slice(2)}`
const expireCallbackName = `${callbackName}_expire`
const errorCallbackName = `${callbackName}_error`

// 注入 Turnstile script（仅一次）
function loadScript(): Promise<void> {
  return new Promise((resolve, reject) => {
    if (window.turnstile) {
      scriptLoaded.value = true
      resolve()
      return
    }
    const existing = document.querySelector<HTMLScriptElement>(
      `script[src="${SCRIPT_SRC}"]`
    )
    if (existing) {
      existing.addEventListener('load', () => {
        scriptLoaded.value = true
        resolve()
      })
      existing.addEventListener('error', reject)
      return
    }
    const script = document.createElement('script')
    script.src = SCRIPT_SRC
    script.async = true
    script.defer = true
    script.onload = () => {
      scriptLoaded.value = true
      resolve()
    }
    script.onerror = reject
    document.head.appendChild(script)
  })
}

// 渲染 Turnstile widget
async function render(): Promise<void> {
  if (!containerRef.value || !window.turnstile) return

  // 暴露全局回调供 Turnstile 调用
  window[callbackName] = (token: string) => emit('verify', token)
  window[expireCallbackName] = () => emit('expire')
  window[errorCallbackName] = () => emit('error')

  widgetId.value = window.turnstile.render(containerRef.value, {
    sitekey: props.siteKey,
    action: props.action,
    callback: callbackName,
    'expired-callback': expireCallbackName,
    'error-callback': errorCallbackName
  })
}

function reset(): void {
  if (widgetId.value && window.turnstile) {
    window.turnstile.reset(widgetId.value)
  }
}

onMounted(async () => {
  try {
    await loadScript()
    await render()
  } catch {
    emit('error')
  }
})

// siteKey 变化时重渲染
watch(
  () => props.siteKey,
  async () => {
    if (widgetId.value && window.turnstile) {
      window.turnstile.remove(widgetId.value)
      widgetId.value = null
    }
    await render()
  }
)

onBeforeUnmount(() => {
  if (widgetId.value && window.turnstile) {
    window.turnstile.remove(widgetId.value)
  }
  // 清理全局回调
  delete window[callbackName]
  delete window[expireCallbackName]
  delete window[errorCallbackName]
})

defineExpose({ reset })
</script>

<template>
  <div
    ref="containerRef"
    class="turnstile-widget"
  />
</template>

<style scoped>
.turnstile-widget {
  display: flex;
  justify-content: center;
  min-height: 65px;
}
</style>
