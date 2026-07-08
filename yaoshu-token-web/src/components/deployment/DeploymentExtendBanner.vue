<script setup lang="ts">
/**
 * 延长重要提醒 Banner。
 */
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <el-alert
    type="warning"
    :title="t('deployment.extend.bannerTitle')"
    :description="t('deployment.extend.bannerDesc')"
    :closable="false"
    show-icon
  />
</template>
