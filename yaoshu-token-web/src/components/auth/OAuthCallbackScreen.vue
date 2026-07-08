<script setup lang="ts">
// 职责：OAuth provider 回调时显示的 loading 屏（图标 + provider 名 + 旋转 loader）
// mode 区分 login/bind，文案不同

import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

interface Props {
  provider: string
  mode: 'login' | 'bind'
}

const props = defineProps<Props>()

const { t } = useI18n()

// provider 名规范化展示
const providerLabel = computed(() => {
  const normalized = props.provider?.toLowerCase() ?? ''
  const dict: Record<string, string> = {
    github: 'GitHub',
    discord: 'Discord',
    oidc: 'OIDC',
    linuxdo: 'LinuxDO',
    telegram: 'Telegram',
    wechat: 'WeChat'
  }
  return dict[normalized] || t('auth.oauthCallback.account')
})

const isBindMode = computed(() => props.mode === 'bind')

const headline = computed(() =>
  isBindMode.value
    ? t('auth.oauthCallback.bindHeadline', { provider: providerLabel.value })
    : t('auth.oauthCallback.loginHeadline', { provider: providerLabel.value })
)

const description = computed(() =>
  isBindMode.value
    ? t('auth.oauthCallback.bindDescription')
    : t('auth.oauthCallback.loginDescription')
)

const secondaryNote = computed(() =>
  isBindMode.value
    ? t('auth.oauthCallback.bindSecondary')
    : t('auth.oauthCallback.loginSecondary')
)
</script>

<template>
  <div class="oauth-callback-screen">
    <div class="oauth-callback-screen__header">
      <div class="oauth-callback-screen__icon-wrap">
        <i class="i-ep-connection oauth-callback-screen__icon" />
      </div>
      <div class="oauth-callback-screen__title-group">
        <h2 class="oauth-callback-screen__title">
          {{ headline }}
        </h2>
        <p class="oauth-callback-screen__description">
          {{ description }}
        </p>
      </div>
    </div>

    <div class="oauth-callback-screen__body">
      <div class="oauth-callback-screen__loading">
        <i class="i-ep-loading oauth-callback-screen__spinner" />
        <span class="oauth-callback-screen__loading-text">
          {{ t('auth.oauthCallback.processing') }}
        </span>
      </div>
      <p class="oauth-callback-screen__secondary">
        {{ secondaryNote }}
      </p>
      <p class="oauth-callback-screen__hint">
        {{ t('auth.oauthCallback.hint') }}
      </p>
    </div>
  </div>
</template>

<style scoped lang="scss">
.oauth-callback-screen {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-8);
  width: 100%;

  &__header {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-4);
    align-items: center;
    text-align: center;
  }

  &__icon-wrap {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 64px;
    height: 64px;
    background: var(--el-fill-color-light);
    border-radius: var(--ys-radius-xl);
  }

  &__icon {
    width: 32px;
    height: 32px;
    font-size: 32px;
    color: var(--el-color-primary);
  }

  &__title-group {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-2);
  }

  &__title {
    margin: 0;
    font-size: var(--ys-font-size-2xl);
    font-weight: 600;
    text-align: center;
    letter-spacing: -0.025em;
  }

  &__description {
    margin: 0;
    font-size: var(--ys-font-size-base);
    color: var(--el-text-color-secondary);
  }

  &__body {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-4);
    text-align: center;
  }

  &__loading {
    display: flex;
    gap: var(--ys-spacing-2);
    align-items: center;
    justify-content: center;
    font-size: var(--ys-font-size-base);
    font-weight: 500;
  }

  &__spinner {
    width: 16px;
    height: 16px;
    font-size: var(--ys-font-size-lg);
    color: var(--el-color-primary);
    animation: spin 1.4s linear infinite;
  }

  &__secondary {
    margin: 0;
    font-size: var(--ys-font-size-base);
    color: var(--el-text-color-secondary);
  }

  &__hint {
    margin: 0;
    font-size: var(--ys-font-size-xs);
    color: var(--el-text-color-secondary);
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
