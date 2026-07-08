<script setup lang="ts">
// 职责：渲染忘记密码表单 + 注册引导链接

import { useI18n } from 'vue-i18n'
import ForgotPasswordForm from '@/components/auth/ForgotPasswordForm.vue'

const { t } = useI18n()
</script>

<template>
  <div class="forgot-password-view">
    <div class="forgot-password-view__header">
      <h2 class="forgot-password-view__title">
        {{ t('auth.forgotPassword.title') }}
      </h2>
      <p class="forgot-password-view__description">
        {{ t('auth.forgotPassword.description') }}
      </p>
      <p class="forgot-password-view__hint">
        {{ t('auth.signIn.noAccount') }}
        <RouterLink
          to="/sign-up"
          class="forgot-password-view__link"
        >
          {{ t('auth.signUp.title') }}
        </RouterLink>
      </p>
    </div>

    <ForgotPasswordForm />
  </div>
</template>

<style scoped lang="scss">
.forgot-password-view {
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
