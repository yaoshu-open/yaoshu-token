<script setup lang="ts">
/**
 * T-MJ-01 Midjourney 回调提醒 banner。
 *
 * 管理员可见，提示「未开启 MJ 回调可能导致部分项目无绘图结果」。
 * localStorage('mj_notify_enabled') 持久化关闭状态。
 */
import { ElAlert, ElButton } from 'element-plus'
import { useI18n } from 'vue-i18n'

defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'dismiss'): void
}>()

const { t } = useI18n()
</script>

<template>
  <ElAlert
    v-if="visible"
    type="warning"
    :closable="false"
    show-icon
    class="mj-banner"
  >
    <template #title>
      <span>{{ t('midjourney.banner.callbackDisabled') }}</span>
    </template>
    <template #default>
      <ElButton
        type="warning"
        size="small"
        text
        @click="emit('dismiss')"
      >
        {{ t('midjourney.banner.dismiss') }}
      </ElButton>
    </template>
  </ElAlert>
</template>

<style scoped>
.mj-banner {
  margin-bottom: 12px;
}
</style>
