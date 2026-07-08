<script setup lang="ts">
/**
 * 用户协议页（薄壳）。
 * 路由：/user-agreement（公开，无 requireAuth）。
 */
import { useI18n } from 'vue-i18n'
import DocumentRenderer from '@/components/common/DocumentRenderer.vue'

const { t } = useI18n()
</script>

<template>
  <DocumentRenderer
    api-endpoint="/api/legal/user_agreement"
    :title="t('legal.userAgreement.title')"
    cache-key="user_agreement"
    :empty-message="t('legal.loadFailed', { title: t('legal.userAgreement.title') })"
  />
</template>
