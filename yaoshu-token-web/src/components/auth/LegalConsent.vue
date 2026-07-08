<script setup lang="ts">
// 职责：根据 status.userAgreementEnabled/privacyPolicyEnabled 决定是否渲染 + 暴露 v-model:checked

import { computed } from 'vue'
import { ElCheckbox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useStatus } from '@/composables/useStatus'

interface Props {
  checked: boolean
  disabled?: boolean
}

interface Emits {
  (e: 'update:checked', value: boolean): void
}

// Props 通过 template 自动注入，无需显式 props 变量
defineProps<Props>()
const emit = defineEmits<Emits>()

const { t } = useI18n()
const { status } = useStatus()

const hasUserAgreement = computed(
  () => Boolean(status.value?.userAgreementEnabled) === true
)
const hasPrivacyPolicy = computed(
  () => Boolean(status.value?.privacyPolicyEnabled) === true
)
const shouldRender = computed(() => hasUserAgreement.value || hasPrivacyPolicy.value)

function handleChange(value: boolean | string | number | boolean): void {
  emit('update:checked', value === true)
}
</script>

<template>
  <div
    v-if="shouldRender"
    class="legal-consent"
    :class="{ 'legal-consent--disabled': disabled }"
  >
    <ElCheckbox
      :model-value="checked"
      :disabled="disabled"
      @change="handleChange"
    >
      <span class="legal-consent__text">
        {{ t('auth.legalConsent.prefix') }}
        <a
          v-if="hasUserAgreement"
          href="/user-agreement"
          target="_blank"
          rel="noopener noreferrer"
          class="legal-consent__link"
        >
          {{ t('auth.legalConsent.userAgreement') }}
        </a>
        <template v-if="hasUserAgreement && hasPrivacyPolicy">
          {{ t('auth.legalConsent.and') }}
        </template>
        <a
          v-if="hasPrivacyPolicy"
          href="/privacy-policy"
          target="_blank"
          rel="noopener noreferrer"
          class="legal-consent__link"
        >
          {{ t('auth.legalConsent.privacyPolicy') }}
        </a>
        {{ t('auth.legalConsent.suffix') }}
      </span>
    </ElCheckbox>
  </div>
</template>

<style scoped lang="scss">
.legal-consent {
  padding: var(--ys-spacing-3);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);

  &--disabled {
    opacity: 0.6;
  }

  &__text {
    font-size: var(--ys-font-size-xs);
    font-weight: 400;
    line-height: 1.5;
    color: var(--el-text-color-secondary);
  }

  &__link {
    color: var(--el-color-primary);
    text-decoration: underline;
    text-underline-offset: 2px;

    &:hover {
      opacity: 0.85;
    }
  }
}
</style>
