<script setup lang="ts">
// 职责：渲染登录表单 + 显示注册引导链接 + 条款页脚

import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { useStatus } from '@/composables/useStatus'
import UserAuthForm from '@/components/auth/UserAuthForm.vue'
import TermsFooter from '@/components/auth/TermsFooter.vue'

const { t } = useI18n()
const route = useRoute()
const { status } = useStatus()

// redirect 参数：登录成功后回跳目标
const redirectQuery = route.query.redirect
const redirectTo = typeof redirectQuery === 'string' ? redirectQuery : undefined

// 注册功能是否启用（selfUseModeEnabled=true 时隐藏注册引导）
const showSignUpLink = computed(() => {
  if (status.value?.selfUseModeEnabled) return false
  return status.value?.registerEnabled !== false
})
</script>

<template>
  <div class="sign-in-view">
    <div class="sign-in-view__header">
      <h2 class="sign-in-view__title">
        {{ t('auth.signIn.title') }}
      </h2>
      <p
        v-if="showSignUpLink"
        class="sign-in-view__hint"
      >
        {{ t('auth.signIn.noAccount') }}
        <RouterLink
          to="/sign-up"
          class="sign-in-view__link"
        >
          {{ t('auth.signUp.title') }}
        </RouterLink>
      </p>
    </div>

    <UserAuthForm :redirect-to="redirectTo" />

    <TermsFooter
      variant="sign-in"
      class="sign-in-view__terms"
    />
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens' as *;

.sign-in-view {
  display: flex;
  flex-direction: column;
  gap: $spacing-8;
  width: 100%;

  &__header {
    display: flex;
    flex-direction: column;
    gap: $spacing-2;
  }

  &__title {
    margin: 0;
    font-size: $font-size-2xl;
    font-weight: $font-weight-semibold;
    text-align: center;
    letter-spacing: -0.025em;

    @media (width >= 640px) {
      text-align: left;
    }
  }

  &__hint {
    margin: 0;
    font-size: $font-size-base;
    color: var(--el-text-color-secondary);
    text-align: center;

    @media (width >= 640px) {
      text-align: left;
    }
  }

  &__link {
    font-weight: $font-weight-medium;
    color: var(--el-color-primary);
    text-decoration: underline;
    text-underline-offset: $spacing-1;
  }

  &__terms {
    margin-top: $spacing-2;
  }
}
</style>
