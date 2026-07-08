<script setup lang="ts">
/**
 * 隐私政策页（薄壳）。
 * 路由：/privacy-policy（公开，无 requireAuth）。
 */
import { useI18n } from 'vue-i18n'
import DocumentRenderer from '@/components/common/DocumentRenderer.vue'

const { t } = useI18n()
</script>

<template>
  <DocumentRenderer
    api-endpoint="/api/legal/privacy_policy"
    :title="t('legal.privacyPolicy.title')"
    cache-key="privacy_policy"
    :empty-message="t('legal.loadFailed', { title: t('legal.privacyPolicy.title') })"
  />
</template>
