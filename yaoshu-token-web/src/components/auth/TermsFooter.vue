<script setup lang="ts">
// 职责：sign-in/sign-up 页底部的「点击即同意 用户协议 + 隐私政策」文案
// status 两者均未启用时不渲染

import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useStatus } from '@/composables/useStatus'

interface Props {
  variant?: 'sign-in' | 'sign-up'
}

// Props 通过 template 自动注入（variant 在 template 内消费）
withDefaults(defineProps<Props>(), {
  variant: 'sign-in'
})

const { t } = useI18n()
const { status } = useStatus()

const hasUserAgreement = computed(
  () => Boolean(status.value?.userAgreementEnabled) === true
)
const hasPrivacyPolicy = computed(
  () => Boolean(status.value?.privacyPolicyEnabled) === true
)

const shouldRender = computed(
  () => hasUserAgreement.value || hasPrivacyPolicy.value
)
</script>

<template>
  <p
    v-if="shouldRender"
    class="terms-footer"
  >
    <span class="terms-footer__prefix">
      {{
        variant === 'sign-in'
          ? t('auth.termsFooter.signInPrefix')
          : t('auth.termsFooter.signUpPrefix')
      }}
    </span>
    <router-link
      v-if="hasUserAgreement"
      :to="{ name: 'UserAgreement' }"
      class="terms-footer__link"
    >
      {{ t('auth.termsFooter.userAgreement') }}
    </router-link>
    <template v-if="hasUserAgreement && hasPrivacyPolicy">
      {{ t('auth.termsFooter.and') }}
    </template>
    <router-link
      v-if="hasPrivacyPolicy"
      :to="{ name: 'PrivacyPolicy' }"
      class="terms-footer__link"
    >
      {{ t('auth.termsFooter.privacyPolicy') }}
    </router-link>
    {{ t('auth.termsFooter.suffix') }}
  </p>
</template>

<style scoped>
.terms-footer {
  margin: 0;
  font-size: var(--ys-font-size-xs);
  color: var(--el-text-color-secondary);
  text-align: center;

  &__prefix {
    margin-right: 4px;
  }

  &__link {
    color: var(--el-color-primary);
    text-decoration: underline;
    text-underline-offset: 4px;

    &:hover {
      opacity: 0.85;
    }
  }
}
</style>
