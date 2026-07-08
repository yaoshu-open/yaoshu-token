<script setup lang="ts">
// 职责：渲染注册表单 + 显示登录引导链接 + 条款页脚

import { useI18n } from 'vue-i18n'
import SignUpForm from '@/components/auth/SignUpForm.vue'
import TermsFooter from '@/components/auth/TermsFooter.vue'

const { t } = useI18n()
</script>

<template>
  <div class="sign-up-view">
    <div class="sign-up-view__header">
      <h2 class="sign-up-view__title">
        {{ t('auth.signUp.title') }}
      </h2>
      <p class="sign-up-view__hint">
        {{ t('auth.signUp.hasAccount') }}
        <RouterLink
          to="/sign-in"
          class="sign-up-view__link"
        >
          {{ t('auth.signIn.title') }}
        </RouterLink>
      </p>
    </div>

    <SignUpForm />

    <TermsFooter
      variant="sign-up"
      class="sign-up-view__terms"
    />
  </div>
</template>

<style scoped lang="scss">
.sign-up-view {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-8);
  width: 100%;

  &__header {
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

    @media (width >= 640px) {
      text-align: left;
    }
  }

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

  &__terms {
    margin-top: var(--ys-spacing-2);
  }
}
</style>
