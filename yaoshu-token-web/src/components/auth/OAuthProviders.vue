<script setup lang="ts">
// 职责：根据 status 动态渲染可用的 OAuth provider 按钮（GitHub/Discord/OIDC/LinuxDO/Telegram/WeChat/自定义）
// 内部消费 useOAuthLogin composable 处理重定向流；微信入口由父组件控制 dialog 开关

import { computed } from 'vue'
import { ElButton, ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useStatus } from '@/composables/useStatus'
import { useOAuthLogin } from '@/composables/auth/useOAuthLogin'
import TelegramWidget from './TelegramWidget.vue'
import type { TelegramUser } from './TelegramWidget.vue'

interface Props {
  disabled?: boolean
  className?: string
  onWeChatLogin?: () => void
  isWeChatLoading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
  className: '',
  onWeChatLogin: undefined,
  isWeChatLoading: false
})

interface ProviderButton {
  key: string
  label: string
  onClick: () => void
  iconClass?: string
  disabled?: boolean
}

const { t } = useI18n()
const { status } = useStatus()
const {
  isLoading,
  githubButtonText,
  githubButtonDisabled,
  handleGitHubLogin,
  handleDiscordLogin,
  handleOIDCLogin,
  handleLinuxDOLogin,
  handleTelegramCallback,
  handleCustomOAuthLogin
} = useOAuthLogin(status.value)

// 微信二维码 URL：M1-B-T2 后端字段已确认为 wechatQrCodeUrl（camelCase），删除 6 字段 fallback 链
const wechatQrCodeUrl = computed(() => {
  const s = status.value ?? {}
  return (s.wechatQrCodeUrl as string) || ''
})

// 动态构建按钮列表（Telegram 单独走 Widget 渲染，不进入按钮列表）
const providerButtons = computed<ProviderButton[]>(() => {
  const s = status.value
  if (!s) return []
  const buttons: ProviderButton[] = []

  if (s.wechatLogin && props.onWeChatLogin) {
    buttons.push({
      key: 'wechat',
      label: t('auth.oauth.wechat'),
      onClick: props.onWeChatLogin,
      iconClass: 'i-ep-chat-dot-round',
      disabled: props.isWeChatLoading
    })
  }

  if (s.githubOauth) {
    buttons.push({
      key: 'github',
      label: githubButtonText.value || t('auth.oauth.github'),
      onClick: handleGitHubLogin,
      iconClass: 'i-ep-connection',
      disabled: githubButtonDisabled.value
    })
  }

  if (s.discordOauth) {
    buttons.push({
      key: 'discord',
      label: t('auth.oauth.discord'),
      onClick: handleDiscordLogin,
      iconClass: 'i-ep-chat-line-round'
    })
  }

  if (s.oidcEnabled) {
    buttons.push({
      key: 'oidc',
      label: t('auth.oauth.oidc'),
      onClick: handleOIDCLogin,
      iconClass: 'i-ep-key'
    })
  }

  if (s.linuxdoOauth) {
    buttons.push({
      key: 'linuxdo',
      label: t('auth.oauth.linuxdo'),
      onClick: handleLinuxDOLogin,
      iconClass: 'i-ep-platform'
    })
  }

  // 自定义 OAuth providers
  const customProviders = s.customOauthProviders
  if (Array.isArray(customProviders) && customProviders.length > 0) {
    for (const provider of customProviders) {
      buttons.push({
        key: `custom-${provider.slug}`,
        label: t('auth.oauth.custom', { name: provider.name }),
        onClick: () => handleCustomOAuthLogin(provider),
        iconClass: 'i-ep-connection'
      })
    }
  }

  return buttons
})

// Telegram Widget 配置：status.telegramOauth=true 且 telegramBotName 存在时渲染
const telegramBotName = computed(() => {
  const s = status.value
  if (!s?.telegramOauth) return ''
  return (s.telegramBotName as string) || ''
})

// Telegram Widget 授权回调 → 透传给 /api/oauth/telegram/login
function handleTelegramWidgetSuccess(user: TelegramUser): void {
  handleTelegramCallback(user)
}

function handleTelegramWidgetError(message: string): void {
  ElMessage.error(message || t('auth.oauth.startFailed', { provider: 'Telegram' }))
}

// 暴露给父组件的微信二维码 URL（避免重复 fallback 逻辑）
defineExpose({ wechatQrCodeUrl })
</script>

<template>
  <div
    v-if="providerButtons.length > 0 || telegramBotName"
    class="oauth-providers"
    :class="className"
  >
    <div class="oauth-providers__divider">
      <span class="oauth-providers__divider-text">
        {{ t('auth.oauth.orContinueWith') }}
      </span>
    </div>

    <div class="oauth-providers__buttons">
      <ElButton
        v-for="btn in providerButtons"
        :key="btn.key"
        type="default"
        :disabled="disabled || isLoading || btn.disabled"
        class="oauth-providers__btn"
        @click="btn.onClick"
      >
        <i
          v-if="btn.iconClass"
          :class="btn.iconClass"
          class="oauth-providers__icon"
        />
        <span>{{ btn.label }}</span>
      </ElButton>

      <!-- Telegram 走官方 Login Widget（M1-B-T3） -->
      <div
        v-if="telegramBotName"
        class="oauth-providers__btn oauth-providers__telegram-widget"
      >
        <TelegramWidget
          :bot-name="telegramBotName"
          @success="handleTelegramWidgetSuccess"
          @error="handleTelegramWidgetError"
        />
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.oauth-providers {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-3);

  &__divider {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;

    &::before,
    &::after {
      flex: 1;
      height: 1px;
      content: '';
      background: var(--el-border-color);
    }
  }

  &__divider-text {
    padding: 0 var(--ys-spacing-2);
    font-size: 11px;
    color: var(--el-text-color-secondary);
    text-transform: uppercase;
  }

  &__buttons {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-2);
  }

  &__btn {
    gap: var(--ys-spacing-2);
    justify-content: center;
    width: 100%;
    height: 44px;
  }

  &__icon {
    width: 16px;
    height: 16px;
    font-size: var(--ys-font-size-lg);
  }
}
</style>
