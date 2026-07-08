<script setup lang="ts">
// 职责：渲染 OtpForm + 显示「会话过期？重新登录」引导

import { useI18n } from 'vue-i18n'
import OtpForm from '@/components/auth/OtpForm.vue'

const { t } = useI18n()
</script>

<template>
  <div class="otp-view">
    <div class="otp-view__header">
      <h2 class="otp-view__title">
        {{ t('auth.otp.title') }}
      </h2>
      <p class="otp-view__description">
        {{ t('auth.otp.description') }}
      </p>
      <p class="otp-view__hint">
        {{ t('auth.otp.sessionExpired') }}
        <RouterLink
          to="/sign-in"
          class="otp-view__link"
        >
          {{ t('auth.otp.reLogin') }}
        </RouterLink>
      </p>
    </div>

    <OtpForm />
  </div>
</template>

<style scoped lang="scss">
.otp-view {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-8);
  width: 100%;

  &__header {
    display: flex;
    flex-direction: column;
    gap: var(--ys-spacing-3);
  }

  &__title {
    margin: 0;
    font-size: var(--ys-font-size-2xl);
    font-weight: 600;
    text-align: center;
    letter-spacing: -0.025em;

    @media (width >= 640px) {
      text-align: left;
    }
  }

  &__description,
  &__hint {
    margin: 0;
    font-size: var(--ys-font-size-base);
    color: var(--el-text-color-secondary);
    text-align: center;

    @media (width >= 640px) {
      text-align: left;
    }
  }

  &__link {
    font-weight: 500;
    color: var(--el-color-primary);
    text-decoration: underline;
    text-underline-offset: 4px;
  }
}
</style>
