<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

const { t } = useI18n()
const router = useRouter()
</script>

<template>
  <div class="not-found">
    <h1>404</h1>
    <p>{{ t('common.notFound') }}</p>
    <el-button
      type="primary"
      @click="router.push('/')"
    >
      {{ t('common.backHome') }}
    </el-button>
  </div>
</template>

<style scoped>
.not-found {
  display: flex;
  flex-direction: column;
  gap: var(--ys-spacing-4);
  align-items: center;
  justify-content: center;
  height: 100%;
}
</style>
