<script setup lang="ts">
// Telegram Login Widget 集成：M1-B-T3
// 职责：动态注入 Telegram 官方 widget script + 暴露 TelegramUser 回调
// 流程：widget 渲染 → 用户授权 → window.onTelegramAuth(user) → emit success
// 被消费方：components/auth/OAuthProviders.vue

import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

// Telegram Login Widget 回调返回的用户对象
// 文档：https://core.telegram.org/widgets/login#receiving-authorization-data
export interface TelegramUser {
  id: number
  first_name?: string
  last_name?: string
  username?: string
  photo_url?: string
  auth_date: number
  hash: string
}

interface Props {
  // Telegram Bot 用户名（来自 status.telegramBotName）
  botName: string
  // Widget 尺寸：large 适配全宽登录按钮场景
  size?: 'large' | 'medium' | 'small'
  // 用户头像请求半径（0-64）
  userPic?: boolean
  // 请求权限（逗号分隔），默认空
  requestAccess?: string
}

interface Emits {
  (e: 'success', user: TelegramUser): void
  (e: 'error', message: string): void
}

const props = withDefaults(defineProps<Props>(), {
  size: 'large',
  userPic: false,
  requestAccess: ''
})
const emit = defineEmits<Emits>()

const widgetContainer = ref<HTMLDivElement | null>(null)
const scriptEl = ref<HTMLScriptElement | null>(null)

// Telegram Widget 通过全局回调通知授权结果
// 使用固定函数名 onTelegramAuth，避免多次实例化时回调名冲突
;(window as unknown as Record<string, unknown>).onTelegramAuth = (
  user: TelegramUser
) => {
  emit('success', user)
}

function loadWidget(): void {
  if (!widgetContainer.value || !props.botName) return
  // 清理旧 script（botName 变更时重建 widget）
  if (scriptEl.value) {
    scriptEl.value.remove()
    scriptEl.value = null
    widgetContainer.value.innerHTML = ''
  }

  const script = document.createElement('script')
  script.async = true
  script.src = 'https://telegram.org/js/telegram-widget.js?22'
  script.setAttribute('data-telegram-login', props.botName)
  script.setAttribute('data-size', props.size)
  script.setAttribute('data-onauth', 'onTelegramAuth(user)')
  script.setAttribute('data-request-access', props.requestAccess)
  if (props.userPic) {
    script.setAttribute('data-userpic', 'true')
  } else {
    script.setAttribute('data-userpic', 'false')
  }

  script.onerror = () => {
    emit('error', 'Telegram widget script load failed')
  }

  widgetContainer.value.appendChild(script)
  scriptEl.value = script
}

onMounted(loadWidget)

watch(
  () => props.botName,
  () => loadWidget()
)

onBeforeUnmount(() => {
  if (scriptEl.value) scriptEl.value.remove()
})
</script>

<template>
  <div
    ref="widgetContainer"
    class="telegram-widget"
  />
</template>

<style scoped lang="scss">
.telegram-widget {
  display: flex;
  justify-content: center;
  width: 100%;

  // 让 Telegram Widget 居中渲染（iframe 由 script 注入）
  :deep(iframe) {
    width: 100% !important;
    max-width: 100%;
  }
}
</style>
